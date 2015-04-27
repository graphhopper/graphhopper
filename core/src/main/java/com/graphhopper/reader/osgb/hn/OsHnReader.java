package com.graphhopper.reader.osgb.hn;


import gnu.trove.map.TLongObjectMap;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.osgb.AbstractOsReader;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.Helper;

public class OsHnReader extends AbstractOsReader<Long> {


    private static final Logger logger = LoggerFactory.getLogger(OsHnReader.class.getName());

    private TLongObjectMap<String> edgeEnvironmentMap;

    public OsHnReader(GraphStorage storage) {
        this(storage, null);
    }
    public OsHnReader(GraphStorage storage, TLongObjectMap<String> edgeEnvironmentMap) {
        super(storage);
        this.edgeEnvironmentMap = edgeEnvironmentMap;
    }

    @Override
    public int getInternalNodeIdOfOsmNode(Long viaOsm) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Long getOsmIdOfInternalEdge(int edge) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void preProcess(File itnFile) {
        try {
            preProcessDirOrFile(itnFile);
        } catch (Exception ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        }
    }

    private void preProcessDirOrFile(File osmFile) throws XMLStreamException,
    IOException, MismatchedDimensionException, FactoryException,
    TransformException {
        if (osmFile.isDirectory()) {
            String absolutePath = osmFile.getAbsolutePath();
            String[] list = osmFile.list();
            for (String file : list) {
                File nextFile = new File(absolutePath + File.separator + file);
                preProcessDirOrFile(nextFile);
            }
        } else {
            preProcessSingleFile(osmFile);
        }
    }

    private void preProcessSingleFile(File osmFile) throws XMLStreamException,
    IOException, MismatchedDimensionException, FactoryException,
    TransformException {
        OsHnInputFile in = null;
        try {
            logger.error(PREPROCESS_FORMAT, osmFile.getName());
            in = new OsHnInputFile(osmFile);
            in.setWorkerThreads(workerThreads).open();
            preProcessSingleFile(in);
        } finally {
            Helper.close(in);
        }
    }

    @Override
    protected void writeOsm2Graph(File osmFile) {
        // TODO Auto-generated method stub

    }

    private void preProcessSingleFile(OsHnInputFile in)
            throws XMLStreamException, MismatchedDimensionException,
            FactoryException, TransformException {
        logger.error("==== preProcessSingleFile");
        RoutingElement item;
        while ((item = in.getNext()) != null) {
            // Look for this road (or is a road link) in the itn data and add additional tags based on environment
            if (edgeEnvironmentMap!=null) {
                // No instanceof check required yet as only OsHnRoadLink are returned
                //if (item instanceof OsHnRoadLink) {
                OsHnRoadLink osHnRoadLink = (OsHnRoadLink)item;
                String environment = osHnRoadLink.getEnvironment();
                long id = osHnRoadLink.getId();
                edgeEnvironmentMap.put(id, environment);
                //}
            }
        }
        System.out.println("=====================> We have found environments for " + edgeEnvironmentMap.size() + " ways");
    }


}
