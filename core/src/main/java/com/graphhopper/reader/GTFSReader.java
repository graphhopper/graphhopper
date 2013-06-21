/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.PublicTransitEdgeFilter;
import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.index.Id2NameIndex;
import com.graphhopper.storage.index.LocationTime2IDIndex;
import com.graphhopper.storage.index.LocationTime2NodeNtree;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.TimeUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.Frequency;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Transfer;
import org.onebusaway.gtfs.model.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onebusaway.gtfs.serialization.GtfsReader;

/**
 * This class generates time-expanded graph from a GTFS files.
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class GTFSReader {

    private static Logger logger = LoggerFactory.getLogger(GTFSReader.class);
    private GraphStorage graph;

    private LocationTime2IDIndex index;
    private Id2NameIndex nameIndex;
    
    // Default time it takes to get off in sec
    private int defaultAlightTime = 240;
    
    
    private int travelFlags;
    private static int nodeId = 0;

    private Map<Stop, TransitStop> stopNodes;

    public GTFSReader(GraphStorage graph) {
        this.stopNodes = new HashMap<Stop, TransitStop>();
        this.graph = graph;
        this.nameIndex = new Id2NameIndex();
        this.travelFlags = new PublicTransitFlagEncoder().flags(false);

    }

    /**
     * 
     * @param gtfsFile
     * @throws IOException
     */
    public void load(File gtfsFile) throws IOException {
        //graph.create(graphSize);
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(gtfsFile);

        GtfsRelationalDaoImpl store = new GtfsRelationalDaoImpl();
        reader.setEntityStore(store);

        reader.run();
        long expectedNodes = calcExpectedNodes(store);
        prepare(expectedNodes);
        loadStops(store);
        loadTrips(store);
        loadTransfers(store);
    }

    /**
     * Loads the stations/stops from entity store and adds them to the graph
     *
     * @param store
     */
    private void loadStops(GtfsRelationalDaoImpl store) {
        logger.info("Importing stations ...");
        for (Stop stop : store.getAllStops()) {
            TransitStop transitStop = new TransitStop(graph, stop,defaultAlightTime);
            stopNodes.put(stop, transitStop);

            for (StopTime stopTime : store.getStopTimesForStop(stop)) {
                transitStop.addTransitNode(stopTime);
            }
            transitStop.buildTransitNodes();
        }
        logger.info("Added " + stopNodes.size() +" stations.");
    }

    /**
     * Generates transfer pattern for each trip and adds it to the graph.
     * Trips which uses frequencies.txt are not yet supported
     *
     * @param store
     */
    private void loadTrips(GtfsRelationalDaoImpl store) {

        Collection<Trip> trips = store.getAllTrips();

        /* first, record which trips are used by one or more frequency entries */
        HashMap<Trip, List<Frequency>> tripFrequencies = new HashMap<Trip, List<Frequency>>();
        for (Frequency freq : store.getAllFrequencies()) {
            List<Frequency> freqs = tripFrequencies.get(freq.getTrip());
            if (freqs == null) {
                freqs = new ArrayList<Frequency>();
                tripFrequencies.put(freq.getTrip(), freqs);
            }
            freqs.add(freq);
        }

        for (Trip trip : trips) {

            /* check to see if this trip is used by one or more frequency entries */
            List<Frequency> frequencies = tripFrequencies.get(trip);
            if (frequencies != null) {
                // Not yet Supported
            } else {
                int departureNode = -1;
                int departureTime = 0;
                Iterator<StopTime> iterator = store.getStopTimesForTrip(trip).iterator();
                if (iterator.hasNext()) {
                    // First stop in a trip
                    StopTime stopTime = iterator.next();
                    Stop stop = stopTime.getStop();
                    TransitStop transitStop = stopNodes.get(stop);
                    departureNode = transitStop.addDepartureNode(stopTime);
                    departureTime = stopTime.getDepartureTime();
                }
                while (iterator.hasNext()) {
                    StopTime stopTime = iterator.next();
                    Stop stop = stopTime.getStop();
                    TransitStop transitStop = stopNodes.get(stop);
                    
                    // Add arrivale node and connect to previous departure node
                    int arrivalNode = transitStop.addArrivalNode(stopTime);
                    int travelTime = stopTime.getArrivalTime() - departureTime;
                    checkTime(travelTime);
                    graph.edge(departureNode, arrivalNode, travelTime, travelFlags);

                    // Check if it is the last stop in the trip
                    if (iterator.hasNext()) {
                        departureNode = transitStop.addDepartureNode(stopTime);
                        departureTime = stopTime.getDepartureTime();
                        int waitingTime = departureTime - stopTime.getArrivalTime();
                        checkTime(waitingTime);
                        graph.edge(arrivalNode, departureNode, waitingTime, travelFlags);
                    }
                }
            }

        }

    }
    
    private void loadTransfers(GtfsRelationalDaoImpl store) {
        logger.info("Importing transfers ...");
        for (Transfer transfer : store.getAllTransfers()){
            TransitStop from = stopNodes.get(transfer.getFromStop());
            TransitStop to = stopNodes.get(transfer.getToStop());
            if (from != null && to != null) {
                int time = transfer.getMinTransferTime();
                from.addTransfer(to, time);
            }
        }
    }

    /**
     * Maps location and time to node id
     * @return index
     */
    public LocationTime2IDIndex getIndex(Directory dir) {
        if (index == null) {
            index = new LocationTime2NodeNtree(graph, dir);
            index.prepareIndex();
        }
        return index;
    }
    
    /**
     * Maps nodes id to the name of the station/stops
     * @return 
     */
    public Id2NameIndex getNameIndex() {
        if (nameIndex.isEmpty()){
            generateNameIndex();
        }
        return nameIndex;
    }
    
    /**
     * Loads the names-id pair into the index
     */
    private void generateNameIndex() {
        for (TransitStop stop : stopNodes.values()) {
            String stopName = stop.getStopName();
            for (int id: stop.getNodesList()){
                nameIndex.addName(id, stopName);
            }
        }
    }

    public GraphStorage graph() {
        return graph;
    }
    
    /**
     * Gets a new unique node id
     * @return unique id
     */
    public synchronized static int getNewNodeId() {
        return nodeId++;
    }
    
    /**
     * Sets the default value how it takes to alight
     * @param defaultAlightTime time in sec
     */
    public void setDefaultAlightTime(int defaultAlightTime) {
        this.defaultAlightTime = defaultAlightTime;
    }
    
    /**
     * Must be called after finish reading the file
     */
    public synchronized void close(){
        nodeId = 0;
    }
    
    /**
     * Just for debugging path which generated which uses graph generated with
     * GTFSReader
     * 
     * Patterns used:
     * Transit      ---
     * Traveling     ===
     * Boarding     __/
     * Alight       \__
     * Finish       --|
     * Error        < E >
     * @param path 
     */
    public void debugPath(Path path, final int startTime) {
        
        
        // Make sure name index is generated
        getNameIndex();
        
        Path.EdgeVisitor visitor = new Path.EdgeVisitor() {
            private final PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
            private boolean onTrain = false;
            private double time = startTime;

            @Override
            public void next(EdgeIterator iter) {
                int flags = iter.flags();
                String output = new String();
                output += "(" + iter.adjNode() + ")";
                time += iter.distance();
                if (!onTrain && encoder.isTransit(flags)) {
                    // Transit edge
                    output += " --- ";
                } else if (!onTrain && encoder.isBoarding(flags)) {
                    // Boarding edge
                    onTrain = true;
                    output += " __/ ";
                } else if (onTrain && encoder.isAlight(flags)) {
                    // Alight edge
                    onTrain = false;
                    output += " \\__ ";
                } else if (!onTrain && encoder.isExit(flags)) {
                        // Get off
                        output += " --| ";
                } else if (onTrain && encoder.isBackward(flags)) {
                        // Travling
                        output += " === ";
                } else {
                    // Wrong edge
                    output += " < E > ";
                }

                output +=  nameIndex.findName(iter.baseNode()) + " (" + iter.baseNode() + ")";
                output += " " + TimeUtils.formatTime((int) time);
                System.out.println(output);
            }
        };
        path.forEveryEdge(visitor);
    }

    private void checkTime(int travelTime) {
        if (travelTime < 0) {
            throw new RuntimeException("Negative travel time!");
        }
    }

    void debugPath(Path path) {
        debugPath(path,0);
    }

    private void prepare(long expectedNodes) {
        graph.create(expectedNodes);
    }

    private long calcExpectedNodes(GtfsRelationalDaoImpl store) {
        long stops = store.getAllStops().size();
        long stopTimes = store.getAllStopTimes().size();
        long trips = store.getAllTrips().size();
        
        return 2 * (stops + stopTimes + trips);
    }
}
