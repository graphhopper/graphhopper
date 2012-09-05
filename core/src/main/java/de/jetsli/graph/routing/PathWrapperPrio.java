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
package de.jetsli.graph.routing;

import de.jetsli.graph.reader.EdgeFlags;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.EdgeSkipIterator;
import de.jetsli.graph.util.GraphUtility;
import gnu.trove.list.array.TIntArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class creates a DijkstraPath from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathWrapperPrio extends PathWrapperRef {

    private Logger logger = LoggerFactory.getLogger(getClass());

    public PathWrapperPrio(PriorityGraph g) {
        super(g);
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract() {
        if (edgeFrom == null || edgeTo == null)
            return null;

        if (edgeFrom.node != edgeTo.node)
            throw new IllegalStateException("Locations of the 'to'- and 'from'-Edge has to be the same." + toString());

        if (switchWrapper) {
            EdgeEntry ee = edgeFrom;
            edgeFrom = edgeTo;
            edgeTo = ee;
        }

        Path path = new Path();
        EdgeEntry currEdge = edgeFrom;
        while (currEdge.prevEntry != null) {
            int tmpFrom = currEdge.node;
            path.add(tmpFrom);
            currEdge = currEdge.prevEntry;
            EdgeSkipIterator iter = (EdgeSkipIterator) g.getOutgoing(currEdge.node);
            path.updateProperties(iter, tmpFrom);
            if (iter.skippedNode() >= 0) {
                // logger.info("iter(" + tmpFrom + "->" + currEdge.node + ") with skipped node:" + iter.skippedNode());
                expand(path, currEdge.node, tmpFrom, iter.skippedNode(), iter.flags());
            }
        }
        path.add(currEdge.node);
        path.reverseOrder();
        currEdge = edgeTo;
        while (currEdge.prevEntry != null) {
            int tmpTo = currEdge.node;
            currEdge = currEdge.prevEntry;
            path.add(currEdge.node);
            EdgeSkipIterator iter = (EdgeSkipIterator) g.getOutgoing(tmpTo);
            path.updateProperties(iter, currEdge.node);
            if (iter.skippedNode() >= 0) {
                // logger.info("iter(" + currEdge.node + "->" + tmpTo + ") with skipped node:" + iter.skippedNode());
                expand(path, tmpTo, currEdge.node, iter.skippedNode(), iter.flags());
            }
        }

        return path;
    }

    private void expand(Path path, int from, int to, int skippedNode, int flags) {
        boolean reverse = false;
        int skip = from;
        EdgeIterator tmpIter = GraphUtility.until(g.getOutgoing(from), skippedNode, flags);
        if (tmpIter == EdgeIterator.EMPTY) {
            skip = to;
            int tmp = from;
            from = to;
            to = tmp;
            // search skipped node at the other end of the shortcut!
            reverse = true;
            tmpIter = GraphUtility.until(g.getOutgoing(from), skippedNode, EdgeFlags.swapDirection(flags));
            if (tmpIter == EdgeIterator.EMPTY)
                throw new IllegalStateException("skipped node " + skippedNode + " not found for " + from + "->" + to + "?");
        }

        TIntArrayList tmp = new TIntArrayList();
        while (true) {
            int node = tmpIter.node();
            tmp.add(node);
            tmpIter = g.getEdges(node);
            tmpIter.next();
            if (tmpIter.node() == skip)
                tmpIter.next();

            skip = node;
            if (((PriorityGraph) g).getPriority(tmpIter.node()) >= 0 || tmpIter.node() == to)
                break;
        }

        if (reverse)
            tmp.reverse();

        for (int i = 0; i < tmp.size(); i++) {
            path.add(tmp.get(i));
        }
    }
}
