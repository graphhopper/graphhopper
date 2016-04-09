package com.graphhopper.matching.http;

import org.json.JSONArray;
import org.json.JSONObject;

import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.GPXExtension;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.util.PointList;

/**
 * Transform MatchResult in Json Object with fallow structure:
 * <pre>
 * { "diary": {
 *   "routes": [
 *     { "links": [
 *        { "geometry": String,
 *          "wpts": [{ "x": Number, "y": Number, "timestamp": Number }]
 *        }]
 *     }]
 * }}
 * </pre>
 */
public class MatchResultToJson {

    protected MatchResult result;

    public MatchResultToJson(MatchResult result) {
        this.result = result;
    }

    public JSONObject exportTo() {
        JSONObject root = new JSONObject();
        JSONObject diary = new JSONObject();
        JSONArray entries = new JSONArray();
        JSONObject route = new JSONObject();
        JSONArray links = new JSONArray();
        for (int emIndex = 0; emIndex < result.getEdgeMatches().size(); emIndex++) {
            JSONObject link = new JSONObject();
            JSONObject geometry = new JSONObject();

            EdgeMatch edgeMatch = result.getEdgeMatches().get(emIndex);
            PointList pointList = edgeMatch.getEdgeState().fetchWayGeometry(emIndex == 0 ? 3 : 2);
           
            if(pointList.size() < 2) {
                geometry.put("coordinates", pointList.toGeoJson().get(0));
                geometry.put("type", "Point");
            } else {
                geometry.put("coordinates", pointList.toGeoJson());
                geometry.put("type", "LineString");
            }

            link.put("id", edgeMatch.getEdgeState().getEdge());
            link.put("geometry", geometry.toString());

            JSONArray wpts = new JSONArray();
            link.put("wpts", wpts);

            for (GPXExtension extension : edgeMatch.getGpxExtensions()) {
                JSONObject wpt = new JSONObject();
                wpt.put("x", extension.getQueryResult().getSnappedPoint().lon);
                wpt.put("y", extension.getQueryResult().getSnappedPoint().lat);
                wpt.put("timestamp", extension.getEntry().getTime());
                wpts.put(wpt);
            }

            links.put(link);
        }

        route.put("links", links);
        entries.put(route);
        diary.put("entries", entries);
        root.put("diary", diary);
        return root;
    }
}
