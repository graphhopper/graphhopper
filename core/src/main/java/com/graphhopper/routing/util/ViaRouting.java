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

package com.graphhopper.routing.util;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ratrun
 */
public class ViaRouting
{
    // for CH prepare
    private AlgorithmPreparation prepare;
    private EncodingManager encodingManager;
    int periodicUpdates;
    int lazyUpdates;
    int neighborUpdates;
    double logMessages;
    
    // for graph:
    private Graph graph;
    
    protected Weighting createWeighting( String weighting, FlagEncoder encoder )
    {
        // ignore case
        weighting = weighting.toLowerCase();
        if ("shortest".equals(weighting))
            return new ShortestWeighting();
        return new FastestWeighting(encoder);
    }
     
    private void initCHPrepare(String chWeighting)
    {
        FlagEncoder encoder = encodingManager.getSingle();
        PrepareContractionHierarchies tmpPrepareCH = new PrepareContractionHierarchies(encoder,
                createWeighting(chWeighting, encoder));

        tmpPrepareCH.setPeriodicUpdates(periodicUpdates).
                setLazyUpdates(lazyUpdates).
                setNeighborUpdates(neighborUpdates).
                setLogMessages(logMessages);

        prepare = tmpPrepareCH;
        prepare.setGraph(graph);
    }
    
    public ViaRouting(EncodingManager encodingManager, Graph graph, int periodicUpdates, int lazyUpdates, int neighborUpdates, double logMessages)
    {
        this.encodingManager=encodingManager;
        this.graph=graph;
        this.periodicUpdates=periodicUpdates;
        this.lazyUpdates=lazyUpdates;
        this.neighborUpdates=neighborUpdates;
        this.logMessages=logMessages;
    }

    public List<Path> calcPathList( QueryResult[] from_via_to_list, String algoname, String vehicle, String weighthing, boolean ch )
    {
        StopWatch sw = new StopWatch().start();
        String debug = "idLookup:" + sw.stop().getSeconds() + "s";
        RoutingAlgorithm algo = null;
        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        
        debug += ", algoInit:" + sw.stop().getSeconds() + "s";
        sw = new StopWatch().start();
        
        List<Path> resultpathlist = new ArrayList<Path>();
        if (from_via_to_list.length < 2)
                return null;

        QueryResult from;
        QueryResult to;
          // Walk through the via list
        for (int i=0; i<from_via_to_list.length-1; i++)
        {
           from = from_via_to_list[i];
           to = from_via_to_list[i+1];
           
           if (ch)
           {
               initCHPrepare(weighthing);
               
               if (prepare == null)
                   throw new IllegalStateException(
                           "Preparation object is null. CH-preparation wasn't done or did you forgot to call disableCHShortcuts()?");

               if (algoname.equals("dijkstrabi"))
                  algo = prepare.createAlgo();
               else if (algoname.equals("astarbi"))
                   algo = ((PrepareContractionHierarchies) prepare).createAStar();
                else
                   throw new IllegalStateException(
                           "Only dijkstrabi and astarbi is supported for LevelGraph (using contraction hierarchies)!");

           } else
           {
               Weighting weighting = createWeighting(weighthing, encoder);
               prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, algoname, encoder, weighting);
               algo = prepare.createAlgo();
           }
           Path path=algo.calcPath(from, to);
           if (path.isFound())
              resultpathlist.add(path);
        }
        
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s, ";
        return resultpathlist;
      }
    
    public double getPathDistance(List<Path> pathList)
    {
        double distance=0;
        for (Path path: pathList)
        {
           distance+=path.getDistance();
        }
        return distance;
    }
    
    public long getPathMillis(List<Path> pathList)
    {
        long millis=0;
        for (Path path: pathList)
        {
           millis+=path.getMillis();
        }
        return millis;
    }
    
    public PointList getPoints(List<Path> pathList)
    {
        PointList res = new PointList();
        for (Path path : pathList)
        {
          PointList segment = path.calcPoints();
          for (int i=0; i<segment.size();i++)
          {
              res.add(segment.getLatitude(i), segment.getLongitude(i));
          }
        }
        return res;
    }
    
    public InstructionList calcInstructions(List<Path> pathList)
    {
        InstructionList res = new InstructionList();
        for (Path path : pathList)
        {
            InstructionList segment=path.calcInstructions();
            for (int i=0; i<segment.size();i++)
            {
                res.add(segment.get(i));
            }
        }
        return res;
    }
}
