/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.routing.util;

import de.jetsli.graph.routing.DijkstraSimple;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.EdgeSkipIterator;
import de.jetsli.graph.util.GraphUtility;
import de.jetsli.graph.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class PrepareRoutingShortcuts {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final PriorityGraph g;
    private int newShortcuts;

    public PrepareRoutingShortcuts(PriorityGraph g) {
        this.g = g;
    }

    public int getShortcuts() {
        return newShortcuts;
    }

    /**
     * Create short cuts to skip 2 degree nodes and make graph traversal for routing more efficient
     */
    public void doWork() {
        newShortcuts = 0;
        int locs = g.getNodes();
        StopWatch sw = new StopWatch().start();
//        System.out.println("309722, 309730:" + new DijkstraSimple(g).calcPath(309722, 309730).distance());
//        System.out.println(GraphUtility.until(g.getOutgoing(309721), 309722).distance() + "," + GraphUtility.until(g.getOutgoing(309730), 309742).distance());
//        System.out.println();
//        System.out.println("314596, 314598:" + new DijkstraSimple(g).calcPath(314596, 314598).distance());
//        System.out.println(GraphUtility.until(g.getOutgoing(309721), 314596).distance() + "," + GraphUtility.until(g.getOutgoing(314598), 309742).distance());
//        GraphUtility.printInfo(g, 309721, 100);
//        for (int startNode = 300000; startNode < locs; startNode++) {
        for (int startNode = 0; startNode < locs; startNode++) {
            if (has1InAnd1Out(startNode))
                continue;

            // now search for possible paths to skip
            EdgeSkipIterator iter = g.getOutgoing(startNode);
            MAIN:
            while (iter.next()) {
                // while iterating new shortcuts could have been introduced. ignore them!
                if (iter.skippedNode() >= 0)
                    continue;

                int firstSkippedNode = iter.node();
                int flags = iter.flags();
                int skip = startNode;
                int currentNode = iter.node();
                double distance = iter.distance();
                while (true) {
                    if (g.getPriority(currentNode) < 0)
                        continue MAIN;
                    
                    if (!has1InAnd1Out(currentNode))
                        break;

                    EdgeIterator twoDegreeIter = g.getEdges(currentNode);
                    twoDegreeIter.next();
                    if (twoDegreeIter.node() == skip) {
                        if (!twoDegreeIter.next())
                            throw new IllegalStateException("there should be an opposite node to "
                                    + "traverse to but wasn't for " + currentNode);
                    }

                    if (twoDegreeIter.node() == skip)
                        throw new IllegalStateException("next node shouldn't be identical to skip "
                                + "(" + skip + ") for " + currentNode + ", startNode=" + startNode);

                    if (flags != twoDegreeIter.flags())
                        break;

                    g.setPriority(currentNode, -1);
                    distance += twoDegreeIter.distance();
                    skip = currentNode;
                    currentNode = twoDegreeIter.node();
                }
                if (currentNode == startNode)
                    continue;

                // found shortcut but check if an edge already exists which is shorter than the shortcut
                EdgeSkipIterator tmpIter = g.getOutgoing(startNode);
                EdgeIterator tmpIter2 = GraphUtility.until(tmpIter, currentNode, flags);
                if (tmpIter2 != EdgeIterator.EMPTY) {
                    tmpIter = (EdgeSkipIterator) tmpIter2;
                    if (tmpIter.distance() > distance) {
                        // update the distance
                        tmpIter.distance(distance);
                        tmpIter.skippedNode(firstSkippedNode);
                    }
                } else {
                    // finally create the shortcut
                    g.shortcut(startNode, currentNode, distance, flags, firstSkippedNode);
                    newShortcuts++;
                }
            }
        }
        logger.info("introduced " + newShortcuts + " new shortcuts in: " + sw.stop().getSeconds() + "s");
    }

    /**
     * Make sure there is exactly one outgoing and exactly one incoming edge. The current
     * implementation uses graph.getEdges() as both edges can be bi-directional and they need to
     * have different end nodes.
     */
    boolean has1InAnd1Out(int index) {
        EdgeIterator iter = g.getEdges(index);
        int counter = 0;
        int node = -1;
        boolean firstForward = false;
        for (; iter.next(); counter++) {
            if (counter == 0) {
                firstForward = EdgeFlags.isForward(iter.flags());
                node = iter.node();
            } else if (counter == 1) {
                if (node == iter.node())
                    break;
                if (firstForward && !EdgeFlags.isBackward(iter.flags()))
                    return false;
                else if (!firstForward && !EdgeFlags.isForward(iter.flags()))
                    return false;
            }
            if (counter > 2)
                return false;
        }
        return counter == 2;
    }
}
