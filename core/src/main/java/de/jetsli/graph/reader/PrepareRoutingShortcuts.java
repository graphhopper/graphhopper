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
package de.jetsli.graph.reader;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyTBitSet;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.EdgeSkipIterator;
import de.jetsli.graph.util.GraphUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class PrepareRoutingShortcuts {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final PriorityGraph g;

    public PrepareRoutingShortcuts(PriorityGraph g) {
        this.g = g;
    }

    /**
     * Create short cuts to skip 2 degree nodes and make graph traversal for routing more efficient
     */
    public void doWork() {
        int locs = g.getNodes();
        int newShortcuts = 0;
        for (int startNode = 0; startNode < locs; startNode++) {
            if (has2Edges(g.getEdges(startNode)))
                continue;

            // now search for possible paths to skip
            EdgeSkipIterator iter = g.getEdges(startNode);
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
                    if (!has2Edges(g.getEdges(currentNode)))
                        break;

                    EdgeIterator twoDegreeIter = g.getEdges(currentNode);
                    twoDegreeIter.next();
                    if (twoDegreeIter.node() == skip)
                        twoDegreeIter.next();

                    if (flags != twoDegreeIter.flags())
                        break;
                    if (g.getPriority(currentNode) < 0)
                        break;
                    
                    g.setPriority(currentNode, -1);
                    distance += twoDegreeIter.distance();
                    skip = currentNode;
                    currentNode = twoDegreeIter.node();
                }
                if (currentNode == startNode)
                    continue;

                // found shortcut but check if an edge already exists which is shorter than the shortcut
                EdgeSkipIterator tmpIter = g.getEdges(startNode);
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
        // logger.info("introduced " + newShortcuts + " new shortcuts");
    }

    // a bit more efficient than "count edges == 2"
    static boolean has2Edges(EdgeIterator iter) {
        int counter = 0;
        for (; iter.next(); counter++) {
            if (counter > 2)
                return false;
        }
        if (counter == 2)
            return true;

        return false;
    }
}
