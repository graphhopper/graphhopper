package com.graphhopper.routing.matrix.algorithm;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.matrix.BucketEntry;
import com.graphhopper.routing.matrix.DistanceMatrix;
import com.graphhopper.routing.matrix.MatrixEntry;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.Snap;

import java.util.List;
import java.util.PriorityQueue;

public abstract class AbstractManyToMany implements MatrixAlgorithm {


    protected RoutingCHGraph graph;

    protected Weighting weighting;

    protected RoutingCHEdgeExplorer inEdgeExplorer;
    protected RoutingCHEdgeExplorer outEdgeExplorer;
    protected CHEdgeFilter levelEdgeFilter;

    protected IntObjectMap<IntObjectMap<BucketEntry>> bucket;

    protected boolean alreadyRun = false;

    protected int size;
    protected int maxVisitedNodes = Integer.MAX_VALUE;
    protected int visitedNodes = 0;

    protected int maxNodes;

    protected IntObjectMap<MatrixEntry> map;
    protected PriorityQueue<MatrixEntry> heap;

    protected IntDoubleMap tentativeWeights;

    protected IntSet visited = new IntHashSet();

    public AbstractManyToMany(QueryRoutingCHGraph graph){

        this.graph = graph;
        this.weighting = graph.getWrappedWeighting();
        this.inEdgeExplorer = graph.createInEdgeExplorer();
        this.outEdgeExplorer = graph.createOutEdgeExplorer();
        this.maxNodes = graph.getBaseGraph().getBaseGraph().getNodes();
        this.levelEdgeFilter = new CHEdgeFilter() {

            @Override
            public boolean accept(RoutingCHEdgeIteratorState edgeState) {

                int base = edgeState.getBaseNode();
                int adj = edgeState.getAdjNode();

                // always accept virtual edges, see #288
                if (base >= maxNodes || adj >= maxNodes) return true;

                // minor performance improvement: shortcuts in wrong direction are disconnected, so no need to exclude them
                if (edgeState.isShortcut()) return true;

                return graph.getLevel(base) <= graph.getLevel(adj);

            }
        };

        this.size = Math.min(Math.max(200, graph.getNodes() / 10), 150_000);
        this.bucket = new GHIntObjectHashMap<>(size);

        this.map = new GHIntObjectHashMap<>(size);
        this.heap = new PriorityQueue<>(size);
        this.tentativeWeights = new IntDoubleHashMap(100);
    }

    @Override
    public DistanceMatrix calcMatrix(List<Snap>  sources, List<Snap> targets){

        checkAlreadyRun();

        DistanceMatrix matrix = new DistanceMatrix(sources.size(),targets.size());
        IntObjectMap<IntArrayList> targetIdxsNodes = new GHIntObjectHashMap<>(targets.size());

        //Backward
        int idxTarget =0;
        while(idxTarget < targets.size()){
            int targetClosestNode = targets.get(idxTarget).getClosestNode();

            //Avoid iterate over the same node two times
            if(!targetIdxsNodes.containsKey(targetClosestNode)){
                IntArrayList a = new IntArrayList();
                a.add(idxTarget);
                targetIdxsNodes.put(targetClosestNode,a);
                backwardSearch(targets.get(idxTarget));
            }else{
                targetIdxsNodes.get(targetClosestNode).add(idxTarget);
            }


            idxTarget++;
        }

        //Forward
        int idxSource =0;
        while(idxSource < sources.size()){
            forwardSearch(sources.get(idxSource),idxSource,matrix,targetIdxsNodes);
            idxSource++;
        }

        return matrix;
    }

    protected void checkAlreadyRun() {
        if (alreadyRun) throw new IllegalStateException("Create a new instance per call");
        alreadyRun = true;
    }

    protected void backwardSearch( Snap targetSnap){

        visited.clear();

        int target = targetSnap.getClosestNode();

        this.map.clear();
        this.heap.clear();

        MatrixEntry currEdge = new MatrixEntry(target,0,0,0);
        heap.add(currEdge);

        // For the first step though we need all edges, so we need to ignore this filter.
        CHEdgeFilter tmpEdgeFilter = this.levelEdgeFilter;
        this.levelEdgeFilter = CHEdgeFilter.ALL_EDGES;

        boolean run;

        do {
            visitedNodes++;
            currEdge = heap.poll();
            int currNode = currEdge.adjNode;

            if(visited.contains(currNode)){
                run = !heap.isEmpty();
                continue;
            }

            RoutingCHEdgeIterator iterator = inEdgeExplorer.setBaseNode(currNode);

            while(iterator.next()){

                if(!accept(iterator,currEdge))
                    continue;

                final double weight = calcWeight(iterator,currEdge,true);

                if(!Double.isFinite(weight))
                    continue;

                final int origEdgeId = getOrigEdgeId(iterator, true);
                final int traversalId = getTraversalId(iterator, origEdgeId, true);
                MatrixEntry entry = map.get(traversalId);

                if (entry == null) {

                    final double distance = calcDistance(iterator, currEdge);
                    final long time = calcTime(iterator, currEdge, true);

                    entry = new MatrixEntry(iterator.getEdge(), origEdgeId, iterator.getAdjNode(), weight, time, distance);
                    map.put(traversalId, entry);
                    heap.add(entry);

                    saveToBucket(entry, iterator, target);

                } else if (entry.getWeightOfVisitedPath() > weight) {


                    final double distance = calcDistance(iterator, currEdge);
                    final long time = calcTime(iterator, currEdge, true);

                    heap.remove(entry);
                    entry.edge = iterator.getEdge();
                    entry.weight = weight;
                    entry.distance = distance;
                    entry.time = time;
                    heap.add(entry);

                    saveToBucket(entry, iterator, target);

                }
            }

            this.levelEdgeFilter = tmpEdgeFilter;

            visited.add(currNode);
            run = (visitedNodes <= maxVisitedNodes) && !heap.isEmpty();
        }while(run);
    }

    protected abstract int getTraversalId(RoutingCHEdgeIteratorState edge, int origEdgeId, Boolean reverse);

    protected int getOrigEdgeId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getOrigEdgeFirst() : edge.getOrigEdgeLast();
    }

    protected boolean accept(RoutingCHEdgeIteratorState edge, MatrixEntry currEdge) {

        if(edge.getEdge() == getIncomingEdge(currEdge))
            return false;
        else
            return levelEdgeFilter == null || levelEdgeFilter.accept(edge);
    }

    protected abstract double calcWeight(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge, boolean reverse);

    protected abstract long calcTime(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge, boolean reverse);

    protected int getIncomingEdge(MatrixEntry entry) {
        return entry.edge;
    }

    protected abstract double calcDistance(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge);

    protected void saveToBucket(MatrixEntry entry, RoutingCHEdgeIteratorState iter, int target){
        int node = iter.getAdjNode();

        if(node != target){

            IntObjectMap<BucketEntry> bucketDistances = bucket.get(node);

            if(bucketDistances == null){

                bucketDistances = new GHIntObjectHashMap<>();
                bucketDistances.put(target,new BucketEntry(entry.weight,entry.time ,entry.distance));
                bucket.put(node,bucketDistances);
            }else {

                BucketEntry targetEntry = bucketDistances.get(target);
                if (targetEntry == null || targetEntry.weight > entry.weight) {
                    bucketDistances.put(target, new BucketEntry(entry.weight, entry.time, entry.distance));
                }
            }
        }
    }

    protected void forwardSearch(Snap sourceSnap, int idxSource, DistanceMatrix dm, IntObjectMap<IntArrayList> targets){

        int source = sourceSnap.getClosestNode();

        this.visited.clear();
        this.map.clear();
        this.heap.clear();
        this.tentativeWeights.clear();

        //For the case when the shortest path is the direct path between source and target
        saveBestPath(source,idxSource,targets,source,0,0,0,dm);

        MatrixEntry currEdge = new MatrixEntry(source,0,0,0);
        map.put(source,currEdge);
        heap.add(currEdge);

        // For the first step though we need all edges, so we need to ignore this filter.
        CHEdgeFilter tmpEdgeFilter = this.levelEdgeFilter;
        this.levelEdgeFilter = CHEdgeFilter.ALL_EDGES;

        boolean run;

        do {
            visitedNodes++;

            currEdge = heap.poll();

            int currNode = currEdge.adjNode;

            if(visited.contains(currNode)){
                run = !heap.isEmpty();
                continue;
            }

            final RoutingCHEdgeIterator iterator = outEdgeExplorer.setBaseNode(currNode);
            while (iterator.next()) {

                if (!accept(iterator, currEdge))
                    continue;

                final double weight = calcWeight(iterator, currEdge, false);
                if (!Double.isFinite(weight))
                    continue;

                final int origEdgeId = getOrigEdgeId(iterator, false);
                final int traversalId = getTraversalId(iterator, origEdgeId, false);
                MatrixEntry entry = map.get(traversalId);

                if (entry == null) {

                    final double distance = calcDistance(iterator, currEdge);
                    final long time = calcTime(iterator, currEdge, false);

                    entry = new MatrixEntry(iterator.getEdge(), origEdgeId, iterator.getAdjNode(), weight, time, distance);
                    map.put(traversalId, entry);
                    heap.add(entry);

                    saveBestPath(source,idxSource,targets,iterator.getAdjNode(),weight,time,distance,dm);

                } else if (entry.getWeightOfVisitedPath() > weight) {

                    final double distance = calcDistance(iterator, currEdge);
                    final long time = calcTime(iterator, currEdge, false);

                    heap.remove(entry);
                    entry.edge = iterator.getEdge();
                    entry.weight = weight;
                    entry.distance = distance;
                    entry.time = time;
                    heap.add(entry);

                    saveBestPath(source,idxSource,targets,iterator.getAdjNode(),weight,time,distance,dm);
                }
            }

            this.levelEdgeFilter = tmpEdgeFilter;

            visited.add(currNode);

            run = (visitedNodes <= maxVisitedNodes) && !heap.isEmpty();
        }while(run);

    }

    private void saveBestPath( int sourceNode,int idxSource, IntObjectMap<IntArrayList> targets,int currNode, double currentEdgeWeight,
                               long currentEdgeTime, double currentEdgeDistance, DistanceMatrix dm){

        final IntObjectMap<BucketEntry> bucketEntries = bucket.get(currNode);
        if(bucketEntries != null){

            for( IntObjectCursor<BucketEntry> next : bucketEntries){

                int target = next.key;

                if(sourceNode == target) continue;

                final double savedWeight = tentativeWeights.get(target);
                final double currentWeight = currentEdgeWeight + next.value.weight;

                if(savedWeight == 0.0){

                    final long time = currentEdgeTime + next.value.time;
                    final double distance = currentEdgeDistance + next.value.distance;
                    tentativeWeights.put(target,currentWeight);

                    for(IntCursor idxNext : targets.get(target)){
                        dm.setCell(idxSource,idxNext.value,distance,time);
                    }

                } else if(currentWeight < savedWeight){

                    final long time = currentEdgeTime + next.value.time;
                    final double distance = currentEdgeDistance + next.value.distance;
                    tentativeWeights.put(target,currentWeight);

                    for(IntCursor idxNext : targets.get(target)){
                        dm.setCell(idxSource,idxNext.value,distance,time);
                    }
                }
            }
        }
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    @Override
    public void setMaxVisitedNodes(int numberOfNodes){
        this.maxVisitedNodes = numberOfNodes;
    }
}