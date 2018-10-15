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
package com.graphhopper.resources;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;

/**
 * Resource to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson array format [longitude,latitude] unlike the format "lat,lon"
 * used for the request. See the full API response format in docs/web/api-doc.md
 *
 * @author Peter Karich
 */
@Path("index")
public class LocationIndexResource {

    private static final Logger logger = LoggerFactory.getLogger(LocationIndexResource.class);

    private final LocationIndexTree index;
    private final NodeAccess nodeAccess;

    @Inject
    public LocationIndexResource(LocationIndexTree index, NodeAccess nodeAccess) {
        this.index = index;
        this.nodeAccess = nodeAccess;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(@QueryParam("bbox") String bboxAsString) {
        final BBox bbox;

        try {
            bbox = BBox.parseTwoPoints(bboxAsString);
        } catch (Exception ex) {
            throw new IllegalArgumentException("BBox has wrong format " + bboxAsString);
        }
        Collection<Integer> res = index.query(bbox);
        GeometryFactory gf = new GeometryFactory();
        int size = res.size();
        if (res.size() > 5_000) {
            // TODO make this illegalargument
            size = 5_000;
            logger.warn("Too many elements: " + res.size() + " ... we are skipping a few until " + size);
        }

        Point[] points = new Point[size];
        int counter = 0;
        for (int nodeId : res) {
            if (counter >= size)
                break;

            points[counter] = gf.createPoint(new Coordinate(nodeAccess.getLongitude(nodeId), nodeAccess.getLatitude(nodeId)));
            counter++;
        }

        MultiPoint mp = new MultiPoint(points, gf);
        return Response.ok(mp).build();
    }
}
