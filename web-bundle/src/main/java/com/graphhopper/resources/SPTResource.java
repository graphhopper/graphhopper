package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

/**
 * This resource provides the entire shortest path tree as response. In a simple CSV format discussed at #1577.
 */
@Path("spt")
public class SPTResource {

    private static final Logger logger = LoggerFactory.getLogger(SPTResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

    @Inject
    public SPTResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }
}
