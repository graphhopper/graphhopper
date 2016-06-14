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

    protected static final EncodingManager encodingManager = new EncodingManager("car,foot");
    protected FlagEncoder carEncoder;

    private MatrixAlgorithmFactory factory;

    @Before
    public void init(){
        factory = new SimpleMatrixAlgorithmFactory();
        carEncoder = encodingManager.getEncoder("car");
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

        DistanceMatrix matrix = algorithm.calcMatrix(new int[]{0}, new int[]{1});

        Assert.assertEquals(1, matrix.getNumberOfOrigins());
        Assert.assertEquals(1, matrix.getNumberOfDestinations());
        Assert.assertEquals(10, matrix.getDistance(0,0), DELTA);
    }


    @Test
    public void test3x3Matrix(){

        AlgorithmOptions options = getMatrixAlgorithmOptions();

        GraphHopperStorage ghStorage = createGHStorage(options);
        initEleGraph(ghStorage);

        MatrixAlgorithm algorithm = createAlgo(ghStorage, options);

        DistanceMatrix matrix = algorithm.calcMatrix(new int[]{0,1,2}, new int[]{0,1,2});


        Assert.assertEquals(3, matrix.getNumberOfOrigins());
        Assert.assertEquals(3, matrix.getNumberOfDestinations());

        // Origin 0 to [0, 1, 2]
        Assert.assertEquals(0, matrix.getDistance(0, 0), DELTA);
        Assert.assertEquals("matrix:\n" + matrix, 10, matrix.getDistance(0, 1), DELTA);
        Assert.assertEquals(10+10, matrix.getDistance(0, 2), DELTA);

        // Origin 1 to [0, 1, 2]
        Assert.assertEquals(10, matrix.getDistance(1, 0), DELTA);
        Assert.assertEquals(0,  matrix.getDistance(1, 1), DELTA);
        Assert.assertEquals(10, matrix.getDistance(1, 2), DELTA);

        // Origin 2 to [0, 1, 2]
        Assert.assertEquals(20, matrix.getDistance(2, 0), DELTA);
        Assert.assertEquals(10, matrix.getDistance(2, 1), DELTA);
        Assert.assertEquals(0,  matrix.getDistance(2, 2), DELTA);
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
