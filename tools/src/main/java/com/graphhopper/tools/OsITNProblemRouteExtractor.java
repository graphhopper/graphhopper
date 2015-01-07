package com.graphhopper.tools;

import java.util.Date;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.tools.routeExtractor.AbstractProblemRouteExtractor;
import com.graphhopper.tools.routeExtractor.NodeListRouteExtractor;
import com.graphhopper.tools.routeExtractor.TwoRoadsRouteExtractor;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.GHUtility;

/**
 * This tool is designed to help extract the xml can contributes to a know route
 * with problems argument is the named road for which you wish to extract all
 * referenced nodes and ways. Initial implementation will just extract the
 * directly referenced nodes and ways. A later version should probably also
 * extract all first order connections.
 * 
 * @author stuartadam 
 * 
 */
public class OsITNProblemRouteExtractor {
    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        String fileOrDirName = args.get("osmreader.osm", null);
        String namedRoad = args.get("roadName", null);
        String namedLinkRoad = args.get("linkRoadName", null);
        String nodeList = args.get("nodeList", null);
        AbstractProblemRouteExtractor extractor = null;
        String outputFileName = null;
        if (nodeList==null) {
            System.out.println("Find junction around " + namedRoad + " and " + namedLinkRoad);
            outputFileName = args.get("itnoutput", "os-itn-" + namedRoad.replaceAll(" ", "-").toLowerCase() + (null != namedLinkRoad ? "-" + namedLinkRoad.replaceAll(" ", "-").toLowerCase() : "") + ".xml");
            extractor = new TwoRoadsRouteExtractor(fileOrDirName, namedRoad, namedLinkRoad);
        }
        else {
            System.out.println("Find graph around nodes: " + nodeList);
            outputFileName = args.get("itnoutput", "os-itn-" + new Date().getTime() + ".xml");
            extractor = new NodeListRouteExtractor(fileOrDirName, nodeList);
        }
        extractor.process(outputFileName);
        args.put("reader.implementation", "OSITN");
        args.put("osmreader.osm", outputFileName);
        args.put("graph.flagEncoders", "car|turnCosts=true");
        
        GraphHopper hopper = new GraphHopper().init(args).importOrLoad();
        FlagEncoder carEncoder = hopper.getEncodingManager().getEncoder("CAR");
        EdgeFilter filter = new DefaultEdgeFilter(carEncoder, false, true);

        GHUtility.printInfo(hopper.getGraph(), 0, Integer.MIN_VALUE, filter);
    }

}
