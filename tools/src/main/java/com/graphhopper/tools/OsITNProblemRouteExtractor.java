package com.graphhopper.tools;

import gnu.trove.TLongCollection;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.procedure.TLongProcedure;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.xml.sax.SAXException;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RelationMember;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.OsItnInputFile;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;

/**
 * This tool is designed to help extract the xml can contributes to a know route
 * with problems argument is the named road for which you wish to extract all
 * referenced nodes and ways. Initial implementation will just extract the
 * directly referenced nodes and ways. A later version should probably also
 * extract all first order connections.
 * 
 * @author stuartadam 
 * 
 */
public class OsITNProblemRouteExtractor {
    OsItnInputFile file;
    private String workingStore;
    private TLongCollection fullWayList = new TLongArrayList(100);
    private TLongCollection fullNodeList = new TLongArrayList(200);
    private TLongCollection otherEndOfWayNodeList = new TLongArrayList(200);
    private TLongCollection roadFidList = new TLongArrayList(200);
    private String workingRoadName;
    protected Set<String> notHighwaySet = new HashSet<String>();

    private abstract class WayNodeProcess implements TLongProcedure {
        protected final long end;
        protected final RoutingElement item;
        protected final long start;

        private WayNodeProcess(long end, RoutingElement item, long start) {
            this.end = end;
            this.item = item;
            this.start = start;
        }

    }

    private abstract class ProcessVisitor<T> {
        abstract void processVisitor(T element) throws XMLStreamException, IOException, ParserConfigurationException, SAXException, TransformerConfigurationException, TransformerException, XPathExpressionException, MismatchedDimensionException, FactoryException, TransformException;
    }

    private abstract class ProcessFileVisitor<T> extends ProcessVisitor<File> {
        protected ProcessVisitor<T> innerProcess;

        void setInnerProcess(ProcessVisitor<T> process) {
            innerProcess = process;
        }
    }

    private ProcessFileVisitor<RoutingElement> fileProcessProcessor = new ProcessFileVisitor<RoutingElement>() {

        @Override
        void processVisitor(File file) throws XMLStreamException, IOException, TransformerConfigurationException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException, MismatchedDimensionException, FactoryException, TransformException {
            OsItnInputFile in = null;
            try {
                in = new OsItnInputFile(file).setWorkerThreads(1).open();
                RoutingElement item;
                while ((item = in.getNext()) != null) {
                    innerProcess.processVisitor(item);
                }
            } finally {
                Helper.close(in);
            }
        }
    };

    private ProcessVisitor<RoutingElement> extractWayIds = new ProcessVisitor<RoutingElement>() {
        @Override
        void processVisitor(RoutingElement item) {
            if (item.isType(OSMElement.WAY)) {
                final Way way = (Way) item;
                if (way.hasTag("name", workingRoadName)) {
                    System.out.println("Way found on " + workingRoadName + " id is " + way.getId());
                    fullWayList.add(way.getId());
                }
            }
            if (item.isType(OSMElement.RELATION)) {
                final Relation relation = (Relation) item;
                // if (!relation.isMetaRelation()
                // && relation.hasTag(OSITNElement.TAG_KEY_TYPE, "route"))
                // prepareWaysWithRelationInfo(relation);
//                System.out.println("\t\tCHECK OUT A RELATION " + relation.getId());
                if (relation.isMetaRelation()) {
                    System.out.println("\t\tADD IT TO my road fids");
                }
                if (relation.hasTag("name", workingRoadName)) {
                    System.out.println("Relation found on " + workingRoadName + " id is " + relation.getId());
                    prepareNameRelation(relation, fullWayList);
                    roadFidList.add(relation.getId());
                }
            }
        }
    };

    private ProcessVisitor<RoutingElement> extractNodeIds = new ProcessVisitor<RoutingElement>() {

        @Override
        void processVisitor(RoutingElement item) {
            if (item.isType(OSMElement.WAY)) {
                final Way way = (Way) item;
                if (item.hasTag("nothighway")) {
                    notHighwaySet.add(item.getTag("nothighway"));
                }
                if (fullWayList.contains(way.getId())) {
                    TLongList nodes = way.getNodes();
                    long startNode = nodes.get(0);
                    long endNode = nodes.get(nodes.size() - 1);
//                    System.out.println("Add start: " + startNode + " end: " + endNode);
                    fullNodeList.add(startNode);
                    fullNodeList.add(endNode);
                }
            }
        }
    };

    private ProcessVisitor<RoutingElement> extractWayIdLinkedToNodes = new ProcessVisitor<RoutingElement>() {
        @Override
        void processVisitor(final RoutingElement item) {
            if (item.isType(OSMElement.WAY)) {
                final Way way = (Way) item;
                TLongList nodes = way.getNodes();
                final long start = nodes.get(0);
                final long end = nodes.get(nodes.size() - 1);
                TLongProcedure addWayIfNodeExists = new WayNodeProcess(end, item, start) {
                    @Override
                    public boolean execute(long testNode) {
                        if (testNode == start || testNode == end) {
                            // ADD THE OTHER END IN TO OUR new collection
                            long otherEnd = testNode == start?end:start;
                            otherEndOfWayNodeList.add(otherEnd);

                            fullWayList.add(item.getId());
                            
                            return false;
                        }
                        return true;
                    }
                };
                origFullNodeList.forEach(addWayIfNodeExists);
            }
        }
    };

    private ProcessVisitor<RoutingElement> extractRelationsAtJunctionOfBothRoads = new ProcessVisitor<RoutingElement>() {
        @Override
        void processVisitor(final RoutingElement item) {
            if (item.isType(OSMElement.RELATION)) {
                final Relation rel = (Relation) item;
                ArrayList<? extends RelationMember> links = rel.getMembers();
                final long start = links.get(0).ref();
                final long end = links.get(links.size() - 1).ref();
                TLongProcedure addRelIfWayExists = new WayNodeProcess(end, item, start) {
                    @Override
                    public boolean execute(long testNode) {
                        if (testNode == start || testNode == end) {
                            relationList.add(rel.getId());
                            return false;
                        }                        
                        return true;
                    }
                };
                fullWayList.forEach(addRelIfWayExists);
            }
        }
    };

    private ProcessVisitor<File> extractProcessor = new ProcessVisitor<File>() {
        void processVisitor(File element) throws XMLStreamException, IOException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException {
            OsItnInputFile itn = new OsItnInputFile(element);
            InputStream bis = itn.getInputStream();
            TLongArrayList fidList = new TLongArrayList(relationList);
            fidList.addAll(fullWayList);
            fidList.addAll(origFullNodeList);
            // ADD IN OUR ADDITIONAL NODE LIST HERE
            fidList.addAll(otherEndOfWayNodeList);
            fidList.addAll(roadFidList);
            
            outputListedFids(fidList, bis);
        };

        private void outputListedFids(TLongArrayList fidList, InputStream bis) throws XMLStreamException, NumberFormatException, IOException {
            boolean output = false;

            BufferedReader bir = new BufferedReader(new InputStreamReader(bis));
            String lastLine = "";
            while (bir.ready()) {
                String line = bir.readLine();

                if (output) {
                    outputWriter.println(line);
                    if (isEndBlock(line)) {
                        output = false;
                        outputWriter.flush();
                    }
                }
                if (!output && line.contains("fid='osgb")) {
                    String idStr = line.substring(line.indexOf("fid='osgb") + 9, line.lastIndexOf('\''));
                    long checkFid = Long.parseLong(idStr);
                    if (fidList.contains(checkFid)) {
                        output = true;
                        outputWriter.println(lastLine);
                        outputWriter.println(line);
                    }
                }
                lastLine = line;
            }
        }

        private boolean isEndBlock(String curLine) {
            boolean endBlock = false;
            switch (curLine) {
            case "</osgb:networkMember>":
            case "</osgb:roadInformationMember>":
            case "</osgb:roadMember>": {
                endBlock = true;
                break;
            }
            }
            return endBlock;
        }
    };

    private String workingLinkRoad;
    private TLongCollection origFullNodeList;
    private TLongCollection origFullWayList;
    private TLongProcedure nodeOutput;
    private TLongProcedure wayOutput;
    private TLongArrayList relationList;
    private TLongProcedure relOutput;
    private PrintWriter outputWriter;

    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        String fileOrDirName = args.get("osmreader.osm", null);
        String namedRoad = args.get("roadName", null);
        String namedLinkRoad = args.get("linkRoadName", null);
        System.out.println("Find junction around " + namedRoad + " and " + namedLinkRoad);
        String outputFileName = args.get("itnoutput", "os-itn-" + namedRoad.replaceAll(" ", "-").toLowerCase() + (null != namedLinkRoad ? "-" + namedLinkRoad.replaceAll(" ", "-").toLowerCase() : "") + ".xml");
        OsITNProblemRouteExtractor extractor = new OsITNProblemRouteExtractor(fileOrDirName, namedRoad, namedLinkRoad);
        extractor.process(outputFileName);
        args.put("reader.implementation", "OSITN");
        args.put("osmreader.osm", outputFileName);
        GraphHopper hopper = new GraphHopper().init(args).importOrLoad();
        FlagEncoder carEncoder = hopper.getEncodingManager().getEncoder("CAR");
        EdgeFilter filter = new DefaultEdgeFilter(carEncoder, false, true);

        GHUtility.printInfo(hopper.getGraph(), 0, Integer.MIN_VALUE, filter);
    }

    public OsITNProblemRouteExtractor(String fileOrDirName, String namedRoad, String namedLinkRoad) {
        workingStore = fileOrDirName;
        workingRoadName = namedRoad;
        workingLinkRoad = namedLinkRoad;
    }

    private void process(String outputFileName) throws TransformerException, ParserConfigurationException, SAXException, XPathExpressionException, XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        prepareOutputMethods();

        File itnFile = new File(workingStore);
        findWaysOnRoad(itnFile);
        findNodesOfRoad(itnFile);

        if (null != workingLinkRoad) {
            findLinkedWayIDs(itnFile);
            findNodesOnBothWays(itnFile);
            origFullNodeList.forEach(nodeOutput);
            findWaysLinkedAtJunctionOfBothRoads(itnFile);
            fullWayList.forEach(wayOutput);
            findRelationsAtJunctionOfBothRoads(itnFile);
            relationList.forEach(relOutput);
        } else {
            fullNodeList.forEach(nodeOutput);
            fullWayList.forEach(wayOutput);
        }

        outputWriter = new PrintWriter(outputFileName);

        outputWriter.println("<?xml version='1.0' encoding='UTF-8'?>" + "<osgb:FeatureCollection " + "xmlns:osgb='http://www.ordnancesurvey.co.uk/xml/namespaces/osgb' " + "xmlns:gml='http://www.opengis.net/gml' " + "xmlns:xlink='http://www.w3.org/1999/xlink' " + "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " + "xsi:schemaLocation='http://www.ordnancesurvey.co.uk/xml/namespaces/osgb http://www.ordnancesurvey.co.uk/xml/schema/v7/OSDNFFeatures.xsd' " + "fid='GDS-58096-1'>"
                + "<gml:description>Ordnance Survey, (c) Crown Copyright. All rights reserved, 2009-07-30</gml:description>" + "<gml:boundedBy><gml:null>unknown</gml:null></gml:boundedBy>" + "<osgb:queryTime>2009-07-30T02:01:07</osgb:queryTime>" + "<osgb:queryExtent>" + "<osgb:Rectangle srsName='osgb:BNG'>" + "<gml:coordinates>291000.000,92000.000 293000.000,94000.000</gml:coordinates>" + "</osgb:Rectangle>" + "</osgb:queryExtent>");
        outputWriter.flush();
        processDirOrFile(itnFile, extractProcessor);
        outputWriter.println("<osgb:boundedBy>" + "<gml:Box srsName='osgb:BNG'>" + "<gml:coordinates>290822.000,91912.000 293199.000,94222.000</gml:coordinates>" + "</gml:Box>" + "</osgb:boundedBy>" + "</osgb:FeatureCollection>");
        outputWriter.flush();
        outputWriter.close();
    }

    private void prepareOutputMethods() {
        nodeOutput = new TLongProcedure() {
            @Override
            public boolean execute(long arg0) {
                System.err.println("node:" + arg0);
                return true;
            }
        };

        wayOutput = new TLongProcedure() {
            @Override
            public boolean execute(long arg0) {
                System.err.println("way:" + arg0);
                return true;
            }
        };

        relOutput = new TLongProcedure() {
            @Override
            public boolean execute(long arg0) {
                System.err.println("rel:" + arg0);
                return true;
            }
        };
    }

    private void findNodesOfRoad(File itnFile) {
        System.err.println("STAGE TWO - findNodesOfRoad");
        fileProcessProcessor.setInnerProcess(extractNodeIds);
        process(itnFile, fileProcessProcessor);
    }

    private void findWaysOnRoad(File itnFile) {
        System.err.println("STAGE ONE - findWaysOnRoad");
        fileProcessProcessor.setInnerProcess(extractWayIds);
        process(itnFile, fileProcessProcessor);
    }

    private void findRelationsAtJunctionOfBothRoads(File itnFile) {
        relationList = new TLongArrayList(30);
        fileProcessProcessor.setInnerProcess(extractRelationsAtJunctionOfBothRoads);
        process(itnFile, fileProcessProcessor);
    }

    private void findWaysLinkedAtJunctionOfBothRoads(File itnFile) {
        fullWayList = new TLongArrayList(30);
        fullNodeList = origFullNodeList;
        fileProcessProcessor.setInnerProcess(extractWayIdLinkedToNodes);
        process(itnFile, fileProcessProcessor);
    }

    private void findNodesOnBothWays(File itnFile) {
        System.err.println("STAGE FOUR - findNodesOnBothWays");
        fileProcessProcessor.setInnerProcess(extractNodeIds);
        process(itnFile, fileProcessProcessor);
        origFullNodeList.retainAll(fullNodeList);
    }

    private void findLinkedWayIDs(File itnFile) {
        System.err.println("STAGE THREE - findLinkedWayIDs");
        origFullNodeList = fullNodeList;
        origFullWayList = fullWayList;
        fullNodeList = new TLongArrayList(200);
        fullWayList = new TLongArrayList(100);
        workingRoadName = workingLinkRoad;
        findWaysOnRoad(itnFile);
    }

    void process(File itnFile, ProcessVisitor<File> processVisitor) {
        try {
            processDirOrFile(itnFile, processVisitor);
        } catch (Exception ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        }
    }

    private void processDirOrFile(File osmFile, ProcessVisitor<File> processVisitor) throws XMLStreamException, IOException, TransformerConfigurationException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException, MismatchedDimensionException, FactoryException, TransformException {
        if (osmFile.isDirectory()) {
            String absolutePath = osmFile.getAbsolutePath();
            String[] list = osmFile.list();
            for (String file : list) {
                File nextFile = new File(absolutePath + File.separator + file);
                processDirOrFile(nextFile, processVisitor);
            }
        } else {
            processSingleFile(osmFile, processVisitor);
        }
    }

    private void processSingleFile(File osmFile, ProcessVisitor<File> processVisitor) throws XMLStreamException, IOException, TransformerConfigurationException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException, MismatchedDimensionException, FactoryException, TransformException {
        processVisitor.processVisitor(osmFile);
    }

    private void prepareNameRelation(Relation relation, TLongCollection wayList) {
        ArrayList<? extends RelationMember> members = relation.getMembers();
        for (RelationMember relationMember : members) {
//            System.out.println("\t Add way member: " + relationMember.ref());
            wayList.add(relationMember.ref());
        }
    }

    private void prepareWaysWithRelationInfo(Relation relation) {
        // TODO Auto-generated method stub

    }
}
