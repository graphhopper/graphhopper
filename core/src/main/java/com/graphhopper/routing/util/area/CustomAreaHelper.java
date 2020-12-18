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
package com.graphhopper.routing.util.area;

import com.graphhopper.config.CustomArea;
import com.graphhopper.config.CustomAreaFile;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.shapes.BBox;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.PolygonExtracter;

import static com.graphhopper.util.Helper.toLowerCase;

import java.util.*;

/**
 * Helper class to build the custom areas. This is kind of an ugly plugin
 * mechanism to avoid requiring a jackson dependency on the core.
 *
 * @author Robin Boldt
 * @author Thomas Butz
 */
public class CustomAreaHelper {

    private static final GeometryFactory FAC = new GeometryFactory();
    private static final Envelope WORLD_BBOX = new Envelope(-180, 180, -90, 90);

    private CustomAreaHelper() {
    }
    
    public static List<CustomArea> loadAreas(CustomAreaFile customAreaFile, JsonFeatureCollection geoJson) {
        String idField = customAreaFile.getIdField();
        Envelope bbox = BBox.toEnvelope(BBox.parseBBoxString(customAreaFile.getMaxBbox()));
        int evLimit = customAreaFile.getEncodedValueLimit() == -1 ? geoJson.getFeatures().size() : customAreaFile.getEncodedValueLimit();
        
        return loadAreas(geoJson, idField, bbox, customAreaFile.getEncodedValue(), evLimit);
    }
    
    public static List<CustomArea> loadAreas(JsonFeatureCollection geoJson, String idField) {
        return loadAreas(geoJson, idField, WORLD_BBOX);
    }
    
    public static List<CustomArea> loadAreas(JsonFeatureCollection geoJson, String idField, Envelope bbox) {
        return loadAreas(geoJson, idField, bbox, "", -1);
    }

    /**
     * Extracts the borders from the given {@link JsonFeatureCollection} and attempts to create a
     * {@link CustomArea} for each feature.
     * 
     * @param geoJson
     *            a {@link JsonFeatureCollection} describing borders of custom areas
     * @param idField
     *            the property of the {@link JsonFeature} to be used as the ID when creating the
     *            areas
     * @param bbox
     *            the generated {@link CustomArea CustomAreas} are guaranteed to be within the
     *            {@link Envelope}. Only parts of the border {@link Polygon Polygons} within the
     *            boundingbox are retained.
     * @param encodedValue
     *            the name to be used for the encoded value: {@link CustomArea#getEncodedValue()}
     * @param encodedValueLimit
     *            the expected number of entries to be stored in the encoded value:
     *            {@link CustomArea#getEncodedValueLimit()}
     * @return a (possibly empty) List of {@link CustomArea CustomAreas}
     */
    private static List<CustomArea> loadAreas(JsonFeatureCollection geoJson, String idField,
                    Envelope bbox, String encodedValue, int encodedValueLimit) {
        Geometry bboxGeometry = FAC.toGeometry(bbox);

        List<CustomArea> customAreas = new ArrayList<>();
        for (JsonFeature jsonFeature : geoJson.getFeatures()) {
            String id = getId(jsonFeature, idField);
            List<Polygon> borders = intersections(jsonFeature.getGeometry(), id, bboxGeometry);

            if (!borders.isEmpty()) {
                customAreas.add(new CustomArea(id, borders, encodedValue, encodedValueLimit));
            }
        }
        
        return customAreas;
    }
    
    private static List<Polygon> intersections(Geometry jsonGeometry, String id, Geometry bboxGeometry) {
        List<Polygon> borders = new ArrayList<>();
        for (int i = 0; i < jsonGeometry.getNumGeometries(); i++) {
            Geometry poly = jsonGeometry.getGeometryN(i);
            if (!(poly instanceof Polygon)) {
                throw new IllegalArgumentException("Geometry for " + id + " (" + i
                                + ") not supported " + poly.getClass().getSimpleName());
            }
            Geometry intersection = bboxGeometry.intersection(poly);
            if (!intersection.isEmpty()) {
                PolygonExtracter.getPolygons(intersection, borders);
            }
        }
        return borders;
    }
    
    private static String getId(JsonFeature jsonFeature, String jsonIdField) {
        String id;
        if (jsonIdField.isEmpty() || "id".equalsIgnoreCase(jsonIdField)) {
            id = jsonFeature.getId();
        } else {
            id = (String) jsonFeature.getProperty(jsonIdField);
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID cannot be empty for JsonFeature");
        }
        return toLowerCase(id);
    }
}
