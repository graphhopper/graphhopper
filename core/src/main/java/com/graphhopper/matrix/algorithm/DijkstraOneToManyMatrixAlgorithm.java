package com.graphhopper.matrix.algorithm;

import com.graphhopper.matrix.DistanceMatrix;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;

import java.util.List;

/**
 * This implementation uses the DijkstraOneToMany algorithm, which can reuse datastructures as long
 * as the same start-node is in use.
 *
 * This means we reuse the algorithm/cache per row.
 * I.e. for each origin, there is a new DijkstraOneToMany / cache created.
 *
 * @author Pascal Büttiker
 */
public class DijkstraOneToManyMatrixAlgorithm extends AbstractMatrixAlgorithm {

    private final RoutingAlgorithmFactory routingFactory = new RoutingAlgorithmFactorySimple();
    private final AlgorithmOptions underlyingAlgo;


    /**
     * @param graph         specifies the graph where this algorithm will run on
     * @param encoder       sets the used vehicle (bike, car, foot)
     * @param weighting     set the used weight calculation (e.g. fastest, shortest).
     * @param traversalMode how the graph is traversed e.g. if via nodes or edges.
     */
    public DijkstraOneToManyMatrixAlgorithm(Graph graph, FlagEncoder encoder, Weighting weighting,
                                            TraversalMode traversalMode, AlgorithmOptions underlyingAlgo) {
        super(graph, encoder, weighting, traversalMode);
        this.underlyingAlgo = underlyingAlgo;
    }


    @Override
    public DistanceMatrix calcMatrix(List<Integer> origins, List<Integer> destinations) {

        DistanceMatrix matrix = new DistanceMatrix();

        DijkstraOneToMany algorithm = (DijkstraOneToMany)routingFactory.createAlgo(graph, underlyingAlgo);

        algorithm.clear();

        for (int origin : origins) {

            algorithm.clear(); // Clear cache on different start node
            DistanceMatrix.DistanceRow row = matrix.addRow(origin);

            for (int destination : destinations) {
                if(origin != destination){
                    Path path = algorithm.calcPath(origin, destination);
                    row.addDestination(destination, path.getDistance(), path.getTime());
                }else{
                    // No need to search for a path, we are already there.
                    row.addDestination(destination, 0, 0);
                }
            }
        }

        return matrix;
    }
}
