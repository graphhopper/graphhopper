package com.samsix.graphhopper.tools;

import com.graphhopper.GraphHopper;
import com.graphhopper.util.CmdArgs;
import com.samsix.graphhopper.S6GHUtils;

public class S6Import
{
    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new GraphHopper().init(args);

        hopper.setEncodingManager(S6GHUtils.getS6EncodingManager());

        hopper.importOrLoad();
        hopper.close();
    }
}
