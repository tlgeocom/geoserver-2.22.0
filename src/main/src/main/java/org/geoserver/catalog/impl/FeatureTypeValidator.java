/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.ValidationException;
import org.geotools.data.DataAccess;
import org.geotools.data.DataStore;
import org.geotools.filter.FilterAttributeExtractor;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.filter.visitor.ExpressionTypeVisitor;
import org.geotools.util.Converters;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.expression.Expression;

/** Validates a feature type attributes */
class FeatureTypeValidator {

    public void validate(FeatureTypeInfo fti) {
        List<AttributeTypeInfo> attributes = fti.getAttributes();
        if (attributes == null || attributes.isEmpty()) return;

        // only checking simple features
        try {
            DataAccess access = fti.getStore().getDataStore(null);
            if (!(access instanceof DataStore)) return;
            DataStore ds = (DataStore) access;

            SimpleFeatureType ft = ds.getSchema(fti.getNativeName());
            Map<String, AttributeDescriptor> nativeAttributes =
                    ft.getAttributeDescriptors().stream()
                            .collect(Collectors.toMap(ad -> ad.getLocalName(), ad -> ad));

            // validate each attribute
            Set<String> names = new HashSet<>();
            for (AttributeTypeInfo attribute : attributes) {
                validate(attribute, ft, nativeAttributes);
                // check for duplicate attribute names
                String name = attribute.getName();
                if (names.contains(name))
                    throw new ValidationException(
                            "multiAttributeSameName",
                            "Found multiple definitions for output attribute {0}",
                            name);
                names.add(name);
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to access data source to check attribute customization", e);
        }
    }

    private void validate(
            AttributeTypeInfo attribute,
            SimpleFeatureType schema,
            Map<String, AttributeDescriptor> nativeAttributes) {
        try {
            if (attribute.getName() == null || attribute.getName().isEmpty())
                throw new ValidationException(
                        "attributeNullName", "Attribute name must not be null or empty");

            if (attribute.getSource() == null || attribute.getSource().isEmpty())
                throw new ValidationException(
                        "attributeNullSource", "Attribute source must not be null or empty");

            // parses the CQL expression, will throw exception if invalid
            Expression expression = ECQL.toExpression(attribute.getSource());

            // look for source attributes that might be missing
            FilterAttributeExtractor extractor = new FilterAttributeExtractor(schema);
            expression.accept(extractor, null);
            Set<String> usedSourceAttributes = new HashSet<>(extractor.getAttributeNameSet());
            usedSourceAttributes.removeAll(nativeAttributes.keySet());
            if (!usedSourceAttributes.isEmpty()) {
                throw new ValidationException(
                        "cqlUsesInvalidAttribute",
                        "The CQL source expression for attribute {0} refers to attributes unavailable in the data source: {1}",
                        attribute.getName(),
                        usedSourceAttributes);
            }

            // can we perform the eventual type conversion?
            Class<?> binding = attribute.getBinding();
            if (binding != null) {
                ExpressionTypeVisitor typeVisitor = new ExpressionTypeVisitor(schema);
                Class expressionType = (Class) expression.accept(typeVisitor, null);
                if (!Object.class.equals(expressionType)
                        && !expressionType.equals(binding)
                        && !binding.equals(String.class) // can do even without a Converter
                        && Converters.getConverterFactories(expressionType, binding).isEmpty()) {
                    throw new ValidationException(
                            "attributeInvalidConversion",
                            "Issue found in attribute {0}, unable to convert from native type, {1}, to target type, {2}",
                            attribute.getName(),
                            expressionType.getName(),
                            binding.getName());
                }
            }
        } catch (CQLException e) {
            throw new ValidationException(
                    "attributeInvalidCQL",
                    "Invalid CQL for {0} source. {1}",
                    attribute.getName(),
                    e.getMessage());
        }
    }
}
