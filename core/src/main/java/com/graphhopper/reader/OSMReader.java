/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.Helper;
import static com.graphhopper.util.Helper.*;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.StopWatch;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class parses an OSM xml file and creates a graph from it. See run.sh on
 * how to use it from command line.
 *
 * @author Peter Karich
 */
public class OSMReader {

    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private long locations;
    private long skippedLocations;
    private GraphStorage graphStorage;
    private OSMReaderHelper helper;
    private Boolean negativeIds;

    public OSMReader(GraphStorage storage, long expectedNodes) {
        this.graphStorage = storage;
        helper = createDoubleParseHelper(expectedNodes);
        helper.acceptWay(new AcceptWay(true, false, false));
    }

    private OSMReaderHelper createDoubleParseHelper(long expectedNodes) {
        return new OSMReaderHelperDoubleParse(graphStorage, expectedNodes);
    }

    private InputStream createInputStream(File file) throws IOException {
        FileInputStream fi = new FileInputStream(file);
        if (file.getAbsolutePath().endsWith(".gz"))
            return new GZIPInputStream(fi);
        else if (file.getAbsolutePath().endsWith(".zip"))
            return new ZipInputStream(fi);

        return fi;
    }

    public void osm2Graph(File osmXmlFile) throws IOException {
        helper.preProcess(createInputStream(osmXmlFile));
        writeOsm2Graph(createInputStream(osmXmlFile));
    }

    /**
     * Creates the edges and nodes files from the specified inputstream (osm xml
     * file).
     */
    void writeOsm2Graph(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");

        int tmp = (int) Math.max(helper.foundNodes() / 50, 100);
        logger.info("creating graph. Found nodes (pillar+tower):" + nf(helper.foundNodes()) + ", " + Helper.memInfo());
        graphStorage.create(tmp);
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        long wayStart = -1;
        StopWatch sw = new StopWatch();
        long counter = 1;
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next(), counter++) {

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName())) {
                            processNode(sReader);
                            if (counter % 10000000 == 0) {
                                logger.info(nf(counter) + ", locs:" + nf(locations)
                                        + " (" + skippedLocations + ") " + Helper.memInfo());
                            }
                        } else if ("way".equals(sReader.getLocalName())) {
                            if (wayStart < 0) {
                                helper.startWayProcessing();
                                logger.info(nf(counter) + ", now parsing ways");
                                wayStart = counter;
                                sw.start();
                            }
                            helper.processWay(sReader);
                            if (counter - wayStart == 10000 && sw.stop().getSeconds() > 1) {
                                logger.warn("Something is wrong! Processing ways takes too long! "
                                        + sw.getSeconds() + "sec for only " + (counter - wayStart) + " entries");
                            }
                            // hmmh a bit hacky: counter does +=2 until the next loop
                            if ((counter / 2) % 1000000 == 0) {
                                logger.info(nf(counter) + ", locs:" + nf(locations)
                                        + " (" + skippedLocations + "), edges:" + nf(helper.edgeCount())
                                        + " " + Helper.memInfo());
                            }
                        }
                        break;
                }
            }
            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't process file", ex);
        } finally {
            Helper7.close(sReader);
        }

        helper.finishedReading();
        if (graphStorage.nodes() == 0)
            throw new IllegalStateException("osm must not be empty. read " + counter + " lines and " + locations + " locations");
    }

    private void processNode(XMLStreamReader sReader) throws XMLStreamException {
        long osmId;
        try {
            osmId = Long.parseLong(sReader.getAttributeValue(null, "id"));
        } catch (Exception ex) {
            logger.error("cannot get id from xml node:" + sReader.getAttributeValue(null, "id"), ex);
            return;
        }

        if (negativeIds == null)
            negativeIds = osmId < 0;
        else if (negativeIds != osmId < 0)
            throw new IllegalStateException("You cannot mix positive and negative ids. See #42");
        if (negativeIds)
            osmId = -osmId;

        double lat = -1;
        double lon = -1;
        try {
            lat = Double.parseDouble(sReader.getAttributeValue(null, "lat"));
            lon = Double.parseDouble(sReader.getAttributeValue(null, "lon"));
            if (isInBounds(lat, lon)) {
                helper.addNode(osmId, lat, lon);
                locations++;
            } else {
                skippedLocations++;
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle lon/lat of node " + osmId + ": " + lat + "," + lon, ex);
        }
    }

    boolean isInBounds(double lat, double lon) {
        return true;
    }

    public GraphStorage graph() {
        return graphStorage;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    public OSMReader acceptWay(AcceptWay acceptWay) {
        helper.acceptWay(acceptWay);
        return this;
    }

    OSMReaderHelper helper() {
        return helper;
    }

    public void wayPointMaxDistance(double maxDist) {
        helper.wayPointMaxDistance(maxDist);
    }
}
