package com.samsix.graphhopper.tools;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.CmdArgs;
import com.samsix.graphhopper.FeederPatrolFlagEncoder;
import com.samsix.graphhopper.S6FootFlagEncoder;
import com.samsix.graphhopper.S6GraphHopper;
import com.samsix.graphhopper.TruckServiceFlagEncoder;

public class S6Import
{
    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new S6GraphHopper().init(args);

        List<FlagEncoder> encoders = new ArrayList<FlagEncoder>();
        encoders.add(new S6FootFlagEncoder());
        encoders.add(new FeederPatrolFlagEncoder());
        encoders.add(new CarFlagEncoder());
        encoders.add(new TruckServiceFlagEncoder());
        EncodingManager manager = new EncodingManager(encoders, 8);
        hopper.setEncodingManager(manager);
        
        hopper.importOrLoad();
        hopper.close();
    }
}
