/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.routing.ar;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.LMAlgoFactoryDecorator;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * This class computes viaNodes for all possible pairs of areas which will help finding alternative routes
 *
 * @author Maximilian Sturm
 */
public class PrepareAlternativeRoute extends AbstractAlgoPreparation {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final GraphHopperStorage ghStorage;
    private final Weighting weighting;

    private GraphPartition partition;
    private RoutingAlgorithmFactory algoFactory;

    private double maxWeightFactor = 1.4;
    private double maxShareFactor = 0.6;
    private int maxPaths = 3;
    private int additionalPaths = 3;

    private Random random = new Random(123);
    private int nodesPerArea = 3;

    private IntArrayList viaNodes[][];
    private DataAccess arStorage;
    private ViaNodeSet viaNodeSet;

    public PrepareAlternativeRoute(GraphHopperStorage ghStorage, Weighting weighting,
                                   GraphPartition partition, RoutingAlgorithmFactory algoFactory) {
        this.ghStorage = ghStorage;
        this.weighting = weighting;
        this.partition = partition;
        this.algoFactory = algoFactory;
        arStorage = ghStorage.getDirectory().find("ar_" + AbstractWeighting.weightingToFileName(weighting));
    }

    public PrepareAlternativeRoute setParams(PMap pMap) {
        maxWeightFactor = pMap.getDouble(Parameters.AR.MAX_WEIGHT, maxWeightFactor);
        maxShareFactor = pMap.getDouble(Parameters.AR.MAX_SHARE, maxShareFactor);
        maxPaths = pMap.getInt(Parameters.AR.MAX_PATHS, maxPaths);
        additionalPaths = pMap.getInt(Parameters.AR.ADDITIONAL_PATHS, additionalPaths);
        return this;
    }

    public Weighting getWeighting() {
        return weighting;
    }

    public double getMaxWeightFactor() {
        return maxWeightFactor;
    }

    public double getMaxShareFactor() {
        return maxShareFactor;
    }

    public int getMaxPaths() {
        return maxPaths;
    }

    public int getAdditionalPaths() {
        return additionalPaths;
    }

    /**
     * @param algo the base algorithm
     * @return the alternative route algorithm with the computed ViaNodeSet or the unchanged base algorithm if it's
     * neither AlternativeRoute nor AlternativeRouteCH.
     */
    public RoutingAlgorithm getDecoratedAlgorithm(RoutingAlgorithm algo) {
        if (algo instanceof AlternativeRouteCH) {
            AlternativeRouteCH alt = (AlternativeRouteCH) algo;
            alt.setViaNodeSet(viaNodeSet);
            return alt;
        } else if (algo instanceof AlternativeRoute) {
            AlternativeRoute alt = (AlternativeRoute) algo;
            alt.setViaNodeSet(viaNodeSet);
            return alt;
        }
        return algo;
    }

    /**
     * @return true, if loaded successfully
     */
    public boolean loadExisting() {
        if (isPrepared())
            throw new IllegalStateException("Cannot call GraphPartition.loadExisting if already prepared");
        if (arStorage.loadExisting()) {
            int areas = arStorage.getHeader(0);
            if (areas != partition.getAreas())
                throw new IllegalStateException("The amounts of areas do not match");
            viaNodes = new IntArrayList[areas][areas];
            for (int i = 0; i < areas; i++) {
                for (int j = 0; j < areas; j++) {
                    int location = arStorage.getInt((i * areas + j) * 8 + 0);
                    int size = arStorage.getInt((i * areas + j) * 8 + 4);
                    if (location == Integer.MIN_VALUE || size == 0)
                        continue;
                    IntArrayList currentViaNodes = new IntArrayList(size);
                    for (int k = 0; k < size; k++) {
                        currentViaNodes.add(arStorage.getInt(location + k * 4));
                    }
                    viaNodes[i][j] = currentViaNodes;
                }
            }
            viaNodeSet = createViaNodeSet();
            setPrepared();
            return true;
        }
        return false;
    }

    @Override
    public void doSpecificWork() {
        if (algoFactory instanceof LMAlgoFactoryDecorator.LMRAFactory)
            LOGGER.warn("Preparing advanced alternative routes using the landmark algorithm. This may take a very long" +
                    " time. It's recommended to use CH instead.");
        if (algoFactory instanceof RoutingAlgorithmFactorySimple)
            LOGGER.warn("Preparing advanced alternative routes without using any speed-up algorithm. This may take a" +
                    " very long time. It's recommended to use CH instead.");

        int foundViaNodes = 0;
        int finishedAreaPairs = 0;
        int totalAreaPairs = calcAreaPairs();
        StopWatch sw = new StopWatch().start();
        viaNodes = new IntArrayList[partition.getAreas()][partition.getAreas()];
        for (int i = 0; i < partition.getAreas(); i++) {
            for (int j = 0; j < partition.getAreas(); j++) {
                if (!partition.isDirectlyConnected(i, j)) {
                    viaNodes[i][j] = calcViaNodes(i, j);
                    if (viaNodes[i][j] != null)
                        foundViaNodes += viaNodes[i][j].size();
                    if (++finishedAreaPairs % (int) Math.ceil((double) totalAreaPairs / 5) == 0)
                        logStats(foundViaNodes, finishedAreaPairs, totalAreaPairs, sw.getCurrentSeconds());
                }
            }
        }
        createStorage(foundViaNodes);
        logFinished(foundViaNodes, finishedAreaPairs, totalAreaPairs, sw.stop().getSeconds());
    }

    /**
     * @param area1
     * @param area2
     * @return the list of calculated viaNodes between area1 and area2
     */
    private IntArrayList calcViaNodes(int area1, int area2) {
        IntArrayList nodes1 = partition.getNodes(area1);
        IntArrayList nodes2 = partition.getNodes(area2);

        IntArrayList contactNodes;
        IntArrayList viaNodes;
        if (algoFactory instanceof PrepareContractionHierarchies) {
            contactNodes = searchContactNodesCH(nodes1, nodes2);
            viaNodes = searchViaNodesCH(nodes1, nodes2, contactNodes);
        } else {
            contactNodes = searchContactNodes(nodes1, nodes2);
            viaNodes = searchViaNodes(nodes1, nodes2, contactNodes);
        }

        if (viaNodes.size() == 0)
            return null;
        return viaNodes;
    }

    /**
     * @param nodes1 the list of nodes in area1
     * @param nodes2 the list of nodes in area2
     * @return a list of contact nodes and possible viaNodes between area1 and area2. In order to find many contact
     * nodes, multiple searches are run in this method. This method uses CH
     */
    private IntArrayList searchContactNodesCH(IntArrayList nodes1, IntArrayList nodes2) {
        IntArrayList contactNodes = new IntArrayList();
        for (int i = 0; i < nodesPerArea; i++) {
            int from = nodes1.get(Math.abs(random.nextInt()) % nodes1.size());
            int to = nodes2.get(Math.abs(random.nextInt()) % nodes2.size());
            AlternativeRouteCH alt = (AlternativeRouteCH) algoFactory.createAlgo(
                    ghStorage.getGraph(CHGraph.class),
                    new AlgorithmOptions("alternative_route", weighting));
            alt.setMaxWeightFactor(getMaxWeightFactor());
            alt.setMaxShareFactor(getMaxShareFactor());
            alt.setMaxPaths(getMaxPaths());
            alt.setAdditionalPaths(getAdditionalPaths());
            IntArrayList newContactNodes = alt.calcContactNodes(from, to);
            if (newContactNodes.size() > contactNodes.size())
                contactNodes = newContactNodes;
        }
        return contactNodes;
    }

    /**
     * @param nodes1 the list of nodes in area1
     * @param nodes2 the list of nodes in area2
     * @return a list of contact nodes and possible viaNodes between area1 and area2. In order to find many contact
     * nodes, multiple searches are run in this method
     */
    private IntArrayList searchContactNodes(IntArrayList nodes1, IntArrayList nodes2) {
        IntArrayList contactNodes = new IntArrayList();
        for (int i = 0; i < nodesPerArea; i++) {
            int from = nodes1.get(Math.abs(random.nextInt()) % nodes1.size());
            int to = nodes2.get(Math.abs(random.nextInt()) % nodes2.size());
            AlternativeRoute alt = (AlternativeRoute) algoFactory.createAlgo(
                    ghStorage,
                    new AlgorithmOptions("alternative_route", weighting));
            alt.setMaxWeightFactor(getMaxWeightFactor());
            alt.setMaxShareFactor(getMaxShareFactor());
            alt.setMaxPaths(getMaxPaths());
            alt.setAdditionalPaths(getAdditionalPaths());
            IntArrayList newContactNodes = alt.calcContactNodes(from, to);
            if (newContactNodes.size() > contactNodes.size())
                contactNodes = newContactNodes;
        }
        return contactNodes;
    }

    /**
     * @param nodes1 the list of nodes in area1
     * @param nodes2 the list of nodes in area2
     * @param contactNodes the list of contact nodes between area1 and area2
     * @return a list containing the contact nodes which were used in at least one alternative in the queries in this
     * method. This method uses CH
     */
    private IntArrayList searchViaNodesCH(IntArrayList nodes1, IntArrayList nodes2, IntArrayList contactNodes) {
        IntArrayList viaNodes = new IntArrayList();
        if (contactNodes.size() == 0)
            return viaNodes;
        ArrayList<ViaNodeInfo> infos = new ArrayList<>();

        Loop:
        for (int i = 0; i < nodesPerArea * nodesPerArea; i++) {
            int from = nodes1.get(Math.abs(random.nextInt()) % nodes1.size());
            int to = nodes2.get(Math.abs(random.nextInt()) % nodes2.size());
            AlternativeRouteCH alt = (AlternativeRouteCH) algoFactory.createAlgo(
                    ghStorage.getGraph(CHGraph.class),
                    new AlgorithmOptions("alternative_route", weighting));
            alt.setMaxWeightFactor(getMaxWeightFactor());
            alt.setMaxShareFactor(getMaxShareFactor());
            alt.setMaxPaths(getMaxPaths());
            alt.setAdditionalPaths(getAdditionalPaths());
            alt.setViaNodes(contactNodes);
            alt.calcPaths(from, to);
            IntArrayList newViaNodes = alt.getUsedContactNodes();
            for (int j = 0; j < newViaNodes.size(); j++) {
                int node = newViaNodes.get(j);
                ViaNodeInfo info = new ViaNodeInfo(node);
                int index = infos.indexOf(info);
                if (index < 0) {
                    infos.add(info);
                } else {
                    infos.get(index).addWeight();
                }
            }
        }

        Collections.sort(infos);
        for (ViaNodeInfo info : infos)
            viaNodes.add(info.getNode());
        return viaNodes;
    }

    /**
     * @param nodes1 the list of nodes in area1
     * @param nodes2 the list of nodes in area2
     * @param contactNodes the list of contact nodes between area1 and area2
     * @return a list containing the contact nodes which were used in at least one alternative in the queries in this
     * method
     */
    private IntArrayList searchViaNodes(IntArrayList nodes1, IntArrayList nodes2, IntArrayList contactNodes) {
        IntArrayList viaNodes = new IntArrayList();
        if (contactNodes.size() == 0)
            return viaNodes;
        ArrayList<ViaNodeInfo> infos = new ArrayList<>();

        Loop:
        for (int i = 0; i < nodesPerArea * nodesPerArea; i++) {
            int from = nodes1.get(Math.abs(random.nextInt()) % nodes1.size());
            int to = nodes2.get(Math.abs(random.nextInt()) % nodes2.size());
            AlternativeRoute alt = (AlternativeRoute) algoFactory.createAlgo(
                    ghStorage,
                    new AlgorithmOptions("alternative_route", weighting));
            alt.setMaxWeightFactor(getMaxWeightFactor());
            alt.setMaxShareFactor(getMaxShareFactor());
            alt.setMaxPaths(getMaxPaths());
            alt.setAdditionalPaths(getAdditionalPaths());
            alt.setViaNodes(contactNodes);
            alt.calcPaths(from, to);
            IntArrayList newViaNodes = alt.getUsedContactNodes();
            for (int j = 0; j < newViaNodes.size(); j++) {
                int node = newViaNodes.get(j);
                ViaNodeInfo info = new ViaNodeInfo(node);
                int index = infos.indexOf(info);
                if (index < 0) {
                    infos.add(info);
                } else {
                    infos.get(index).addWeight();
                }
            }
        }

        Collections.sort(infos);
        for (ViaNodeInfo info : infos)
            viaNodes.add(info.getNode());
        return viaNodes;
    }

    /**
     * This method creates and saves the useful data
     */
    private void createStorage(int foundViaNodes) {
        int areas = partition.getAreas();
        int dictionarySize = areas * areas * 8;
        int processedViaNodes = 0;
        arStorage.setHeader(0, areas);
        arStorage.create(dictionarySize + foundViaNodes * 4);
        for (int i = 0; i < areas; i++) {
            for (int j = 0; j < areas; j++) {
                IntArrayList currentViaNodes = viaNodes[i][j];
                if (currentViaNodes == null) {
                    arStorage.setInt((i * areas + j) * 8 + 0, Integer.MIN_VALUE);
                    arStorage.setInt((i * areas + j) * 8 + 4, 0);
                    continue;
                }
                if (currentViaNodes.size() == 0) {
                    arStorage.setInt((i * areas + j) * 8 + 0, Integer.MIN_VALUE);
                    arStorage.setInt((i * areas + j) * 8 + 4, 0);
                    continue;
                }
                int location = dictionarySize + processedViaNodes * 4;
                int size = currentViaNodes.size();
                arStorage.setInt((i * areas + j) * 8 + 0, location);
                arStorage.setInt((i * areas + j) * 8 + 4, size);
                for (int k = 0; k < size; k++) {
                    arStorage.setInt(location + k * 4, currentViaNodes.get(k));
                    processedViaNodes++;
                }
            }
        }
        if (processedViaNodes != foundViaNodes)
            throw new IllegalStateException("This should not happen");
        arStorage.flush();
        viaNodeSet = createViaNodeSet();
    }

    /**
     * @return a ViaNodeSet out of the calculated data
     */
    private ViaNodeSet createViaNodeSet() {
        int area[] = new int[partition.getTotalNodes()];
        for (int i = 0; i < partition.getTotalNodes(); i++)
            area[i] = partition.getArea(i);
        return new ViaNodeSet(area, viaNodes);
    }

    /**
     * @return the amount of area pairs which have to be calculated (just needed for logging purposes)
     */
    private int calcAreaPairs() {
        int areaPairs = 0;
        for (int i = 0; i < partition.getAreas(); i++) {
            for (int j = 0; j < partition.getAreas(); j++) {
                if (!partition.isDirectlyConnected(i, j))
                    areaPairs++;
            }
        }
        return areaPairs;
    }

    private void logStats(int foundViaNodes, int finishedAreaPairs, int totalAreaPairs, float time) {
        LOGGER.info(String.format("prepared area pairs: %4d / %4d, average via nodes: %.3f, time: %.3f s",
                finishedAreaPairs,
                totalAreaPairs,
                (float) foundViaNodes / (float) finishedAreaPairs,
                time));
    }

    private void logFinished(int foundViaNodes, int finishedAreaPairs, int totalAreaPairs, float time) {
        LOGGER.info(String.format("finished preparing advanced alternative routes for %s, average via nodes: %.3f, total time: %.3f s",
                AbstractWeighting.weightingToFileName(weighting),
                (float) foundViaNodes / (float) finishedAreaPairs,
                time));
    }

    /**
     * a class used to compare viaNodes to each other
     */
    private class ViaNodeInfo implements Comparable<ViaNodeInfo>  {
        private final int node;
        private int weight = 1;

        private ViaNodeInfo(int node) {
            this.node = node;
        }

        private int getNode() {
            return node;
        }

        private int getWeight() {
            return weight;
        }

        private void addWeight() {
            weight++;
        }

        @Override
        public int compareTo(ViaNodeInfo o) {
            return Integer.compare(o.getWeight(), getWeight());
        }

        @Override
        public boolean equals(Object obj) {
            ViaNodeInfo info = (ViaNodeInfo) obj;
            return getNode() == info.getNode();
        }

        @Override
        public String toString() {
            return "(" + node + "|" + weight + ")";
        }
    }
}
