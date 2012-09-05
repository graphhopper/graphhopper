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
package de.jetsli.graph.util;

import de.jetsli.graph.storage.Edge;
import de.jetsli.graph.storage.Graph;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is introduced as a helper to avoid cluttering the Graph interface with all the common
 * methods. Most of the methods are useful for unit tests.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class GraphUtility {

    private static Logger logger = LoggerFactory.getLogger(GraphUtility.class);

    /**
     * @throws could throw exception if uncatched problems like index out of bounds etc
     */
    public static List<String> getProblems(Graph g) {
        List<String> problems = new ArrayList<String>();
        int nodes = g.getNodes();
        for (int i = 0; i < nodes; i++) {
            double lat = g.getLatitude(i);
            if (lat > 90 || lat < -90)
                problems.add("latitude is not within its bounds " + lat);
            double lon = g.getLongitude(i);
            if (lon > 180 || lon < -180)
                problems.add("longitude is not within its bounds " + lon);
            int incom = count(g.getIncoming(i));
            int out = count(g.getOutgoing(i));
            int e = count(g.getEdges(i));
            if (Math.max(out, incom) > e)
                problems.add("count incoming or outgoing edges should be maximum "
                        + e + " but were:" + incom + "(in), " + out + "(out)");

            EdgeIterator iter = g.getEdges(i);
            while (iter.next()) {
                if (iter.node() >= nodes)
                    problems.add("edge of " + i + " has a node " + iter.node() + " greater or equal to getNodes");
                if (iter.node() < 0)
                    problems.add("edge of " + i + " has a negative node " + iter.node());
            }
        }

//        for (int i = 0; i < nodes; i++) {
//            new XFirstSearch().start(g, i, false);
//        }

        return problems;
    }

    /**
     * note/todo: this methods counts edges twice if both directions are available
     */
    public static int countEdges(Graph g) {
        int counter = 0;
        int nodes = g.getNodes();
        for (int i = 0; i < nodes; i++) {
            EdgeIterator iter = g.getOutgoing(i);
            while (iter.next()) {
                counter++;
            }
        }
        return counter;
    }

    public static int count(EdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            ++counter;
        }
        return counter;
    }

    public static int count(Iterable<?> iter) {
        int counter = 0;
        for (Object o : iter) {
            ++counter;
        }
        return counter;
    }

    public static boolean contains(EdgeIterator iter, int... locs) {
        TIntHashSet set = new TIntHashSet();

        while (iter.next()) {
            set.add(iter.node());
        }
        for (int l : locs) {
            if (!set.contains(l))
                return false;
        }
        return true;
    }

    public static boolean contains(Iterable<? extends Edge> iter, int... locs) {
        Iterator<? extends Edge> i = iter.iterator();
        TIntHashSet set = new TIntHashSet();
        while (i.hasNext()) {
            set.add(i.next().node);
        }
        for (int l : locs) {
            if (!set.contains(l))
                return false;
        }
        return true;
    }

    public static EdgeIterator until(EdgeIterator edges, int node, int flags) {
        while (edges.next()) {
            if (edges.node() == node && edges.flags() == flags)
                return edges;
        }
        return EdgeIterator.EMPTY;
    }

    public static EdgeIterator until(EdgeIterator edges, int node) {
        while (edges.next()) {
            if (edges.node() == node)
                return edges;
        }
        return EdgeIterator.EMPTY;
    }

    /**
     * Added this helper method to avoid cluttering the graph interface. Good idea?
     */
    public static EdgeIterator getEdges(Graph graph, int index, boolean out) {
        if (out)
            return graph.getOutgoing(index);
        else
            return graph.getIncoming(index);
    }

    public static void printInfo(final Graph g, int startNode, final int counts) {
        new XFirstSearch() {
            int counter = 0;

            @Override protected boolean goFurther(int nodeId) {
                System.out.println(getNodeInfo(g, nodeId));
                if (counter++ > counts)
                    return false;
                return true;
            }
        }.start(g, startNode, false);
    }

    public static String getNodeInfo(Graph g, int nodeId) {
        EdgeIterator iter = g.getOutgoing(nodeId);
        String str = nodeId + ":" + g.getLatitude(nodeId) + "," + g.getLongitude(nodeId) + "\n";
        while (iter.next()) {
            str += "  ->" + iter.node() + "\t" + BitUtil.toBitString(iter.flags(), 2) + "\n";
        }
        return str;
    }
}
