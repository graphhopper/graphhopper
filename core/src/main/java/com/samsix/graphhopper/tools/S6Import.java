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
import com.samsix.graphhopper.S6GraphHopper;
import com.samsix.graphhopper.TruckFlagEncoder;

public class S6Import
{
    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new S6GraphHopper().init(args);

        List<FlagEncoder> encoders = new ArrayList<FlagEncoder>();
        encoders.add(new S6FootFlagEncoder());
        encoders.add(new NoHighwayFlagEncoder());
        encoders.add(new S6CarFlagEncoder());
        encoders.add(new TruckFlagEncoder());
        encoders.add(new BikeFlagEncoder());
        EncodingManager manager = new EncodingManager(encoders, 8);
        hopper.setEncodingManager(manager);
        
        hopper.importOrLoad();
        hopper.close();
    }
}
