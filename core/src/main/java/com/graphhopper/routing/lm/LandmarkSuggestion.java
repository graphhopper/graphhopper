package com.graphhopper.routing.lm;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.Helper;
import com.graphhopper.core.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class collects landmarks from an external source for one subnetwork to avoid the expensive and sometimes
 * suboptimal automatic landmark finding process.
 */
public class LandmarkSuggestion {
    private final List<Integer> nodeIds;
    private final BBox box;

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
     * <p>
     * to specify an explicit bounding box. TODO: support GeoJSON instead.
     */
    public static LandmarkSuggestion readLandmarks(String file, LocationIndex locationIndex) throws IOException {
        // landmarks should be suited for all vehicles
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

            GHPoint point = GHPoint.fromStringLonLat(lmStr);
            if (point == null)
                throw new RuntimeException("Invalid format " + lmStr + " for point " + lmSuggestionIdx);

            lmSuggestionIdx++;
            Snap result = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
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
