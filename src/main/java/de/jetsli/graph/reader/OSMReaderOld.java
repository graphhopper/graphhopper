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
package de.jetsli.graph.reader;

import de.jetsli.graph.dijkstra.DijkstraBidirection;
import de.jetsli.graph.storage.GeoGraph;
import de.jetsli.graph.storage.GeoLocation;
import de.jetsli.graph.storage.GeoLocationSimple;
import de.jetsli.graph.util.BufferedSimpleInputStream;
import de.jetsli.graph.util.BufferedSimpleOutputStream;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $ ulimit -v unlimited
 * $ export JAVA_OPTS="-Xmx1000m"
 * mvn -o -e exec:java -Dexec.mainClass="de.jetsli.graph.dijkstra.OSM" -Dexec.args="/media/SAMSUNG/germany.osm"
 * 
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReaderOld implements OSMReader {

    public static void main(String[] args) throws Exception {
        new OSMReaderOld().read(args);
    }

    // see tests for the xml format
    public void read(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalStateException(".osm file missing");

        // I'm having the osm on an external usb drive serving the osm async to our binary file
        File file = new File(args[0]);
        if (!file.exists())
            throw new IllegalStateException("File does not exist:" + args[0]);

        logger.info("Start parsing " + file);
        writeOsm2Binary(new FileInputStream(file));
        GeoGraph g = readGraph();
        System.out.println("path:" + new DijkstraBidirection(g).calcShortestPath(123, 140).toString());
    }
    private static final int MB = 1 << 20;
    private int maxLocs = 95 * 1000 * 1000;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private TIntIntHashMap osmIdToIndexMap;
    private int nextLocationIndex = 0;
    private int nextEdgeIndex = 0;
//    private RandomAccessFile edgesFile;
//    private RandomAccessFile locationsFile;
    private BufferedSimpleOutputStream edgesOutFile;
    private BufferedSimpleInputStream edgesInFile;
    private BufferedSimpleOutputStream locationsOutFile;
    private float[] lats;
    private float[] lons;
    private File tmpEdgesFilename;
    private File tmpLocationsFilename;

    /**
     * Writes two files from the inputstream which is the standard osm xml file.
     */
    public void writeOsm2Binary(InputStream is) {
        printMemInfo();
        osmIdToIndexMap = createIntMap(maxLocs);
        lats = new float[maxLocs];
        lons = new float[maxLocs];
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            openFiles(tmpLocationsFilename = newLocationsFilename(), tmpEdgesFilename = newEdgesFilename(), false);

            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {
                if (++counter % 1000000 == 0)
                    logger.info(counter + ", locs:" + nextLocationIndex + ", edges:" + nextEdgeIndex
                            + ", totalMB:" + Runtime.getRuntime().totalMemory() / MB
                            + ", usedMB:" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB);

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName()))
                            processNode(sReader);
                        else if ("way".equals(sReader.getLocalName()))
                            processWay(sReader);

                        break;
                }
            }
            osmIdToIndexMap = null;
            System.gc();

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeFiles();
            try {
                if (sReader != null)
                    sReader.close();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public void processNode(XMLStreamReader sReader) throws XMLStreamException {
        int osmId = -1;
        try {
            osmId = Integer.parseInt(sReader.getAttributeValue(null, "id"));
        } catch (Exception ex) {
            logger.error("cannot get id from node:" + sReader.getAttributeValue(null, "id"), ex);
            return;
        }

        int old = osmIdToIndexMap.putIfAbsent(osmId, nextLocationIndex);
        if (old != osmIdToIndexMap.getNoEntryValue())
            throw new IllegalStateException("id " + osmId + " already taken");

        try {
            lats[nextLocationIndex] = Float.parseFloat(sReader.getAttributeValue(null, "lat"));
            lons[nextLocationIndex] = Float.parseFloat(sReader.getAttributeValue(null, "lon"));
        } catch (Exception ex) {
            logger.error("cannot get lon/lat from node:" + sReader.getAttributeValue(null, "lon") + " " + sReader.getAttributeValue(null, "lat"), ex);
        }

        try {
            locationsOutFile.writeFloat(lats[nextLocationIndex]);
            locationsOutFile.writeFloat(lons[nextLocationIndex + 4]);
        } catch (Exception ex) {
            logger.error("cannot write lon/lat to locations file " + getTmpLocationsFilename(), ex);
        }

        nextLocationIndex++;
    }
    TIntArrayList tmpLocs = new TIntArrayList(10);
    byte[] emptyBytes = new byte[]{(byte) 0};

    public void processWay(XMLStreamReader sReader)
            throws XMLStreamException {
//        String roadName = null;
//        long roadOsmId = -1;
//        try {
//            roadOsmId = Long.parseLong(sReader.getAttributeValue(null, "id"));
//        } catch (Exception ex) {
//            logger.error("cannot get id from way:" + sReader.getAttributeValue(null, "id"));
//        }

        tmpLocs.clear();
        for (int tmpE = sReader.nextTag(); tmpE != XMLStreamConstants.END_ELEMENT;
                tmpE = sReader.nextTag()) {
            if (tmpE == XMLStreamConstants.START_ELEMENT) {
                if ("nd".equals(sReader.getLocalName())) {
                    String ref = sReader.getAttributeValue(null, "ref");
                    try {
                        int osmId = Integer.parseInt(ref);
                        int old = osmIdToIndexMap.get(osmId);
                        if (old == osmIdToIndexMap.getNoEntryValue())
                            logger.error("No node found with reference:" + ref);
                        else
                            tmpLocs.add(old);
                    } catch (Exception ex) {
                        logger.error("cannot get ref from way. ref:" + ref, ex);
                    }
                }
//                else if ("tag".equals(sReader.getLocalName())
//                        && "name".equals(sReader.getAttributeValue(null, "k")))
//                    roadName = sReader.getAttributeValue(null, "v");

                sReader.next();
            }
        }

        if (tmpLocs.size() > 1) {
            GeoLocation prev = getLocation(tmpLocs.get(0));
            try {
                for (int index = 1; index < tmpLocs.size(); index++) {
                    // Later: save the name, type, estimated timeDistance too!
                    GeoLocation curr = getLocation(index);
                    edgesOutFile.writeInt(prev.id());
                    edgesOutFile.writeInt(curr.id());
                    edgesOutFile.writeFloat(calcDistKm(prev, curr));
                    edgesOutFile.write(emptyBytes);
                    nextEdgeIndex++;
                    prev = curr;
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public GeoGraph readGraph() {
        if (getTmpEdgesFilename() == null || getTmpLocationsFilename() == null)
            throw new IllegalStateException("please specify filenames if you don't write from OSM file before!");

        return readGraph(getTmpEdgesFilename(), getTmpLocationsFilename());
    }

    /**
     * Retrieves the in-memory graph created from the previously written binary files
     * Later: open those files here again and close them in writeOsm2Binary!
     */
    public GeoGraph readGraph(File edgesFilename, File locationsFilename) {
        try {
            printMemInfo();
            openFiles(locationsFilename, edgesFilename, true);
            logger.info("Reading graph with " + nextLocationIndex + " nodes ...");
            GeoGraph graph = createGraphInstance(nextLocationIndex);
            for (int pointer = 0; pointer < nextLocationIndex; pointer++) {
                if (pointer % 1000000 == 0)
                    logger.info(pointer + " progress ..."+ ", totalMB:" + Runtime.getRuntime().totalMemory() / MB
                            + ", usedMB:" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB);

                graph.addLocation(lats[pointer], lons[pointer]);                
            }
            logger.info("... now reading " + nextEdgeIndex + " edges");
            for (int i = 0; i < nextEdgeIndex; i++) {
                graph.edge(edgesInFile.readInt(), edgesInFile.readInt(),
                        edgesInFile.readFloat(), edgesInFile.readByte() == 0);
            }
            logger.info("graph now in-memory");
            printMemInfo();
            return graph;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeFiles();
        }
    }

    private void closeFiles() {
        try {
            if (locationsOutFile != null)
                locationsOutFile.close();
            if (edgesOutFile != null)
                edgesOutFile.close();
            if (edgesInFile != null)
                edgesInFile.close();

            locationsOutFile = null;
            edgesOutFile = null;
            edgesInFile = null;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    GeoLocation getLocation(int pointer) {
        GeoLocation loc = new GeoLocationSimple(pointer, null);
        loc.lat(lats[pointer]);
        loc.lon(lons[pointer]);
        return loc;
    }

    protected TIntIntHashMap createIntMap(int maxLocs) {
        return new TIntIntHashMap(maxLocs, 0.9f, -1, -1);
    }

    protected GeoGraph createGraphInstance(int no) {
        return new GeoGraph(no);
    }

    public OSMReaderOld setMaxLocations(int maxLocs) {
        this.maxLocs = maxLocs;
        return this;
    }
    static double R = 6371; // km

    /**
     * http://en.wikipedia.org/wiki/Haversine_formula
     * a = sin²(Δlat/2) + cos(lat1).cos(lat2).sin²(Δlong/2)
     * c = 2.atan2(√a, √(1−a))
     * d = R.c
     */
    public static float calcDistKm(GeoLocation from, GeoLocation to) {
        float flat = from.lat();
        float tlat = to.lat();
        double dLat = Math.toRadians(tlat - flat);
        double dLon = Math.toRadians(to.lon() - from.lon());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(flat)) * Math.cos(Math.toRadians(tlat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (float) (R * 2 * Math.asin(Math.sqrt(a)));
    }

    public static double fasterCalcDist(GeoLocation from, GeoLocation to) {
        float flat = from.lat();
        float tlat = to.lat();
        return Math.acos(Math.sin(flat) * Math.sin(tlat)
                + Math.cos(flat) * Math.cos(tlat)
                * Math.cos(to.lon() - from.lon())) * R;
    }

    private void openFiles(File locationsFilename, File edgesFilename, boolean read) throws FileNotFoundException {
        if (edgesOutFile != null || edgesInFile != null)
            throw new IllegalStateException("edgesFile is already opened");
        if (locationsOutFile != null)
            throw new IllegalStateException("locationsFile is already opened");

        // Buffered writer + reader are a lot faster than RandomAccessFile!
        if (read)
            edgesInFile = new BufferedSimpleInputStream(new FileInputStream(edgesFilename), 1 << 14);
        else
            edgesOutFile = new BufferedSimpleOutputStream(new FileOutputStream(edgesFilename), 1 << 14);

        locationsOutFile = new BufferedSimpleOutputStream(new FileOutputStream(locationsFilename), 1 << 14);
        logger.info("locations:" + locationsFilename + ", edges:" + edgesFilename);
    }

    private File newLocationsFilename() throws IOException {
        return File.createTempFile("locations", ".dat", new File("/tmp"));
    }

    private File newEdgesFilename() throws IOException {
        return File.createTempFile("edges", ".dat", new File("/tmp"));
    }

    public File getTmpEdgesFilename() {
        return tmpEdgesFilename;
    }

    public File getTmpLocationsFilename() {
        return tmpLocationsFilename;
    }

    private void printMemInfo() {
        java.lang.management.OperatingSystemMXBean mxbean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunmxbean = (com.sun.management.OperatingSystemMXBean) mxbean;
        long freeMemory = sunmxbean.getFreePhysicalMemorySize();
        long availableMemory = sunmxbean.getTotalPhysicalMemorySize();
        logger.info("starting with free:" + freeMemory / MB + ", available:" + availableMemory / MB + ", rfree:" + Runtime.getRuntime().freeMemory() / MB);
    }

    @Override
    public void close() {
        closeFiles();
    }

    // used from http://wiki.openstreetmap.org/wiki/OpenRouteService#Used_OSM_Tags_for_Routing
    // http://wiki.openstreetmap.org/wiki/Map_Features#Highway
    public static class CarSpeed {

        int defaultSpeed = 50;
        // autobahn
        int motorway = 110;
        int motorway_link = 90;
        // bundesstraße
        int trunk = 90;
        int trunk_link;
        // linking bigger town
        int primary = 70;
        int primary_link = 60;
        // linking smaller towns + villages
        int secondary = 60;
        int secondary_link = 50;
        // streets without middle line separation (too small)
        int tertiary = 55;
        int tertiary_link = 45;
        int unclassified = 50;
        int residential = 40;
        // spielstraße
        int living_street = 10;
        int service = 30;
    }
}
