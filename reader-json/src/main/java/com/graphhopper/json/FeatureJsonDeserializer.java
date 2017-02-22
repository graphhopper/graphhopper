/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.json;

import com.graphhopper.json.geo.*;
import com.google.gson.*;
import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.util.shapes.BBox;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;

/**
 * This class makes reading the different geometry types possible for Gson.
 *
 * @author Peter Karich
 */
public class FeatureJsonDeserializer implements JsonDeserializer<JsonFeature> {
    @Override
    public JsonFeature deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        try {
            JsonObject obj = json.getAsJsonObject();
            String id, strType = null;
            Map<String, Object> properties = null;
            BBox bbox = null;
            Geometry geometry = null;

            // TODO ensure uniqueness
            if (obj.has("id"))
                id = obj.get("id").getAsString();
            else
                id = UUID.randomUUID().toString();

            if (obj.has("properties")) {
                properties = context.deserialize(obj.get("properties"), Map.class);
            }

            if (obj.has("bbox"))
                bbox = parseBBox(obj.get("bbox").getAsJsonArray());

            if (obj.has("geometry")) {
                JsonObject geometryJson = obj.get("geometry").getAsJsonObject();

                if (geometryJson.has("coordinates")) {
                    if (!geometryJson.has("type"))
                        throw new IllegalArgumentException("No type for non-empty coordinates specified");

                    strType = context.deserialize(geometryJson.get("type"), String.class);
                    if ("Point".equals(strType)) {
                        JsonArray arr = geometryJson.get("coordinates").getAsJsonArray();
                        double lon = arr.get(0).getAsDouble();
                        double lat = arr.get(1).getAsDouble();
                        if (arr.size() == 3)
                            geometry = new Point(lat, lon, arr.get(2).getAsDouble());
                        else
                            geometry = new Point(lat, lon);

                    } else if ("MultiPoint".equals(strType)) {
                        geometry = parseLineString(geometryJson);

                    } else if ("LineString".equals(strType)) {
                        geometry = parseLineString(geometryJson);

                    } else if ("Polygon".equals(strType)) {
                        geometry = parsePolygonString(geometryJson);
                    } else if ("MultiPolygon".equals(strType)) {
                        geometry = parsePolygonString(geometryJson);
                    } else {
                        throw new IllegalArgumentException("Coordinates type " + strType + " not yet supported");
                    }
                }
            }

            return new JsonFeature(id, strType, bbox, geometry, properties);

        } catch (Exception ex) {
            throw new JsonParseException("Problem parsing JSON feature " + json, ex);
        }
    }

    LineString parseLineString(JsonObject geometry) {
        JsonArray arr = geometry.get("coordinates").getAsJsonArray();
        boolean is3D = arr.size() == 0 || arr.get(0).getAsJsonArray().size() == 3;
        LineString lineString = new LineString(arr.size(), is3D);

        for (int i = 0; i < arr.size(); i++) {
            JsonArray pointArr = arr.get(i).getAsJsonArray();
            double lon = pointArr.get(0).getAsDouble();
            double lat = pointArr.get(1).getAsDouble();
            if (pointArr.size() == 3)
                lineString.add(lat, lon, pointArr.get(2).getAsDouble());
            else
                lineString.add(lat, lon);
        }
        return lineString;
    }

    GeoJsonPolygon parsePolygonString(JsonObject geometry) {
        JsonArray arr = geometry.get("coordinates").getAsJsonArray();
        GeoJsonPolygon geoJsonPolygon = new GeoJsonPolygon();

        if (geometry.get("type").getAsString().equals("Polygon")) {
            geoJsonPolygon.addPolygon(parseSinglePolygonCoordinates(arr));
        } else {
            for (int i = 0; i < arr.size(); i++) {
                geoJsonPolygon.addPolygon(parseSinglePolygonCoordinates(arr.get(i).getAsJsonArray()));
            }
        }
        return geoJsonPolygon;
    }

    private Polygon parseSinglePolygonCoordinates(JsonArray arr) {
        if (arr.size() == 0) {
            throw new IllegalStateException("The passed Array should be of format: [[[coords1],[coords2],....[coordsN]]]");
        }
        /*
         TODO We currently ignore holes/interior rings the spec defines:
        For type "Polygon", the "coordinates" member must be an array of LinearRing coordinate arrays.
        For Polygons with multiple rings, the first must be the exterior ring and any others must be
        interior rings or holes.
         */
        JsonArray polygonArr = arr.get(0).getAsJsonArray();

        double[] lats = new double[polygonArr.size()];
        double[] lons = new double[polygonArr.size()];

        for (int i = 0; i < polygonArr.size(); i++) {
            JsonArray pointArr = polygonArr.get(i).getAsJsonArray();
            lons[i] = pointArr.get(0).getAsDouble();
            lats[i] = pointArr.get(1).getAsDouble();
        }

        return new Polygon(lats, lons);
    }

    private BBox parseBBox(JsonArray arr) {
        // The value of the bbox member must be a 2*n array where n is the number of dimensions represented 
        // in the contained geometries, with the lowest values for all axes followed by the highest values. 
        // The axes order of a bbox follows the axes order of geometries => lon,lat,ele
        if (arr.size() == 6) {
            double minLon = arr.get(0).getAsDouble();
            double minLat = arr.get(1).getAsDouble();
            double minEle = arr.get(2).getAsDouble();

            double maxLon = arr.get(3).getAsDouble();
            double maxLat = arr.get(4).getAsDouble();
            double maxEle = arr.get(5).getAsDouble();

            return new BBox(minLon, maxLon, minLat, maxLat, minEle, maxEle);

        } else if (arr.size() == 4) {
            double minLon = arr.get(0).getAsDouble();
            double minLat = arr.get(1).getAsDouble();

            double maxLon = arr.get(2).getAsDouble();
            double maxLat = arr.get(3).getAsDouble();

            return new BBox(minLon, maxLon, minLat, maxLat);
        } else {
            throw new IllegalArgumentException("Illegal array dimension (" + arr.size() + ") of bbox " + arr.toString());
        }
    }
}
