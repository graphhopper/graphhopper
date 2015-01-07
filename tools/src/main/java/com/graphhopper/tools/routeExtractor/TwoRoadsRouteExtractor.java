package com.graphhopper.tools.routeExtractor;

import gnu.trove.list.array.TLongArrayList;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.xml.sax.SAXException;

import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.Way;

public class TwoRoadsRouteExtractor extends AbstractProblemRouteExtractor {
    protected String workingRoadName;
    protected String workingLinkRoad;
    public TwoRoadsRouteExtractor(String fileOrDirName, String namedRoad, String namedLinkRoad) {
        super(fileOrDirName);
        workingRoadName = namedRoad;
        workingLinkRoad = namedLinkRoad;
    }
    public void process(final String outputFileName) throws TransformerException, ParserConfigurationException, SAXException, XPathExpressionException, XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        prepareOutputMethods();

        final File itnFile = new File(workingStore);
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

    private void findWaysOnRoad(final File itnFile) {
        System.err.println("STAGE ONE - findWaysOnRoad");
        fileProcessProcessor.setInnerProcess(extractWayIds);
        process(itnFile, fileProcessProcessor);
    }
    private void findNodesOfRoad(final File itnFile) {
        System.err.println("STAGE TWO - findNodesOfRoad");
        fileProcessProcessor.setInnerProcess(extractNodeIds);
        process(itnFile, fileProcessProcessor);
    }
    private final ProcessVisitor<RoutingElement> extractWayIds = new ProcessVisitor<RoutingElement>() {
        @Override
        void processVisitor(final RoutingElement item) {
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
                // System.out.println("\t\tCHECK OUT A RELATION " +
                // relation.getId());
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
    private void findLinkedWayIDs(final File itnFile) {
        System.err.println("STAGE THREE - findLinkedWayIDs");
        origFullNodeList = fullNodeList;
        origFullWayList = fullWayList;
        fullNodeList = new TLongArrayList(200);
        fullWayList = new TLongArrayList(100);
        workingRoadName = workingLinkRoad;
        findWaysOnRoad(itnFile);
    }


}
