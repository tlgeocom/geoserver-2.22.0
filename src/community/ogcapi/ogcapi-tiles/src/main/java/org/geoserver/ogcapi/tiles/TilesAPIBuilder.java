/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi.tiles;

import com.google.common.collect.Streams;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.ogcapi.OpenAPIBuilder;

/** Builds the OpenAPI definition for the tiles service */
public class TilesAPIBuilder extends OpenAPIBuilder<TilesServiceInfo> {

    private final GWC gwc;

    public TilesAPIBuilder(GWC gwc) {
        super(TilesServiceInfo.class, "openapi.yaml", "Tiles API", "ogc/tiles");
        this.gwc = gwc;
    }

    @Override
    @SuppressWarnings("unchecked") // getSchema not generified
    public OpenAPI build(TilesServiceInfo service) throws IOException {
        OpenAPI api = super.build(service);

        // adjust path output formats
        declareGetResponseFormats(api, "/collections", TiledCollectionsDocument.class);
        declareGetResponseFormats(
                api, "/collections/{collectionId}", TiledCollectionsDocument.class);

        // the external documentation
        api.externalDocs(
                new ExternalDocumentation()
                        .description("Tiles specification")
                        .url("https://github.com/opengeospatial/OGC-API-Map-Tiles"));

        // provide a list of valid values for collectionId
        Map<String, Parameter> parameters = api.getComponents().getParameters();
        Parameter collectionId = parameters.get("collectionId");

        List<String> validCollectionIds =
                Streams.stream(gwc.getTileLayers())
                        .map(
                                tl ->
                                        tl instanceof GeoServerTileLayer
                                                ? ((GeoServerTileLayer) tl).getContextualName()
                                                : tl.getName())
                        .collect(Collectors.toList());
        collectionId.getSchema().setEnum(validCollectionIds);

        return api;
    }
}
