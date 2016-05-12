package com.graphhopper.matrix.algorithm;

import com.graphhopper.matrix.DistanceMatrix;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;

import java.util.List;

/**
 * This implementation will just run a plain route algorithm for
 * each origin-destination combination. The used route algorithm can be
 * specified using the routingAlgoOptions.
 *
 * Data structures between the route algorithms are not reused, resulting
 * in O(R^2) performance hit, where R is the complexity of the underlying route algorithm,
 * assuming a quadratic matrix (origins == destinations)
 *
 * For Dijkstra this is O( (|E|+|V|log(|V|))^2 )
 *
 * @author Pascal Büttiker
 */
public class OneToOneLoopMatrixAlgorithm extends AbstractMatrixAlgorithm {

    private final RoutingAlgorithmFactory routingFactory = new RoutingAlgorithmFactorySimple();
    private final AlgorithmOptions routingAlgoOptions;


    /**
     * @param graph                 specifies the graph where this algorithm will run on
     * @param encoder               sets the used vehicle (bike, car, foot)
     * @param weighting             set the used weight calculation (e.g. fastest, shortest).
     * @param traversalMode         how the graph is traversed e.g. if via nodes or edges.
     * @param routingAlgoOptions    the route algorithm options used for each route request
     */
    public OneToOneLoopMatrixAlgorithm(Graph graph, FlagEncoder encoder, Weighting weighting,
                                       TraversalMode traversalMode, AlgorithmOptions routingAlgoOptions) {
        super(graph, encoder, weighting, traversalMode);
        this.routingAlgoOptions = routingAlgoOptions;
    }


    @Override
    public DistanceMatrix calcMatrix(List<Integer> origins, List<Integer> destinations) {

        DistanceMatrix matrix = new DistanceMatrix();

        for (int origin : origins) {

            DistanceMatrix.DistanceRow row = matrix.addRow(origin);

            for (int destination : destinations) {

                RoutingAlgorithm algorithm = routingFactory.createAlgo(graph, routingAlgoOptions);
                Path path = algorithm.calcPath(origin, destination);

                row.addDestination(destination, path.getDistance(), path.getTime());
            }
        }

        return matrix;
    }
}
