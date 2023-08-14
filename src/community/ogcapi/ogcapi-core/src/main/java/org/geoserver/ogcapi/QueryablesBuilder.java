/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi;

import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.geoserver.catalog.FeatureTypeInfo;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeType;

public class QueryablesBuilder {

    public static final String POINT_SCHEMA_REF = "https://geojson.org/schema/Point.json";
    public static final String MULTIPOINT_SCHEMA_REF = "https://geojson.org/schema/MultiPoint.json";
    public static final String LINESTRING_SCHEMA_REF = "https://geojson.org/schema/LineString.json";
    public static final String MULTILINESTRING_SCHEMA_REF =
            "https://geojson.org/schema/MultiLineString.json";
    public static final String POLYGON_SCHEMA_REF = "https://geojson.org/schema/Polygon.json";
    public static final String MULTIPOLYGON_SCHEMA_REF =
            "https://geojson.org/schema/MultiPolygon.json";
    public static final String GEOMETRY_SCHEMA_REF = "https://geojson.org/schema/Geometry.json";

    Queryables queryables;

    public QueryablesBuilder(String id) {
        this.queryables = new Queryables(id);
        this.queryables.setType("object");
    }

    public QueryablesBuilder forType(FeatureTypeInfo ft) throws IOException {
        this.queryables.setCollectionId(ft.prefixedName());
        this.queryables.setTitle(Optional.of(ft.getTitle()).orElse(ft.prefixedName()));
        this.queryables.setDescription(ft.getDescription());
        return forType((SimpleFeatureType) ft.getFeatureType());
    }

    public QueryablesBuilder forType(SimpleFeatureType ft) {
        Map<String, Schema> properties =
                ft.getAttributeDescriptors().stream()
                        .collect(
                                Collectors.toMap(
                                        ad -> ad.getLocalName(),
                                        ad -> getSchema(ad.getType()),
                                        (u, v) -> {
                                            throw new IllegalStateException(
                                                    String.format("Duplicate key %s", u));
                                        },
                                        () -> new LinkedHashMap<>()));
        this.queryables.setProperties(properties);
        return this;
    }

    private Schema<?> getSchema(AttributeType type) {
        Class<?> binding = type.getBinding();
        return getSchema(binding);
    }

    /** Returns the schema for a given data type */
    public static Schema<?> getSchema(Class<?> binding) {
        if (Geometry.class.isAssignableFrom(binding)) return getGeometrySchema(binding);
        else return getAlphanumericSchema(binding);
    }

    private static Schema<?> getGeometrySchema(Class<?> binding) {
        Schema schema = new Schema();
        String ref;
        String description;
        if (Point.class.isAssignableFrom(binding)) {
            ref = POINT_SCHEMA_REF;
            description = "Point";
        } else if (MultiPoint.class.isAssignableFrom(binding)) {
            ref = MULTIPOINT_SCHEMA_REF;
            description = "MultiPoint";
        } else if (LineString.class.isAssignableFrom(binding)) {
            ref = LINESTRING_SCHEMA_REF;
            description = "LineString";
        } else if (MultiLineString.class.isAssignableFrom(binding)) {
            ref = MULTILINESTRING_SCHEMA_REF;
            description = "MultiLineString";
        } else if (Polygon.class.isAssignableFrom(binding)) {
            ref = POLYGON_SCHEMA_REF;
            description = "Polygon";
        } else if (MultiPolygon.class.isAssignableFrom(binding)) {
            ref = MULTIPOLYGON_SCHEMA_REF;
            description = "MultiPolygon";
        } else {
            ref = GEOMETRY_SCHEMA_REF;
            description = "Generic geometry";
        }

        schema.set$ref(ref);
        schema.setDescription(description);
        return schema;
    }

    private static Schema<?> getAlphanumericSchema(Class<?> binding) {
        Schema<?> schema = new Schema<>();

        schema.setType(org.geoserver.ogcapi.AttributeType.fromClass(binding).getType());
        schema.setDescription(schema.getType());
        if (java.sql.Date.class.isAssignableFrom(binding)) {
            schema.setFormat("date");
            schema.setDescription("Date");
        } else if (java.sql.Time.class.isAssignableFrom(binding)) {
            schema.setFormat("time");
            schema.setDescription("Time");
        } else if (java.util.Date.class.isAssignableFrom(binding)) {
            schema.setFormat("date-time");
            schema.setDescription("DateTime");
        }
        return schema;
    }

    public Queryables build() {
        return queryables;
    }
}
