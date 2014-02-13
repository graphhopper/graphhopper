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

package com.graphhopper.routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ViaRouting;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.TestAlgoCollector;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 * Perform testing of "via" functionality with real data
 * <p/>
 * @author ratrun
 */
public class ViaRoutingTest
{
    
    TestAlgoCollector testCollector;
    private LocationIndex idx;
    private final DistanceCalc distCalc = new DistanceCalcEarth();
    
    @Before
    public void setUp()
    {
        testCollector = new TestAlgoCollector("core integration tests");
    }
    
    List<GeoLocation> createMonacoCar()
    {
        List<GeoLocation> res = new ArrayList<GeoLocation>(2);
        res.add( new GeoLocation(43.730729, 7.42135 , 0, 0 ));
        res.add( new GeoLocation(43.727697, 7.419199 , 2581, 91 ));
        res.add( new GeoLocation(43.726387,7.40919 , 2300, 90 ));
        return res;
    }
    
    QueryResult[] getArray(List<GeoLocation> list, EdgeFilter edgeFilter)
    {
        List<QueryResult> reslist = new ArrayList<QueryResult>(list.size());
        for(GeoLocation location : list)
        {
            QueryResult loc = idx.findClosest(location.getLat(), location.getLon(), edgeFilter);
            reslist.add(loc);
        }
        return reslist.toArray(new QueryResult[reslist.size()]);
    }
       
    @Test
    public void testMonaco()
    {
        List<String> algos = new ArrayList<String>();
        // CH alogorithms
        algos.add("astarbi");
        algos.add("dijkstrabi");
        for (String algo : algos) 
        {
           assertEquals(testCollector.toString(), 0, testCollector.errors.size());
           runAlgo(testCollector, algo, "files/monaco.osm.gz", "target/graph-monaco",
                createMonacoCar(), "CAR", true, "CAR", "shortest");
           assertEquals(testCollector.toString(), 0, testCollector.errors.size());
        }
        algos.add("astar");
        algos.add("dijkstraNative");
        algos.add("dijkstra");
        // None CH alogorithms
        for (String algo : algos) 
        {
           runAlgo(testCollector, algo, "files/monaco.osm.gz", "target/graph-monaco",
                createMonacoCar(), "CAR", false, "CAR", "shortest");
           assertEquals(testCollector.toString(), 0, testCollector.errors.size());
        }
        testCollector.toString();        
    }
    
    void runAlgo( TestAlgoCollector testCollector, String algo, String osmFile,
            String graphFile, List<GeoLocation> visitlocations, String importVehicles,
            boolean ch, String vehicle, String weightCalcStr )
    {
        try
        {
            Helper.removeDir(new File(graphFile));
            EncodingManager encodingManager = new EncodingManager(importVehicles);
            GraphHopper hopper;
            if (ch)
            {
               hopper = new GraphHopper().setInMemory(true, true).setOSMFile(osmFile).
                    setCHShortcuts(weightCalcStr).
                    setGraphHopperLocation(graphFile).setEncodingManager(encodingManager).
                    importOrLoad();
            }
            else
            {
               hopper = new GraphHopper().setInMemory(true, true).setOSMFile(osmFile).
                    disableCHShortcuts().
                    setGraphHopperLocation(graphFile).setEncodingManager(encodingManager).
                    importOrLoad();
            }
            
            idx = hopper.getLocationIndex();

            FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicle);
            Weighting weighting = new ShortestWeighting();
            if ("fastest".equalsIgnoreCase(weightCalcStr))
                weighting = new FastestWeighting(encoder);
            EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
            
            ViaRouting viaRouter = new ViaRouting(encodingManager,hopper.getGraph(), 20, 10, 20, 20);
            List<Path> pathList;
            pathList = viaRouter.calcPathList( getArray(visitlocations,edgeFilter), algo, vehicle, weightCalcStr, ch );
            
            for (int i=0;i<pathList.size();i++)
            {
              Path path = pathList.get(i);
              if (!path.isFound())
              {
                 testCollector.errors.add("Algorithm: " + algo + ":Path segment #" + i + " not found!");
                 throw new RuntimeException("Path not found!");
              }

              if (i <= pathList.size())
              {                  
                if (Math.abs(path.getDistance() - visitlocations.get(i+1).getSegDistance()) > 5)
                {
                    testCollector.errors.add("Algorithm" + algo + ":Path segment #" + i + " distance expected:" + visitlocations.get(i+1).getSegDistance() + " was " + path.getDistance());
                }
              
                PointList pointList = path.calcPoints();
                path.calcInstructions();
                if (Math.abs(pointList.getSize() - visitlocations.get(i+1).getSegPoints()) > 4)
                {
                   testCollector.errors.add("Algorithm: " + algo + ":Path segment #" + i + " returns path not matching the expected points of " + visitlocations.get(i+1).getSegPoints()
                       + "\t Returned was " + pointList.getSize());
                }

              }
              
            }


        } catch (Exception ex)
        {
            throw new RuntimeException("Algorithm: " + algo + ":cannot handle file " + osmFile, ex);
        } finally
        {
            Helper.removeDir(new File(graphFile));
        }
    }

    static class GeoLocation
    {
        double lat, lon, lastsegdistance;
        int lastsegpoints;

        public GeoLocation( double lat, double lon, double lastsegdistance, int lastsegpoints)
        {
            this.lat = lat;
            this.lon = lon;
            this.lastsegdistance = lastsegdistance;
            this.lastsegpoints = lastsegpoints;
        }
        
        public double getLat()
        {
            return lat;
        }
        
        public double getLon()
        {
            return lon;
        }
        
        public double getSegDistance()
        {
            return lastsegdistance;
        }
        
        public int getSegPoints()
        {
            return lastsegpoints;
        }

        @Override
        public String toString()
        {
            return lat + "," + lon;
        }
    }
  
    
}
