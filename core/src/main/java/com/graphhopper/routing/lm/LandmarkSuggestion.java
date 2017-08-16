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
package com.graphhopper.routing.lm;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class collects landmarks from an external source for one subnetwork to avoid the expensive and sometimes
 * suboptimal automatic landmark finding process.
 */
public class LandmarkSuggestion {
    private List<Integer> nodeIds;
    private BBox box;

    public LandmarkSuggestion(List<Integer> nodeIds, BBox box) {
        this.nodeIds = nodeIds;
        this.box = box;
    }

    public List<Integer> getNodeIds() {
        return nodeIds;
    }

    public BBox getBox() {
        return box;
    }

    /**
     * The expected format is lon,lat per line where lines starting with characters will be ignored. You can create
     * such a file manually via geojson.io -> Save as CSV. Optionally add a second line with
     * <pre>#BBOX:minLat,minLon,maxLat,maxLon</pre>
     *
     * to specify an explicit bounding box. TODO: support GeoJSON instead.
     */
    public static final LandmarkSuggestion readLandmarks(String file, LocationIndex locationIndex) throws IOException {
        // landmarks should be suited for all vehicle profiles
        EdgeFilter edgeFilter = EdgeFilter.ALL_EDGES;
        List<String> lines = Helper.readFile(file);
        List<Integer> landmarkNodeIds = new ArrayList<>();
        BBox bbox = BBox.createInverse(false);
        int lmSuggestionIdx = 0;
        String errors = "";
        for (String lmStr : lines) {
            if (lmStr.startsWith("#BBOX:")) {
                bbox = BBox.parseTwoPoints(lmStr.substring("#BBOX:".length()));
                continue;
            } else if (lmStr.isEmpty() || Character.isAlphabetic(lmStr.charAt(0))) {
                continue;
            }

            GHPoint point = GHPoint.parseLonLat(lmStr);
            if (point == null)
                throw new RuntimeException("Invalid format " + lmStr + " for point " + lmSuggestionIdx);

            lmSuggestionIdx++;
            QueryResult result = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
            if (!result.isValid()) {
                errors += "Cannot find close node found for landmark suggestion[" + lmSuggestionIdx + "]=" + point + ".\n";
                continue;
            }

            bbox.update(point.lat, point.lon);
            landmarkNodeIds.add(result.getClosestNode());
        }

        if (!errors.isEmpty())
            throw new RuntimeException(errors);

        return new LandmarkSuggestion(landmarkNodeIds, bbox);
    }
}
