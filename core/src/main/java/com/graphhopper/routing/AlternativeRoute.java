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
package com.graphhopper.routing;

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

/**
 * This class implements the alternative paths search using the "viaNode" method described in the following papers.
 * <p>
 * <ul>
 * <li>Candidate Sets for Alternative Routes in Road Networks - Luxen and Schieferdecker 2012:
 * http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.665.4573&rep=rep1&type=pdf
 * <li>
 * https://algo2.iti.kit.edu/download/ls-csarr-13-poster.pdf
 * </li>
 * </ul>
 *
 * @author Maximilian Sturm
 */
public class AlternativeRoute extends AlternativeRouteAlgorithm {
    private ViaNodeSet viaNodes;
    private ArrayList<ViaPoint> viaPoints;
    private boolean longAlgo;
    private boolean[] contactFound;
    private ArrayList<ContactPoint> contactPoints = new ArrayList<>();

    public AlternativeRoute(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        super(graph, weighting, traversalMode);
        viaNodes = new ViaNodeSet();
        contactFound = new boolean[graph.getNodes()];
    }

    public void setViaNodes(ViaNodeSet viaNodes) {
        this.viaNodes = viaNodes;
    }

    public boolean isLongAlgo() {
        return longAlgo;
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entryCurrent, int traversalId) {
        if (entryCurrent.parent != null) {
            if (isLongAlgo()) {
                int node = entryCurrent.adjNode;
                for (ViaPoint point : viaPoints) {
                    if (node == point.getNode()) {
                        if (bestWeightMapTo == bestWeightMapOther) {
                            point.addEntryFrom(entryCurrent);
                        } else {
                            point.addEntryTo(entryCurrent);
                        }
                        break;
                    }
                }
            } else {
                if (contactFound[entryCurrent.parent.adjNode]) {
                    contactFound[entryCurrent.adjNode] = true;
                } else {
                    Iterator<IntObjectCursor<SPTEntry>> iterator = bestWeightMapOther.iterator();
                    while (iterator.hasNext()) {
                        SPTEntry entryOther = iterator.next().value;
                        int node = entryOther.adjNode;
                        if (entryCurrent.adjNode == node) {
                            contactFound[node] = true;
                            if (bestWeightMapTo == bestWeightMapOther)
                                contactPoints.add(new ContactPoint(entryCurrent, entryOther));
                            else
                                contactPoints.add(new ContactPoint(entryOther, entryCurrent));
                        }
                    }
                }
            }
        }
        super.updateBestPath(edgeState, entryCurrent, traversalId);
    }

    @Override
    public Path calcPath(int from, int to) {
        return calcPaths(from, to).get(0);
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        checkAlreadyRun();
        ArrayList<Integer> list = viaNodes.get(from, to);
        if (list != null) {
            viaPoints = new ArrayList<>();
            longAlgo = true;
            for (int node : list) {
                viaPoints.add(new ViaPoint(node));
            }
        }
        createAndInitPath();
        initFrom(from, 0);
        initTo(to, 0);
        runAlgo();
        return calcAlternatives(extractPath());
    }

    private List<Path> calcAlternatives(Path mainRoute) {
        ArrayList<AlternativeInfo> alternatives = new ArrayList<>();
        alternatives.add(new AlternativeInfo(mainRoute, 0, -1));
        if (isLongAlgo())
            for (ViaPoint point : viaPoints)
                contactPoints.addAll(point.createContactPoints());
        for (ContactPoint contactPoint : contactPoints) {
            Path altRoute = createPath(contactPoint.getEntryFrom(), contactPoint.getEntryTo());
            double sortBy = calcSortBy(mainRoute, altRoute);
            if (sortBy < Double.MAX_VALUE)
                alternatives.add(new AlternativeInfo(altRoute, sortBy, contactPoint.getNode()));
            if (alternatives.size() == maxPaths + additionalPaths)
                break;
        }
        Collections.sort(alternatives, ALT_COMPARATOR);
        ArrayList<Path> paths = new ArrayList<>();
        Iterator<AlternativeInfo> alternativeIterator = alternatives.iterator();
        paths.add(alternativeIterator.next().getPath());
        Loop:
        while (alternativeIterator.hasNext()) {
            Path path = alternativeIterator.next().getPath();
            Iterator<Path> pathIterator = paths.iterator();
            pathIterator.next();
            while (pathIterator.hasNext())
                if (calcSortBy(path, pathIterator.next()) == Double.MAX_VALUE)
                    continue Loop;
            paths.add(path);
            if (paths.size() == maxPaths)
                break;
        }
        return paths;
    }
}
