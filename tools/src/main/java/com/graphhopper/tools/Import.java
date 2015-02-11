package com.graphhopper.tools;

import com.graphhopper.GraphHopper;
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
        hopper.close();
    }
}
