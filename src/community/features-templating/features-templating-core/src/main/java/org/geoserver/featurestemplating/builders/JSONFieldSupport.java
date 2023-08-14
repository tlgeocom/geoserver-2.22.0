/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.builders;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.filter.function.JsonPointerFunction;
import org.geotools.util.Converters;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.expression.Expression;

/**
 * Supports handling Feature properties backed by JSON columns. Currently supports native JSON and
 * JSONB fields from PostgresSQL, as well as usage of the <code>jsonPointer</code> function
 */
public class JSONFieldSupport {

    static final Logger LOGGER = Logging.getLogger(JSONFieldSupport.class);

    // Allows extraction of native type name information for JSON columns
    // TODO: have a cross-database indication that a string is actually a JSON payload
    public static final String JDBC_NATIVE_TYPENAME = "org.geotools.jdbc.nativeTypeName";

    /**
     * Used to parse JSON strings into JSON trees. Single instance as creation is expensive, and the
     * object is shareable and thread safe
     */
    private static ObjectMapper MAPPER =
            new ObjectMapper(new JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS));

    /**
     * Checks if the current result can be evaluated into a JSONNode, based on type information
     * coming from JDBC data stores, and if so, turns it into a JSONNode for encoding "as-is"
     */
    public static Object parseWhenJSON(Expression expression, Object contextObject, Object result) {
        try {
            if (contextObject instanceof ComplexAttribute) {
                // see if there is an indication it was a JSON field
                if (expression instanceof JsonPointerFunction
                        || isJSONField(
                                Optional.ofNullable(((ComplexAttribute) contextObject).getType())
                                        .map(ct -> expression.evaluate(ct))
                                        .filter(d -> d instanceof PropertyDescriptor)
                                        .map(d -> (PropertyDescriptor) d))) {
                    return parseJSON(result);
                }
            } else if (result instanceof Attribute) {
                if (isJSONField(Optional.of(((Attribute) result).getDescriptor()))) {
                    return parseJSON(result);
                }
            }
        } catch (JsonProcessingException ex) {
            LOGGER.log(
                    Level.FINE,
                    "Failed to parse JSON from attribute that was supposed to be a JSON field");
        }
        // fall back on the original value otherwise
        return result;
    }

    /**
     * Checks if the given PropertyDescriptor is backed by a JSON field. For the time being, the
     * code supports recognition of JSON/B columns in PostgreSQL, could be expanded later.
     */
    public static boolean isJSONField(PropertyDescriptor opd) {
        return isJSONField(Optional.ofNullable(opd));
    }

    private static boolean isJSONField(Optional<PropertyDescriptor> opd) {
        return opd.map(pd -> pd.getUserData().get(JDBC_NATIVE_TYPENAME))
                .filter(t -> t instanceof String)
                .map(t -> (String) t)
                .filter(t -> "JSON".equalsIgnoreCase(t) || "JSONB".equalsIgnoreCase(t))
                .isPresent();
    }

    /**
     * Tries to parse the object into a JSONNode. Supported inputs are a String, or an {@link
     * Attribute} wrapping a string.
     */
    public static Object parseJSON(Object value) throws JsonProcessingException {
        String json = getJSON(value);
        if (json != null) return MAPPER.readTree(json);

        return value;
    }

    /** Extracts the JSON string from the value, if possible */
    private static String getJSON(Object value) {
        if (value instanceof Attribute) {
            return Converters.convert(((Attribute) value).getValue(), String.class);
        }
        return Converters.convert(value, String.class);
    }
}
