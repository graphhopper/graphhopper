package com.samsix.graphhopper.tools;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.CmdArgs;
import com.samsix.graphhopper.NoHighwayFlagEncoder;
import com.samsix.graphhopper.S6CarFlagEncoder;
import com.samsix.graphhopper.S6FootFlagEncoder;
import com.samsix.graphhopper.S6GHUtils;
import com.samsix.graphhopper.S6GraphHopper;
import com.samsix.graphhopper.TruckFlagEncoder;

public class S6Import
{
    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new S6GraphHopper().init(args);

        hopper.setEncodingManager(S6GHUtils.getS6EncodingManager());
        
        hopper.importOrLoad();
        hopper.close();
    }
}
