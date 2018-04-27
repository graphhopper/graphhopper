package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.isochrone.algorithm.RasterHullBuilder;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.GHException;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final RasterHullBuilder rasterHullBuilder;

    @Inject
    public IsochroneResource(GraphHopper graphHopper, EncodingManager encodingManager, RasterHullBuilder rasterHullBuilder) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.rasterHullBuilder = rasterHullBuilder;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @QueryParam("weighting") @DefaultValue("fastest") String weightingStr,
            @QueryParam("buckets") @DefaultValue("1") int buckets,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") GHPoint point,
            @QueryParam("result") @DefaultValue("polygon") String resultStr,
            @QueryParam("time_limit") @DefaultValue("600") long timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") double distanceInMeter) {

        if (buckets > 20 || buckets < 1)
            throwArgExc("Number of buckets has to be in the range [1, 20]");

        if (point == null)
            throwArgExc("point parameter cannot be null");

        StopWatch sw = new StopWatch().start();

        if (!encodingManager.supports(vehicle))
            throwArgExc("vehicle not supported:" + vehicle);

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
        if (!qr.isValid())
            throwArgExc("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(Collections.singletonList(qr));

        HintsMap hintsMap = new HintsMap();

        initHints(hintsMap, uriInfo.getQueryParameters());

        Weighting weighting = graphHopper.createWeighting(hintsMap, encoder, graph);
        Isochrone isochrone = new Isochrone(queryGraph, weighting, reverseFlow);

        if (distanceInMeter > 0) {
            double maxMeter = 50 * 1000;
            if (distanceInMeter > maxMeter)
                throwArgExc("Specify a limit of less than " + maxMeter / 1000f + "km");
            if (buckets > (distanceInMeter / 500))
                throwArgExc("Specify buckets less than the number of explored kilometers");

            isochrone.setDistanceLimit(distanceInMeter);
        } else {

            long maxSeconds = 80 * 60;
            if (timeLimitInSeconds > maxSeconds)
                throwArgExc("Specify a limit of less than " + maxSeconds + " seconds");
            if (buckets > (timeLimitInSeconds / 60))
                throwArgExc("Specify buckets less than the number of explored minutes");

            isochrone.setTimeLimit(timeLimitInSeconds);
        }

        List<List<Double[]>> list = isochrone.searchGPS(qr.getClosestNode(), buckets);
        if (isochrone.getVisitedNodes() > graphHopper.getMaxVisitedNodes() / 5) {
            throwArgExc("Server side reset: too many junction nodes would have to explored (" + isochrone.getVisitedNodes() + "). Let us know if you need this increased.");
        }

        int counter = 0;
        for (List<Double[]> tmp : list) {
            if (tmp.size() < 2) {
                throwArgExc("Too few points found for bucket " + counter + ". "
                        + "Please try a different 'point', a smaller 'buckets' count or a larger 'time_limit'. "
                        + "And let us know if you think this is a bug!");
            }
            counter++;
        }

        Object calcRes;
        if ("pointlist".equalsIgnoreCase(resultStr)) {
            calcRes = list;

        } else if ("polygon".equalsIgnoreCase(resultStr)) {
            list = rasterHullBuilder.calcList(list, list.size() - 1);

            ArrayList polyList = new ArrayList();
            int index = 0;
            for (List<Double[]> polygon : list) {
                HashMap<String, Object> geoJsonMap = new HashMap<>();
                HashMap<String, Object> propMap = new HashMap<>();
                HashMap<String, Object> geometryMap = new HashMap<>();
                polyList.add(geoJsonMap);
                geoJsonMap.put("type", "Feature");
                geoJsonMap.put("properties", propMap);
                geoJsonMap.put("geometry", geometryMap);

                propMap.put("bucket", index);
                geometryMap.put("type", "Polygon");
                // we have no holes => embed in yet another list
                geometryMap.put("coordinates", Collections.singletonList(polygon));
                index++;
            }
            calcRes = polyList;
        } else {
            throw new WebApplicationException(jsonErrorResponse(Collections.singletonList(new IllegalArgumentException("type not supported:" + resultStr))));
        }

        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + isochrone.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
        return Response.fromResponse(jsonSuccessResponse(calcRes, sw.stop().getSeconds()))
                .header("X-GH-Took", "" + sw.stop().getSeconds() * 1000)
                .build();
    }

    private void throwArgExc(String msg) {
        throw new WebApplicationException(jsonErrorResponse(Collections.singletonList(new IllegalArgumentException(msg))));
    }


    private Response jsonErrorResponse(List<Throwable> errors) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("message", getMessage(errors.get(0)));
        ArrayNode errorHintList = json.putArray("hints");
        for (Throwable t : errors) {
            ObjectNode error = errorHintList.addObject();
            error.put("message", getMessage(t));
            error.put("details", t.getClass().getName());
            if (t instanceof GHException) {
                ((GHException) t).getDetails().forEach(error::putPOJO);
            }
        }
        return Response.status(SC_BAD_REQUEST).entity(json).build();
    }

    private String getMessage(Throwable t) {
        if (t.getMessage() == null)
            return t.getClass().getSimpleName();
        else
            return t.getMessage();
    }

    // TODO Copied from RouteResource
    private void initHints(HintsMap m, MultivaluedMap<String, String> parameterMap) {
        for (Map.Entry<String, List<String>> e : parameterMap.entrySet()) {
            if (e.getValue().size() == 1) {
                m.put(e.getKey(), e.getValue().get(0));
            } else {
                // Do nothing.
                // TODO: this is dangerous: I can only silently swallow
                // the forbidden multiparameter. If I comment-in the line below,
                // I get an exception, because "point" regularly occurs
                // multiple times.
                // I think either unknown parameters (hints) should be allowed
                // to be multiparameters, too, or we shouldn't use them for
                // known parameters either, _or_ known parameters
                // must be filtered before they come to this code point,
                // _or_ we stop passing unknown parameters alltogether..
                //
                // throw new WebApplicationException(String.format("This query parameter (hint) is not allowed to occur multiple times: %s", e.getKey()));
            }
        }
    }

    private Response jsonSuccessResponse(Object result, float took) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.putPOJO("polygons", result);
        // If you replace GraphHopper with your own brand name, this is fine.
        // Still it would be highly appreciated if you mention us in your about page!
        final ObjectNode info = json.putObject("info");
        info.putArray("copyrights")
                .add("GraphHopper")
                .add("OpenStreetMap contributors");
        info.put("took", Math.round(took * 1000));

        return Response.ok(json).build();
    }
}
