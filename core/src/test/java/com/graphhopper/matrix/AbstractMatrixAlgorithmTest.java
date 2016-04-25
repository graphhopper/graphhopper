package com.graphhopper.matrix;

import com.graphhopper.matrix.algorithm.MatrixAlgorithm;
import com.graphhopper.matrix.algorithm.MatrixAlgorithmFactory;
import com.graphhopper.matrix.algorithm.SimpleMatrixAlgorithmFactory;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Base class for matrix tests, takes MatrixAlgorithm implementations and runs tests against them.
 */
public abstract class AbstractMatrixAlgorithmTest {

    private static final double DELTA = 0.000001;

    protected static final EncodingManager encodingManager = new EncodingManager("CAR,FOOT");
    protected FlagEncoder carEncoder;

    private MatrixAlgorithmFactory factory;

    @Before
    public void init(){
        factory = new SimpleMatrixAlgorithmFactory();
        carEncoder = encodingManager.getEncoder("CAR");
    }

    /**
     * Subclasses which implement the test have to define the used algorithm
     */
    protected abstract AlgorithmOptions getMatrixAlgorithmOptions();

    @Test
    public void test1x1Matrix(){

        AlgorithmOptions options = getMatrixAlgorithmOptions();

        GraphHopperStorage ghStorage = createGHStorage(options);
        initEleGraph(ghStorage);

        MatrixAlgorithm algorithm = createAlgo(ghStorage, options);

        DistanceMatrix matrix = algorithm.calcMatrix(Arrays.asList(0), Arrays.asList(1));

        Assert.assertEquals(1, matrix.getRows().size());
        Assert.assertEquals(0, matrix.getRow(0).originNode);
        Assert.assertEquals(1, matrix.getRow(0).getDestinations().size());
        Assert.assertEquals(1, matrix.getRow(0).getDestinations().get(0).destinationNode);

        Assert.assertEquals(10, matrix.getRow(0).getDestinations().get(0).distance, DELTA);
    }


    @Test
    public void test3x3Matrix(){

        AlgorithmOptions options = getMatrixAlgorithmOptions();

        GraphHopperStorage ghStorage = createGHStorage(options);
        initEleGraph(ghStorage);

        MatrixAlgorithm algorithm = createAlgo(ghStorage, options);

        DistanceMatrix matrix = algorithm.calcMatrix(Arrays.asList(0, 1, 2), Arrays.asList(0, 1, 2));


        Assert.assertEquals(3, matrix.getRows().size());

        // Origin 0 to [0, 1, 2]
        DistanceMatrix.DistanceRow row1 = matrix.getRow(0);
        Assert.assertEquals(0, row1.originNode);
        Assert.assertEquals(3, row1.getDestinations().size());
        Assert.assertEquals(0, row1.getDestinations().get(0).destinationNode);
        Assert.assertEquals(0, row1.getDestinations().get(0).distance, DELTA);
        Assert.assertEquals(1, row1.getDestinations().get(1).destinationNode);
        Assert.assertEquals(10, row1.getDestinations().get(1).distance, DELTA);
        Assert.assertEquals(2, row1.getDestinations().get(2).destinationNode);
        Assert.assertEquals(10+10, row1.getDestinations().get(2).distance, DELTA);


        // Origin 1 to [0, 1, 2]
        DistanceMatrix.DistanceRow row2 = matrix.getRow(1);
        Assert.assertEquals(1, row2.originNode);
        Assert.assertEquals(3, row2.getDestinations().size());
        Assert.assertEquals(0, row2.getDestinations().get(0).destinationNode);
        Assert.assertEquals(10, row2.getDestinations().get(0).distance, DELTA);
        Assert.assertEquals(1, row2.getDestinations().get(1).destinationNode);
        Assert.assertEquals(0, row2.getDestinations().get(1).distance, DELTA);
        Assert.assertEquals(2, row2.getDestinations().get(2).destinationNode);
        Assert.assertEquals(10, row2.getDestinations().get(2).distance, DELTA);

        // Origin 2 to [0, 1, 2]
        DistanceMatrix.DistanceRow row3 = matrix.getRow(2);
        Assert.assertEquals(2, row3.originNode);
        Assert.assertEquals(3, row3.getDestinations().size());
        Assert.assertEquals(0, row3.getDestinations().get(0).destinationNode);
        Assert.assertEquals(20, row3.getDestinations().get(0).distance, DELTA);
        Assert.assertEquals(1, row3.getDestinations().get(1).destinationNode);
        Assert.assertEquals(10, row3.getDestinations().get(1).distance, DELTA);
        Assert.assertEquals(2, row3.getDestinations().get(2).destinationNode);
        Assert.assertEquals(0, row3.getDestinations().get(2).distance, DELTA);

    }


    // 0-1-2
    // |\| |
    // 3 4-11
    // | | |
    // 5-6-7
    // | |\|
    // 8-9-10
    @SuppressWarnings("Duplicates")
    Graph initEleGraph(Graph g )
    {
        g.edge(0, 1, 10, true);
        g.edge(0, 4, 12, true);
        g.edge(0, 3, 5, true);
        g.edge(1, 2, 10, true);
        g.edge(1, 4, 5, true);
        g.edge(3, 5, 5, false);
        g.edge(5, 6, 10, true);
        g.edge(5, 8, 10, true);
        g.edge(6, 4, 5, true);
        g.edge(6, 7, 10, true);
        g.edge(6, 10, 12, true);
        g.edge(6, 9, 12, true);
        g.edge(2, 11, 5, false);
        g.edge(4, 11, 10, true);
        g.edge(7, 11, 5, true);
        g.edge(7, 10, 5, true);
        g.edge(8, 9, 10, false);
        g.edge(9, 8, 9, false);
        g.edge(10, 9, 10, false);
        return g;
    }



    protected final MatrixAlgorithm createAlgo(GraphHopperStorage ghStorage, AlgorithmOptions opts )
    {
        return factory.build(getGraph(ghStorage, opts.getWeighting()), opts);
    }

    protected Graph getGraph( GraphHopperStorage ghStorage, Weighting weighting )
    {
        return ghStorage.getGraph(Graph.class, weighting);
    }


    protected GraphHopperStorage createGHStorage(EncodingManager em, List<? extends Weighting> weightings, boolean is3D )
    {
        return new GraphBuilder(em).set3D(is3D).create();
    }
    protected GraphHopperStorage createGHStorage( AlgorithmOptions options )
    {
        return createGHStorage(options, false);
    }

    protected GraphHopperStorage createGHStorage( AlgorithmOptions options, boolean is3D )
    {
        return createGHStorage(encodingManager, Arrays.asList(options.getWeighting()), is3D);
    }

}
