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
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.index.LocationTime2IDIndex;
import com.graphhopper.util.EdgeIterator;
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
    private int defaultFlags;
    
    private static int nodeId = 0;

    private Map<Stop, TransitStop> stopNodes;

    public GTFSReader(GraphStorage graph) {
        this.stopNodes = new HashMap<Stop, TransitStop>();
        this.graph = graph;
        this.index = new LocationTime2IDIndex(graph);
        this.defaultFlags = new PublicTransitFlagEncoder().flags(false);

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
        loadStops(store);
        loadTrips(store);
    }

    /**
     * Loads the stations/stops from entity store and adds them to the graph
     *
     * @param store
     */
    private void loadStops(GtfsRelationalDaoImpl store) {
        logger.info("Importing stations ...");
        for (Stop stop : store.getAllStops()) {
            TransitStop transitStop = new TransitStop(graph, stop);
            stopNodes.put(stop, transitStop);

            for (StopTime stopTime : store.getStopTimesForStop(stop)) {
                transitStop.addTransitNode(stopTime);
            }
            transitStop.buildTransitNodes();
            index.addStation(transitStop.getStopId(), transitStop.getExitNodeId(), stop.getLat(), stop.getLon());
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
                    graph.edge(departureNode, arrivalNode, travelTime, defaultFlags);

                    // Check if it is the last stop in the trip
                    if (iterator.hasNext()) {
                        departureNode = transitStop.addDepartureNode(stopTime);
                        departureTime = stopTime.getDepartureTime();
                        int waitingTime = departureTime - stopTime.getArrivalTime();
                        checkTime(waitingTime);
                        graph.edge(arrivalNode, departureNode, waitingTime, defaultFlags);
                    }
                }
            }

        }

    }

    /**
     * Maps location and time to node id
     * @return index
     */
    public LocationTime2IDIndex getIndex() {
        return index;
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
    public void debugPath(Path path) {
        Path.EdgeVisitor visitor = new Path.EdgeVisitor() {
            private PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
            private EdgeFilter transitFilter = new PublicTransitEdgeFilter(encoder, true, false, true, false, false);
            private EdgeFilter boardingFilter = new PublicTransitEdgeFilter(encoder, true, false, false, true, false);
            private EdgeFilter alightFilter = new PublicTransitEdgeFilter(encoder, true, false, false, false, true);
            private EdgeFilter defaultFilter = new PublicTransitEdgeFilter(encoder, true, false, false, false, false);
            private boolean onTrain = false;
            private double time = 0;

            @Override
            public void next(EdgeIterator iter) {
                String output = new String();
                output += "(" + iter.adjNode() + ")";
                time += iter.distance();
                if (!onTrain && transitFilter.accept(iter)) {
                    // Transit edge
                    output += " --- ";
                } else if (!onTrain && boardingFilter.accept(iter)) {
                    // Boarding edge
                    onTrain = true;
                    output += " __/ ";
                } else if (onTrain && alightFilter.accept(iter)) {
                    // Alight edge
                    onTrain = false;
                    output += " \\__ ";
                } else if (defaultFilter.accept(iter)) {
                    // travling edge or get off
                    if (!onTrain) {
                        // Get off
                        output += " --| ";
                    } else {
                        // Travling
                        output += " === ";
                    }
                } else {
                    // Wrong edge
                    output += " < E > ";
                }

                output += "(" + iter.baseNode() + ")";
                output += " " + time + "s";
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
}
