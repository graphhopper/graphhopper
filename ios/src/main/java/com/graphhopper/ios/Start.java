/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.ios;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.XFirstSearch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Peter Karich
 */
public class Start
{

    public static void main( String[] strs ) throws Exception
    {
        // TODO fix logger issue!
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new GraphHopper().init(args);
        String location = args.get("graph.location", null);
        if (location == null)
        {
            System.out.println("location is null?");
            return;
        }

        if (!hopper.load(location))
        {
            System.out.println("cannot open " + location);
            return;
        }

        GraphStorage storage = ((GraphStorage) hopper.getGraph());
        System.out.println("graph:" + storage.getNodes() + " " + storage.getDirectory().getDefaultType());
        final AtomicLong c = new AtomicLong(0);
        new XFirstSearch()
        {
            @Override
            protected boolean goFurther( int nodeId )
            {
                c.incrementAndGet();
                return true;
            }
        }.start(storage.createEdgeExplorer(), 0, true);
        System.out.println("explored: " + c.get() + " nodes");
    }
}
