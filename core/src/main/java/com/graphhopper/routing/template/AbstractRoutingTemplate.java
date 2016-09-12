package com.graphhopper.routing.template;

import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.PointList;

import java.util.List;

/**
 * @author Peter Karich
 */
public class AbstractRoutingTemplate {
    // result from lookup
    protected List<QueryResult> queryResults;

    protected PointList getWaypoints() {
        PointList pointList = new PointList(queryResults.size(), true);
        for (QueryResult qr : queryResults) {
            pointList.add(qr.getSnappedPoint());
        }
        return pointList;
    }
}
