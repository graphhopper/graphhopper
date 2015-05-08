package com.graphhopper.reader.osgb;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.StopWatch;

abstract public class AbstractOsReader<E> implements DataReader<E> {

    private static final String TIME_PASS1_PASS2_TOTAL_FORMAT = "time(pass1): {} pass2: {} total: ";
    protected static final String PREPROCESS_FORMAT = "preprocess: {}";

    protected EncodingManager encodingManager;
    protected int workerThreads = -1;
    protected final DouglasPeucker simplifyAlgo = new DouglasPeucker();
    protected boolean doSimplify = true;
    private File routingFile;
    protected final GraphStorage graphStorage;
    protected final NodeAccess nodeAccess;
    protected ElevationProvider elevationProvider = ElevationProvider.NOOP;

    private static final Logger logger = LoggerFactory.getLogger(AbstractOsReader.class.getName());
    protected static final String WE_HAVE_EVALUATED_WAY_NODES_FORMAT = "We have evaluated {} way nodes.";

    public AbstractOsReader(GraphStorage storage) {
        this.graphStorage = storage;
        this.nodeAccess = graphStorage.getNodeAccess();

    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    @Override
    public AbstractOsReader<E> setEncodingManager(EncodingManager acceptWay) {
        this.encodingManager = acceptWay;
        return this;
    }

    @Override
    public AbstractOsReader<E> setWayPointMaxDistance(double maxDist) {
        doSimplify = maxDist > 0;
        simplifyAlgo.setMaxDistance(maxDist);
        return this;
    }

    @Override
    public AbstractOsReader<E> setWorkerThreads(int numOfWorkers) {
        this.workerThreads = numOfWorkers;
        return this;
    }

    @Override
    public void readGraph() throws IOException {
        if (encodingManager == null)
            throw new IllegalStateException("Encoding manager was not set.");

        if (routingFile == null)
            throw new IllegalStateException("No OS file specified");

        if (!routingFile.exists())
            throw new IllegalStateException("Your specified OS file does not exist:" + routingFile.getAbsolutePath());

        StopWatch sw1 = new StopWatch().start();
        preProcess(routingFile);
        sw1.stop();

        StopWatch sw2 = new StopWatch().start();
        try {
            writeOsm2Graph(routingFile);
        } catch (MismatchedDimensionException | XMLStreamException | IOException | FactoryException | TransformException e) {
            logger.error("Exception in writing OSM2Graph", e);
            throw new IllegalStateException("Exception in writing OSM2Graph");
        }
        sw2.stop();

        logger.info(TIME_PASS1_PASS2_TOTAL_FORMAT, (int) sw1.getSeconds(), (int) sw2.getSeconds(), ((int) (sw1.getSeconds() + sw2.getSeconds())));
    }

    /**
     * Preprocessing of routing file to select nodes which are used for
     * highways. This allows a more compact graph data structure.
     */
    protected void preProcess(File routingFile) {
        try {
            preProcessDirOrFile(routingFile);
        } catch (Exception ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        }
    }

    protected void preProcessDirOrFile(File routingFile) throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        if (routingFile.isDirectory()) {
            String absolutePath = routingFile.getAbsolutePath();
            String[] list = routingFile.list();
            for (String file : list) {
                File nextFile = new File(absolutePath + File.separator + file);
                preProcessDirOrFile(nextFile);
            }
        } else {
            preProcessSingleFile(routingFile);
        }
    }

    abstract protected void preProcessSingleFile(File routingFile) throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException;

    abstract protected void writeOsm2Graph(File routingFile) throws MismatchedDimensionException, XMLStreamException, IOException, FactoryException, TransformException;

    @Override
    public AbstractOsReader<E> setOSMFile(File routingFile) {
        this.routingFile = routingFile;
        return this;
    }

    @Override
    public GraphStorage getGraphStorage() {
        return graphStorage;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public AbstractOsReader<E> setElevationProvider(ElevationProvider elevationProvider) {
        if (elevationProvider == null)
            throw new IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider");

        if (!nodeAccess.is3D() && ElevationProvider.NOOP != elevationProvider)
            throw new IllegalStateException("Make sure you graph accepts 3D data");

        this.elevationProvider = elevationProvider;
        return this;
    }

}
