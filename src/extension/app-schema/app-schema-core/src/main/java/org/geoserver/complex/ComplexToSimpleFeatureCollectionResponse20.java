/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.complex;

import java.util.Arrays;
import java.util.List;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;

/** Feature collection response wrapper for WFS 2.0. */
class ComplexToSimpleFeatureCollectionResponse20 extends FeatureCollectionResponse.WFS20 {

    private SimpleFeatureCollection featureCollection;

    public ComplexToSimpleFeatureCollectionResponse20(
            FeatureCollectionResponse response, SimpleFeatureCollection featureCollection) {
        super(response.getAdaptee());
        this.featureCollection = featureCollection;
    }

    @Override
    public List<FeatureCollection> getFeatures() {
        return Arrays.asList(featureCollection);
    }

    @Override
    public List<FeatureCollection> getFeature() {
        return getFeatures();
    }
}
