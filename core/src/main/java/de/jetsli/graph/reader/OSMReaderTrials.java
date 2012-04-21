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
import de.jetsli.graph.dijkstra.DijkstraPath;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.ui.MiniGraphUI;
import de.jetsli.graph.util.Helper;
import gnu.trove.list.array.TIntArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $ ulimit -v unlimited $ export JAVA_OPTS="-Xmx1000m" mvn -o -e exec:java
 * -Dexec.mainClass="de.jetsli.graph.dijkstra.OSM" -Dexec.args="/media/SAMSUNG/germany.osm"
 *
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReaderTrials implements OSMReader {

    public static void main(String[] args) throws Exception {
        new OSMReaderTrials("/tmp/mmap-graph", 10 * 1000 * 1000) {

            @Override
            public boolean isInBounds(float lat, float lon) {
                // regardless of bounds it takes ~7min (nodes) and 5min (edges) for MMyGraphStorage and other fast storages
                return true;

                // ~germany
                // 90  mio nodes
                // 100 mio edges
                // return true;
                //
                // ~bavaria
                // ? mio nodes
                // ? mio edges
                // return lat > 47.1 && lat < 50.5 && lon > 9 && lon < 13.5;

                //
                // ~bayreuth+bamberg+erlangen+nueremberg
                // 2.7 mio nodes
                // 2.9 mio edges
                // mean edges/node: 2.0752556
                // number of edges per node:
                // 1        2       3       4       5       6       7       8
                // 52206    35005   2232056 267471  32157   1345    164     25
                //return lat > 49.3 && lat < 50 && lon > 10.8 && lon < 11.6;
            }
        }.read(args);
    }
    
    private int maxLocs;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private int locations = 0;
    private int skippedLocations = 0;
    private int nextEdgeIndex = 0;
    private int skippedEdges = 0;
    private Storage storage;
    private String file;
    private TIntArrayList tmpLocs = new TIntArrayList(10);
    private CalcDistance callback = new CalcDistance();
    private HashMap<String, Integer> countMap = new HashMap<String, Integer>(1000);
    private HashMap<String, Integer> highwayMap = new HashMap<String, Integer>(100000);
            
    public void read(String[] args) throws Exception {
        // get osm file via wget -O muenchen.osm "http://api.openstreetmap.org/api/0.6/map?bbox=11.54,48.14,11.543,48.145"
        // or if that does not work for you get them here
        // http://download.geofabrik.de/osm/europe/germany/bayern/
        if (args.length != 1)
            throw new IllegalStateException(".osm file missing");

        // I'm having the osm on an external usb drive serving the osm async to our binary file
        File osmFile = new File(args[0]);
        if (!osmFile.exists())
            throw new IllegalStateException("File does not exist:" + args[0]);

        boolean forceCreate = false;
        boolean loaded = init(forceCreate);
        if (!loaded) {
            logger.info("Start creating graph from " + osmFile);
            writeOsm2Binary(new FileInputStream(osmFile));
        }

        // stats();
        
        new PerfTest(readGraph()).start();
//        new MiniGraphUI(readGraph()).visualize();

//        boolean dijkstraSearchTest = storage instanceof MMyGraphStorage;
        boolean dijkstraSearchTest = false;
        if (dijkstraSearchTest) {
            Graph g = ((MMyGraphStorage) storage).getGraph();
            int locs = g.getLocations() - 1;
            Random ran = new Random();
            for (int i = 0; i < 1000; i++) {
                int from = Math.abs(ran.nextInt(locs));
                int to = Math.abs(ran.nextInt(locs));
                if (from == to)
                    continue;

//            E45, 91056 Erlangen, Germany (49.571453, 10.936175)
//            E45, 90475 Brunn,    Germany (49.44403,  11.229972)
//            path:distance:27.990238 <=> confirmed via google
                logger.info(i + " from " + g.getLatitude(from) + ", " + g.getLongitude(from));
                logger.info(i + " to " + g.getLatitude(to) + ", " + g.getLongitude(to));
                DijkstraPath p = new DijkstraBidirection(g).calcShortestPath(from, to);
                if (p == null)
                    continue;
                logger.info(i + " path:" + p.toString());
                for (int loc = 0; loc < p.locations(); loc++) {
                    int node = p.location(loc);
                    System.out.print(g.getLatitude(node) + ", " + g.getLongitude(node) + "->");
                }
                System.out.println("\n");
            }
        }
        close();
    }
    public OSMReaderTrials(String file, int size) {
        this.file = file;
        maxLocs = size;
    }

    /** 
     * @return the number of already existing nodes
     */
    public boolean init(boolean forceCreateNew) {
        logger.info("starting with " + Helper.getBeanMemInfo());
        try {
//            storage = new LuceneStorage().init();
//            storage = new Neo4JStorage(file).init();
//            storage = new BerkeleyDBStorage(file).init();
            if (file == null)
                storage = new MMyGraphStorage(maxLocs).init(forceCreateNew);
            else
                storage = new MMyGraphStorage(file, maxLocs).init(forceCreateNew);

            return storage.getNodes() > 0;
        } catch (Exception ex) {
            throw new RuntimeException("Problem while initializing storage", ex);
        }
    }

    /**
     * Writes two files from the inputstream which is the standard osm xml file.
     */
    @Override
    public void writeOsm2Binary(InputStream is) {
        if (is == null)
            throw new IllegalStateException("Stream cannot be empty");

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(is, "UTF-8");
            int counter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {
                if (++counter % 1000000 == 0) {
                    logger.info(counter + ", locs:" + locations + " (" + skippedLocations + "), edges:" + nextEdgeIndex
                            + " (" + skippedEdges + "), " + Helper.getMemInfo());
                }

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("node".equals(sReader.getLocalName()))
                            processNode(sReader);
                        else if ("way".equals(sReader.getLocalName()))
                            processWay(sReader);

                        break;
                }
            }
            storage.flush();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
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
            logger.error("cannot get id from xml node:" + sReader.getAttributeValue(null, "id"), ex);
            return;
        }

        try {
            float lat = Float.parseFloat(sReader.getAttributeValue(null, "lat"));
            float lon = Float.parseFloat(sReader.getAttributeValue(null, "lon"));
            if (isInBounds(lat, lon)) {
                // quadTree.put(lat, lon, osmId);
                storage.addNode(osmId, lat, lon);
                locations++;
            } else
                skippedLocations++;
        } catch (Exception ex) {
            logger.error("cannot get handle lon/lat of node:" + sReader.getAttributeValue(null, "lon") + " " + sReader.getAttributeValue(null, "lat"), ex);
        }
    }

    public boolean isInBounds(float lat, float lon) {
        return true;
    }

    public void processWay(XMLStreamReader sReader) throws XMLStreamException {
        tmpLocs.clear();
        boolean isHighway = false;
        for (int tmpE = sReader.nextTag(); tmpE != XMLStreamConstants.END_ELEMENT;
                tmpE = sReader.nextTag()) {
            if (tmpE == XMLStreamConstants.START_ELEMENT) {
                if ("nd".equals(sReader.getLocalName())) {
                    String ref = sReader.getAttributeValue(null, "ref");
                    try {
                        tmpLocs.add(Integer.parseInt(ref));
                    } catch (Exception ex) {
                        logger.error("cannot get ref from way. ref:" + ref, ex);
                    }
                } //                else if ("tag".equals(sReader.getLocalName())
                //                        && "name".equals(sReader.getAttributeValue(null, "k")))
                //                    roadName = sReader.getAttributeValue(null, "v");
                else if ("tag".equals(sReader.getLocalName())) {
                    String someKey = sReader.getAttributeValue(null, "k");
                    if (someKey != null && !someKey.isEmpty()) {
                        Integer val = countMap.put(someKey, 1);
                        if (val != null)
                            countMap.put(someKey, val + 1);

                        if ("highway".equals(someKey)) {
                            isHighway = true;
                            String someValue = sReader.getAttributeValue(null, "v");
                            if (someValue != null && !someValue.isEmpty()) {
                                val = highwayMap.put(someValue, 1);
                                if (val != null)
                                    highwayMap.put(someValue, val + 1);
                            }
                        }
                    }
                }

                sReader.next();
            }
        } // for

        if (isHighway && tmpLocs.size() > 1) {
            int prev = tmpLocs.get(0);
            try {
                for (int index = 1; index < tmpLocs.size(); index++) {
                    int curr = tmpLocs.get(index);
                    if (storage.addEdge(prev, curr, true, callback))
                        nextEdgeIndex++;
                    else
                        skippedEdges++;
                    prev = curr;
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public OSMReaderTrials setMaxLocations(int maxLocs) {
        this.maxLocs = maxLocs;
        return this;
    }

    @Override
    public Graph readGraph() {
        if (storage instanceof MMyGraphStorage)
            return ((MMyGraphStorage) storage).getGraph();

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() {
        try {
            storage.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void stats() {
        logger.info("Stats");

//        printSorted(countMap.entrySet());
//        printSorted(highwayMap.entrySet());
        storage.stats();        
    }

    private void printSorted(Set<Entry<String, Integer>> entrySet) {
        List<Entry<String, Integer>> list = new ArrayList<Entry<String, Integer>>(entrySet);
        Collections.sort(list, new Comparator<Entry<String, Integer>>() {

            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                return o1.getValue() - o2.getValue();
            }
        });
        for (Entry<String, Integer> e : list) {
            logger.info(e.getKey() + "\t -> " + e.getValue());
        }
        logger.info("---");
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
