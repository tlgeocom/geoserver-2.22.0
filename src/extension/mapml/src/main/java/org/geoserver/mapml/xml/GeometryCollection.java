//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference
// Implementation, v2.2.8-b130911.1802
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2018.12.17 at 04:13:52 PM PST
//

package org.geoserver.mapml.xml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

/**
 * Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;choice maxOccurs="unbounded" minOccurs="0"&gt;
 *         &lt;element ref="{}Point"/&gt;
 *         &lt;element ref="{}LineString"/&gt;
 *         &lt;element ref="{}Polygon"/&gt;
 *         &lt;element ref="{}MultiPoint"/&gt;
 *         &lt;element ref="{}MultiLineString"/&gt;
 *         &lt;element ref="{}MultiPolygon"/&gt;
 *       &lt;/choice&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "",
        propOrder = {"pointOrLineStringOrPolygon"})
public class GeometryCollection {

    @XmlElements({
        @XmlElement(name = "point", type = Point.class, namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-linestring",
                type = LineString.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-polygon",
                type = Polygon.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-multipoint",
                type = MultiPoint.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-multilinestring",
                type = MultiLineString.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-multipolygon",
                type = MultiPolygon.class,
                namespace = "http://www.w3.org/1999/xhtml")
    })
    protected List<Object> pointOrLineStringOrPolygon;

    /**
     * Gets the value of the pointOrLineStringOrPolygon property.
     *
     * <p>This accessor method returns a reference to the live list, not a snapshot. Therefore any
     * modification you make to the returned list will be present inside the JAXB object. This is
     * why there is not a <CODE>set</CODE> method for the pointOrLineStringOrPolygon property.
     *
     * <p>For example, to add a new item, do as follows:
     *
     * <pre>
     *    getPointOrLineStringOrPolygon().add(newItem);
     * </pre>
     *
     * <p>Objects of the following type(s) are allowed in the list {@link Point } {@link LineString
     * } {@link Polygon } {@link MultiPoint } {@link MultiLineString } {@link MultiPolygon }
     */
    public List<Object> getPointOrLineStringOrPolygon() {
        if (pointOrLineStringOrPolygon == null) {
            pointOrLineStringOrPolygon = new ArrayList<>();
        }
        return this.pointOrLineStringOrPolygon;
    }
}
