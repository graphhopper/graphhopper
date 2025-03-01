package com.conveyal.gtfs.model;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.conveyal.gtfs.GTFSFeed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

public record Location(String id, Geometry geometry, String stop_name, String stop_desc) implements Serializable {

    public static void loadLocations(GTFSFeed gtfsFeed, File file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        InputStream is = Entity.Loader.getInputStreamFromZipOrDirectory(file, "locations.geojson");
        if (is != null) {
            JsonFeatureCollection featureCollection = objectMapper.readValue(is, JsonFeatureCollection.class);
            for (JsonFeature feature : featureCollection.getFeatures()) {
                Location location = new Location(feature.getId(), feature.getGeometry(), (String) feature.getProperties().get("stop_name"), (String) feature.getProperties().get("stop_desc"));
                gtfsFeed.locations.put(location.id, location);
            }
        }
    }

}
