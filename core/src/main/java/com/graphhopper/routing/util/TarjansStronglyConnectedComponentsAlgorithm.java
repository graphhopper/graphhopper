package com.graphhopper.routing.util;

import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.stack.array.TIntArrayStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Implementation of Tarjan's algorithm using an explicit stack. (The traditional recursive approach
 * runs into stack overflow pretty quickly.)
 * <p/>
 * Used for finding strongly connected components to detect dead-ends.
 * <p/>
 * http://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm
 */
public class TarjansStronglyConnectedComponentsAlgorithm
{
    private final GraphHopperStorage graph;
    private final TIntArrayStack nodeStack;
    private final GHBitSetImpl onStack;
    private final int[] nodeIndex;
    private final int[] nodeLowLink;
    private final ArrayList<TIntArrayList> components = new ArrayList<TIntArrayList>();

    private int index = 1;
    private final EdgeFilter edgeFilter;

    public TarjansStronglyConnectedComponentsAlgorithm( GraphHopperStorage graph, final EdgeFilter edgeFilter )
    {
        this.graph = graph;
        this.nodeStack = new TIntArrayStack();
        this.onStack = new GHBitSetImpl(graph.getNodes());
        this.nodeIndex = new int[graph.getNodes()];
        this.nodeLowLink = new int[graph.getNodes()];
        this.edgeFilter = edgeFilter;
    }

    /**
     * Find and return list of all strongly connected components in g.
     */
    public List<TIntArrayList> findComponents()
    {

        int nodes = graph.getNodes();
        for (int start = 0; start < nodes; start++)
        {
            if (nodeIndex[start] == 0 && !graph.isNodeRemoved(start))
            {
                strongConnect(start);
            }
        }

        return components;
    }

    // Find all components reachable from firstNode, add them to 'components'
    private void strongConnect( int firstNode )
    {
        final Stack<TarjanState> stateStack = new Stack<TarjanState>();
        stateStack.push(TarjanState.startState(firstNode));

        // nextState label is equivalent to the function entry point in the recursive Tarjan's algorithm.
        nextState:

        while (!stateStack.empty())
        {
            TarjanState state = stateStack.pop();
            final int start = state.start;
            final EdgeIterator iter;

            if (state.isStart())
            {
                // We're traversing a new node 'start'.  Set the depth index for this node to the smallest unused index.
                nodeIndex[start] = index;
                nodeLowLink[start] = index;
                index++;
                nodeStack.push(start);
                onStack.set(start);

                iter = graph.createEdgeExplorer(edgeFilter).setBaseNode(start);

            } else
            { // if (state.isResume()) {

                // We're resuming iteration over the next child of 'start', set lowLink as appropriate.
                iter = state.iter;

                int prevConnectedId = iter.getAdjNode();
                nodeLowLink[start] = Math.min(nodeLowLink[start], nodeLowLink[prevConnectedId]);
            }

            // Each element (excluding the first) in the current component should be able to find
            // a successor with a lower nodeLowLink.
            while (iter.next())
            {
                int connectedId = iter.getAdjNode();
                if (nodeIndex[connectedId] == 0)
                {
                    // Push resume and start states onto state stack to continue our DFS through the graph after the jump.
                    // Ideally we'd just call strongConnectIterative(connectedId);
                    stateStack.push(TarjanState.resumeState(start, iter));
                    stateStack.push(TarjanState.startState(connectedId));
                    continue nextState;
                } else if (onStack.contains(connectedId))
                {
                    nodeLowLink[start] = Math.min(nodeLowLink[start], nodeIndex[connectedId]);
                }
            }

            // If nodeLowLink == nodeIndex, then we are the first element in a component.
            // Add all nodes higher up on nodeStack to this component.
            if (nodeIndex[start] == nodeLowLink[start])
            {
                TIntArrayList component = new TIntArrayList();
                int node;
                while ((node = nodeStack.pop()) != start)
                {
                    component.add(node);
                    onStack.clear(node);
                }
                component.add(start);
                onStack.clear(start);

                components.add(component);
            }
        }
    }

    // Internal stack state of algorithm, used to avoid recursive function calls and hitting stack overflow exceptions.
    // State is either 'start' for new nodes or 'resume' for partially traversed nodes.
    private static class TarjanState
    {
        final int start;
        final EdgeIterator iter;

        // Iterator only present in 'resume' state.
        boolean isStart()
        {
            return iter == null;
        }

        private TarjanState( final int start, final EdgeIterator iter )
        {
            this.start = start;
            this.iter = iter;
        }

        public static TarjanState startState( int start )
        {
            return new TarjanState(start, null);
        }

        public static TarjanState resumeState( int start, EdgeIterator iter )
        {
            return new TarjanState(start, iter);
        }
    }
}
