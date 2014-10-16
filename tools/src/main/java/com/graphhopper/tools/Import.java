package com.graphhopper.tools;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.RoutingAlgorithmSpecialAreaTests;
import com.graphhopper.util.CmdArgs;

/**
 * @author Peter Karich
 */
public class Import
{
    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new GraphHopper().init(args);
        hopper.importOrLoad();
        if (args.getBool("graph.testIT", false))
        {
            // important: use osmreader.wayPointMaxDistance=0
            RoutingAlgorithmSpecialAreaTests tests = new RoutingAlgorithmSpecialAreaTests(hopper);
            tests.start();
        }
        hopper.close();
    }
}
