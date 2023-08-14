/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.schemalessfeatures.mongodb.data;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.geoserver.schemalessfeatures.data.ComplexContentDataAccess;
import org.geoserver.schemalessfeatures.data.SchemalessFeatureSource;
import org.geoserver.schemalessfeatures.mongodb.MongoSchemalessUtils;
import org.geoserver.schemalessfeatures.mongodb.filter.MongoTypeFinder;
import org.geoserver.schemalessfeatures.mongodb.filter.SchemalessFilterToMongo;
import org.geoserver.schemalessfeatures.type.DynamicFeatureType;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.FilteringFeatureReader;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.mongodb.MongoFilterSplitter;
import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.filter.visitor.PostPreProcessFilterSplittingVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;

public class MongoSchemalessFeatureSource extends SchemalessFeatureSource {

    private MongoCollection<DBObject> collection;

    private MongoTypeFinder dataTypeFinder;

    public MongoSchemalessFeatureSource(
            Name name, MongoCollection<DBObject> collection, ComplexContentDataAccess store) {
        super(name, store);
        this.collection = collection;
        this.dataTypeFinder = new MongoTypeFinder(name, collection);
    }

    @Override
    protected GeometryDescriptor getGeometryDescriptor() {
        String geometryPath = dataTypeFinder.getGeometryPath();
        if (geometryPath == null) return null;
        AttributeTypeBuilder attributeBuilder = new AttributeTypeBuilder();
        attributeBuilder.setBinding(Geometry.class);
        String geometryAttributeName;
        if (geometryPath.indexOf(".") != -1) {
            String[] splitted = geometryPath.split("\\.");
            geometryAttributeName = splitted[splitted.length - 1];
        } else {
            geometryAttributeName = geometryPath;
        }
        attributeBuilder.setName(geometryAttributeName);
        attributeBuilder.setNamespaceURI(name.getNamespaceURI());
        attributeBuilder.setCRS(DefaultGeographicCRS.WGS84);
        GeometryType type = attributeBuilder.buildGeometryType();
        type.getUserData().put(MongoSchemalessUtils.GEOMETRY_PATH, geometryPath);
        return attributeBuilder.buildDescriptor(name(name.getNamespaceURI(), geometryPath), type);
    }

    @Override
    protected FeatureReader<FeatureType, Feature> getReaderInteranl(Query query) {
        List<Filter> postFilterList = new ArrayList<>();
        FeatureReader<FeatureType, Feature> reader =
                new MongoComplexReader(toCursor(query, postFilterList), this);
        if (!postFilterList.isEmpty())
            return new FilteringFeatureReader<>(reader, postFilterList.get(0));
        return reader;
    }

    @Override
    protected int getCountInteral(Query query) {
        DBObject queryDBO = new BasicDBObject();

        Filter f = query.getFilter();
        if (!isAll(f)) {
            Filter[] split = splitFilter(f);
            queryDBO = toQuery(split[0]);
            if (!isAll(split[1])) {
                return -1;
            }
        }
        return Long.valueOf(collection.countDocuments((BasicDBObject) queryDBO)).intValue();
    }

    MongoCursor<DBObject> toCursor(Query q, java.util.List<Filter> postFilter) {
        DBObject query = new BasicDBObject();

        Filter f = q.getFilter();
        if (!isAll(f)) {
            Filter[] split = splitFilter(f);
            query = toQuery(split[0]);
            if (!isAll(split[1])) {
                postFilter.add(split[1]);
            }
        }

        FindIterable<DBObject> it;
        String[] propertyNames = q.getPropertyNames();
        if (propertyNames != Query.ALL_NAMES) {
            DBObject keys = new BasicDBObject();
            for (String p : propertyNames) {
                keys.put(MongoSchemalessUtils.toMongoPath(p), 1);
            }
            // add properties from post filters
            for (Filter postF : postFilter) {
                String[] attributeNames = DataUtilities.attributeNames(postF);
                for (String attrName : attributeNames) {
                    if (attrName != null && !attrName.isEmpty() && !keys.containsField(attrName)) {
                        keys.put(MongoSchemalessUtils.toMongoPath(attrName), 1);
                    }
                }
            }
            String geometryPath = getGeometryPath();
            if (geometryPath != null && !keys.containsField(geometryPath)) {
                keys.put(geometryPath, 1);
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("find(%s, %s)", query, keys));
            }
            it = collection.find((BasicDBObject) query).projection((BasicDBObject) keys);
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("find(%s)", query));
            }
            it = collection.find((BasicDBObject) query);
        }

        if (q.getStartIndex() != null && q.getStartIndex() != 0) {
            it = it.skip(q.getStartIndex());
        }
        if (q.getMaxFeatures() != Integer.MAX_VALUE) {
            it = it.limit(q.getMaxFeatures());
        }

        if (q.getSortBy() != null) {
            BasicDBObject orderBy = new BasicDBObject();
            for (SortBy sortBy : q.getSortBy()) {
                if (sortBy.getPropertyName() != null) {
                    String propName = sortBy.getPropertyName().getPropertyName();
                    String property = MongoSchemalessUtils.toMongoPath(propName);
                    orderBy.append(property, sortBy.getSortOrder() == SortOrder.ASCENDING ? 1 : -1);
                }
            }
            it = it.sort(orderBy);
        }

        return it.cursor();
    }

    DBObject toQuery(Filter f) {
        if (isAll(f)) {
            return new BasicDBObject();
        }

        SchemalessFilterToMongo v =
                new SchemalessFilterToMongo((DynamicFeatureType) getSchema(), collection);

        return (DBObject) f.accept(v, null);
    }

    Filter[] splitFilter(Filter f) {
        PostPreProcessFilterSplittingVisitor splitter =
                new MongoFilterSplitter(getDataStore().getFilterCapabilities(), null, null) {
                    @Override
                    protected void visitBinaryComparisonOperator(BinaryComparisonOperator filter) {
                        Expression expression1 = filter.getExpression1();
                        Expression expression2 = filter.getExpression2();
                        if (expression1 instanceof PropertyName && expression2 instanceof Literal) {
                            preStack.push(filter);
                        } else if (expression2 instanceof PropertyName
                                && expression1 instanceof Literal) {
                            preStack.push(filter);
                        } else {
                            super.visitBinaryComparisonOperator(filter);
                        }
                    }
                };
        f.accept(splitter, null);
        return new Filter[] {splitter.getFilterPre(), splitter.getFilterPost()};
    }

    @Override
    public QueryCapabilities getQueryCapabilities() {
        QueryCapabilities capabilities =
                new QueryCapabilities() {
                    @Override
                    public boolean isOffsetSupported() {
                        return true;
                    }

                    @Override
                    public boolean supportsSorting(SortBy... sortAttributes) {
                        return true;
                    }
                };
        return capabilities;
    }

    private String getGeometryPath() {
        GeometryDescriptor descriptor = getGeometryDescriptor();
        if (descriptor == null) return null;
        return descriptor
                .getType()
                .getUserData()
                .get(MongoSchemalessUtils.GEOMETRY_PATH)
                .toString();
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query q) throws IOException {
        String geometryPath = getGeometryPath();
        if (geometryPath != null) {
            q = new Query(q);
            q.setPropertyNames(MongoSchemalessUtils.toPropertyName(geometryPath));
        }
        return super.getBoundsInternal(q);
    }
}
