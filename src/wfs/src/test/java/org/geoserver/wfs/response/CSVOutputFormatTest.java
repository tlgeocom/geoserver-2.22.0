/* (c) 2014-2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.response;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import au.com.bytecode.opencsv.CSVReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import net.opengis.wfs.GetFeatureType;
import net.opengis.wfs.WfsFactory;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.data.test.MockData;
import org.geoserver.platform.Operation;
import org.geoserver.wfs.WFSTestSupport;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.complex.feature.type.ComplexFeatureTypeImpl;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wfs.internal.WFSContentComplexFeatureCollection;
import org.geotools.feature.FakeTypes;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.DateUtil;
import org.geotools.feature.type.FeatureTypeImpl;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.identity.FeatureId;
import org.springframework.mock.web.MockHttpServletResponse;

public class CSVOutputFormatTest extends WFSTestSupport {

    @Test
    public void testFullRequest() throws Exception {
        MockHttpServletResponse resp =
                getAsServletResponse(
                        "wfs?version=1.1.0&request=GetFeature&typeName=sf:PrimitiveGeoFeature&outputFormat=csv",
                        "");

        FeatureSource fs = getFeatureSource(MockData.PRIMITIVEGEOFEATURE);

        //        System.out.println(resp.getOutputStreamContent());

        // check the mime type
        assertEquals("text/csv", resp.getContentType());

        // check the charset encoding
        assertEquals("UTF-8", resp.getCharacterEncoding());

        // check the content disposition
        assertEquals(
                "attachment; filename=PrimitiveGeoFeature.csv",
                resp.getHeader("Content-Disposition"));

        // read the response back with a parser that can handle escaping, newlines and what not
        List<String[]> lines = readLines(resp.getContentAsString(), ',');

        // we should have one header line and then all the features in that feature type
        assertEquals(fs.getCount(Query.ALL) + 1, lines.size());

        for (String[] line : lines) {
            // check each line has the expected number of elements (num of att + 1 for the id)
            assertEquals(fs.getSchema().getDescriptors().size() + 1, line.length);
        }
    }

    @Test
    public void testHTMLStuff() throws Exception {
        MockHttpServletResponse resp =
                getAsServletResponse(
                        "wfs?version=1.1.0&request=GetFeature&"
                                + "typeName=sf:PrimitiveGeoFeature&"
                                + "outputFormat=csv&format_options=filename:test",
                        "");

        assertEquals("text/csv", resp.getContentType());
        assertEquals("UTF-8", resp.getCharacterEncoding());
        assertEquals("attachment; filename=test.csv", resp.getHeader("Content-Disposition"));
    }

    @Test
    public void testEscapes() throws Exception {
        // build some fake data in memory, the property data store cannot handle newlines in its
        // data
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add("geom", Point.class);
        builder.add("label", String.class);
        builder.add("dtg", Date.class);
        builder.add("n", Integer.class);
        builder.add("d", Double.class);
        builder.setName("funnyLabels");
        SimpleFeatureType type = builder.buildFeatureType();

        Date d = (new SimpleDateFormat("yyyy-MM-dd")).parse("2016-01-01");
        GeometryFactory gf = new GeometryFactory();
        SimpleFeature f1 =
                SimpleFeatureBuilder.build(
                        type,
                        new Object[] {
                            gf.createPoint(new Coordinate(5, 8)),
                            "A label with \"quotes\"",
                            d,
                            10,
                            100.0
                        },
                        null);
        SimpleFeature f2 =
                SimpleFeatureBuilder.build(
                        type,
                        new Object[] {
                            gf.createPoint(new Coordinate(5, 4)),
                            "A long label\nwith newlines",
                            d,
                            10,
                            200.0
                        },
                        null);
        SimpleFeature f3 =
                SimpleFeatureBuilder.build(
                        type,
                        new Object[] {
                            gf.createPoint(new Coordinate(5, 4)),
                            "A long label\r\nwith windows\r\nnewlines",
                            d,
                            10,
                            300.0
                        },
                        null);

        MemoryDataStore data = new MemoryDataStore();
        data.addFeature(f1);
        data.addFeature(f2);
        data.addFeature(f3);
        SimpleFeatureSource fs = data.getFeatureSource("funnyLabels");

        // build the request objects and feed the output format
        GetFeatureType gft = WfsFactory.eINSTANCE.createGetFeatureType();
        Operation op =
                new Operation("GetFeature", getServiceDescriptor10(), null, new Object[] {gft});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FeatureCollectionResponse fct =
                FeatureCollectionResponse.adapt(WfsFactory.eINSTANCE.createFeatureCollectionType());
        fct.getFeature().add(fs.getFeatures());

        // write out the results
        CSVOutputFormat format = new CSVOutputFormat(getGeoServer());
        format.write(fct, bos, op);

        // read the response back with a parser that can handle escaping, newlines and what not
        List<String[]> lines = readLines(bos.toString(), ',');

        // we should have one header line and then all the features in that feature type
        assertEquals(fs.getCount(Query.ALL) + 1, lines.size());

        for (String[] line : lines) {
            // check each line has the expected number of elements
            assertEquals(fs.getSchema().getAttributeCount() + 1, line.length);
        }

        // check we have the expected values in the string attributes
        assertEquals(f1.getAttribute("label"), lines.get(1)[2]);
        assertEquals(f2.getAttribute("label"), lines.get(2)[2]);
        // the test CSVReader helpfully turns \r\n into \n for us.
        assertEquals(((String) f3.getAttribute("label")).replace("\r\n", "\n"), lines.get(3)[2]);
        // dates
        assertEquals(DateUtil.serializeDateTime((Date) f1.getAttribute("dtg")), lines.get(1)[3]);
        // Numbers
        assertEquals(f1.getAttribute("n"), Integer.parseInt(lines.get(1)[4]));
        assertEquals(f2.getAttribute("d"), Double.parseDouble(lines.get(2)[5]));
    }

    /** Convenience to read the csv content and */
    private List<String[]> readLines(String csvContent, Character separator) throws IOException {
        CSVReader reader = new CSVReader(new StringReader(csvContent), separator);

        List<String[]> result = new ArrayList<>();
        String[] nextLine;
        while ((nextLine = reader.readNext()) != null) {
            result.add(nextLine);
        }
        return result;
    }

    @Test
    public void testFullRequestWithDynamicCsvSeparator() throws Exception {

        Character separator = '-';

        // Get dash separated csv response
        MockHttpServletResponse resp =
                getAsServletResponse(
                        "wfs?version=1.1.0&request=GetFeature&typeName=sf:PrimitiveGeoFeature&outputFormat=csv&format_options=csvSeparator:"
                                + separator,
                        "");

        FeatureSource fs = getFeatureSource(MockData.PRIMITIVEGEOFEATURE);

        // check the mime type
        assertEquals("text/csv", resp.getContentType());

        // check the charset encoding
        assertEquals("UTF-8", resp.getCharacterEncoding());

        // check the content disposition
        assertEquals(
                "attachment; filename=PrimitiveGeoFeature.csv",
                resp.getHeader("Content-Disposition"));

        // read the response back with a parser that can handle escaping, newlines and what not
        List<String[]> lines = readLines(resp.getContentAsString(), separator);

        // we should have one header line and then all the features in that feature type
        assertEquals(fs.getCount(Query.ALL) + 1, lines.size());

        for (String[] line : lines) {
            // check each line has the expected number of elements (num of att + 1 for the id)
            assertEquals(fs.getSchema().getDescriptors().size() + 1, line.length);
        }
    }

    @Test
    public void testDoubleQuotesAsCsvSeparator() throws Exception {

        // build the request objects and feed the output format
        GetFeatureType gft = WfsFactory.eINSTANCE.createGetFeatureType();
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("CSVSEPARATOR", "\"");
        gft.setFormatOptions(hashMap);
        Operation op =
                new Operation("GetFeature", getServiceDescriptor10(), null, new Object[] {gft});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FeatureCollectionResponse fct =
                FeatureCollectionResponse.adapt(WfsFactory.eINSTANCE.createFeatureCollectionType());

        SimpleFeatureSource fs = getFeatureSource(MockData.PRIMITIVEGEOFEATURE);

        fct.getFeature().add(fs.getFeatures());

        // write out the results
        CSVOutputFormat format = new CSVOutputFormat(getGeoServer());

        InvalidParameterException invalidParameterException =
                assertThrows(InvalidParameterException.class, () -> format.write(fct, bos, op));

        assertEquals(
                "A double quote is not allowed as a CSV separator",
                invalidParameterException.getMessage());
    }

    @Test
    public void testSemicolonAsCsvSeparator() throws Exception {

        String separator = "semicolon";

        // Get semicolon separated csv response
        MockHttpServletResponse resp =
                getAsServletResponse(
                        "wfs?version=1.1.0&request=GetFeature&typeName=sf:PrimitiveGeoFeature&outputFormat=csv&format_options=csvSeparator:"
                                + separator,
                        "");

        FeatureSource fs = getFeatureSource(MockData.PRIMITIVEGEOFEATURE);

        // check the mime type
        assertEquals("text/csv", resp.getContentType());

        // check the charset encoding
        assertEquals("UTF-8", resp.getCharacterEncoding());

        // check the content disposition
        assertEquals(
                "attachment; filename=PrimitiveGeoFeature.csv",
                resp.getHeader("Content-Disposition"));

        List<String[]> lines = readLines(resp.getContentAsString(), ';');

        // we should have one header line and then all the features in that feature type
        assertEquals(fs.getCount(Query.ALL) + 1, lines.size());

        for (String[] line : lines) {
            // check each line has the expected number of elements (num of att + 1 for the id)
            assertEquals(fs.getSchema().getDescriptors().size() + 1, line.length);
        }
    }

    @Test
    public void testResolvePrefixedAttributeNames() {
        NamespaceInfo nsInfo = new NamespaceInfoImpl();
        nsInfo.setPrefix("test-ns");
        nsInfo.setURI("http://test-ns/core");
        getGeoServer().getCatalog().add(nsInfo);
        CSVOutputFormat csvFormat = new CSVOutputFormat(getGeoServer());
        assertEquals(
                "test-ns:attributeName",
                csvFormat.resolveNamespacePrefixName("http://test-ns/core:attributeName"));
    }

    @Test
    public void testDontResolvePrefixedAttributeNames() {
        NamespaceInfo nsInfo = new NamespaceInfoImpl();
        nsInfo.setPrefix("test-ns2");
        nsInfo.setURI("http://test-ns2/core");
        getGeoServer().getCatalog().add(nsInfo);
        CSVOutputFormat csvFormat = new CSVOutputFormat(getGeoServer());
        assertEquals(
                "test-ns2:attributeName",
                csvFormat.resolveNamespacePrefixName("test-ns2:attributeName"));
    }

    @Test
    public void testUnvalidResolvePrefixedAttributeNames() {
        CSVOutputFormat csvFormat = new CSVOutputFormat(getGeoServer());
        assertEquals(
                "test:attributeName:", csvFormat.resolveNamespacePrefixName("test:attributeName:"));
    }

    @Test
    public void testComplexFeatureMultiValuedAttributes() throws IOException {

        FeatureCollectionResponse fct =
                FeatureCollectionResponse.adapt(WfsFactory.eINSTANCE.createFeatureCollectionType());

        ComplexFeatureTypeImpl schema = createNiceMock(ComplexFeatureTypeImpl.class);
        expect(schema.getName()).andReturn(new NameImpl("testComplexFt"));
        List<PropertyDescriptor> descriptors = new ArrayList<>();

        ComplexType MINENAMETYPE_TYPE =
                new FeatureTypeImpl(
                        FakeTypes.Mine.NAME_MineNameType,
                        FakeTypes.Mine.MINENAMETYPE_SCHEMA,
                        null,
                        false,
                        Collections.emptyList(),
                        FakeTypes.ANYTYPE_TYPE,
                        null);

        // Name of multi-valued attribute
        Name name = new NameImpl("items");
        PropertyDescriptor propertyDescriptor =
                new AttributeDescriptorImpl(MINENAMETYPE_TYPE, name, 1, 1, false, null);
        descriptors.add(propertyDescriptor);

        expect(schema.getDescriptors()).andReturn(descriptors).anyTimes();
        replay(schema);

        Feature feature = createNiceMock(Feature.class);

        FeatureId featureId = createNiceMock(FeatureId.class);
        expect(featureId.getID()).andReturn("testId").anyTimes();
        replay(featureId);

        expect(feature.getIdentifier()).andReturn(featureId);

        Collection<Property> values = new ArrayList<>();
        Property property1 = createNiceMock(Property.class);
        expect(property1.getValue()).andReturn("prop1").anyTimes();
        values.add(property1);
        Property property2 = createNiceMock(Property.class);
        expect(property2.getValue()).andReturn("prop2").anyTimes();
        values.add(property2);
        replay(property1, property2);

        expect(feature.getProperties(name)).andReturn(values).anyTimes();
        replay(feature);

        FeatureCollection featureCollection =
                createNiceMock(WFSContentComplexFeatureCollection.class);
        try (FeatureIterator<Feature> i = createNiceMock(FeatureIterator.class)) {

            expect(i.hasNext()).andReturn(true).times(1);
            expect(i.next()).andReturn(feature).anyTimes();
            replay(i);

            expect(featureCollection.getSchema()).andReturn(schema).anyTimes();
            expect(featureCollection.features()).andReturn(i).anyTimes();
            replay(featureCollection);
        }

        fct.getFeature().add(featureCollection);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        GetFeatureType gft = WfsFactory.eINSTANCE.createGetFeatureType();
        Operation op =
                new Operation("GetFeature", getServiceDescriptor10(), null, new Object[] {gft});

        // write out the results
        CSVOutputFormat format = new CSVOutputFormat(getGeoServer());
        format.write(fct, bos, op);

        String csvResponse = bos.toString();

        List<String[]> lines = readLines(csvResponse, ',');

        // check header line contains 2 attributes
        assertEquals(2, lines.get(0).length);

        // check total lines
        assertEquals(2, lines.size());

        // check expected id
        assertEquals("testId", lines.get(1)[0]);

        // check expected list of values as a comma separated string
        assertEquals("prop1,prop2", lines.get(1)[1]);
    }
}
