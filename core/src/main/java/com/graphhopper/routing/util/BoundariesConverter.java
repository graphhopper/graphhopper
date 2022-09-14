package com.graphhopper.routing.util;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import org.locationtech.jts.geom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BoundariesConverter {
    public static void main(String[] args) throws IOException {
        String input = args[0]; // geojson
        String output = args[1];// geojson
        new BoundariesConverter().convert(input, output);
    }

    void convert(String input, String output) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        int converted = 0;

        System.out.println("convert from " + input + " to " + output);

        GeometryFactory gf = new GeometryFactory();
        JsonFeatureCollection newCollection = new JsonFeatureCollection();
        try (Reader reader = new InputStreamReader(new FileInputStream(input), StandardCharsets.UTF_8)) {
            JsonFeatureCollection jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
            for (JsonFeature feature : jsonFeatureCollection.getFeatures()) {
                String id = (String) feature.getProperties().get(Country.ALPHA2);
                if (id == null) continue;

                if (feature.getGeometry() instanceof MultiPolygon) {
                    converted++;

                    JsonFeature newFeature = new JsonFeature();
                    newFeature.setId(id);
                    newFeature.setProperties(new HashMap<>(feature.getProperties().size()));
                    newFeature.getProperties().putAll(feature.getProperties());
                    if (feature.getGeometry().getNumGeometries() == 1) {
                        Geometry geometry = feature.getGeometry().getGeometryN(0);
                        List<Coordinate> coordList = new ArrayList<>();
                        List<Polygon> polygons = new ArrayList<>();
                        Coordinate firstCoord = null;
                        for (Coordinate c : geometry.getCoordinates()) {
                            if (coordList.isEmpty())
                                firstCoord = c;
                            coordList.add(c);
                            if (coordList.size() > 1 && c.equals(firstCoord)) {
                                polygons.add(gf.createPolygon(coordList.toArray(new Coordinate[0])));
                                coordList.clear();
                            }
                        }

                        newFeature.setGeometry(gf.createMultiPolygon(polygons.toArray(new Polygon[0])));
                        feature = newFeature;
                    } else {
                        System.out.println("WARN MultiPolygon with geometries > 1 !? " + feature.getProperties());
                    }
                }
                newCollection.getFeatures().add(feature);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        System.out.println("converted " + converted + " MultiPolygons");
        System.out.println("features " + newCollection.getFeatures().size());

        objectMapper.writeValue(new FileWriter(output), newCollection);
    }
}
