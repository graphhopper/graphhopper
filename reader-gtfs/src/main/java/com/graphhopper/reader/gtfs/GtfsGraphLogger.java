/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.reader.gtfs;

import java.awt.Color;

import java.io.FileOutputStream;
import java.io.IOException;

import java.lang.Integer;

import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;


class ProjFuncs {
    static CRSFactory mCsFactory = new CRSFactory();
    static CoordinateTransformFactory mCtFactory = new CoordinateTransformFactory();

    static String EPSG_WGS84 = "EPSG:4326";
    static String EPSG_GOOGLE_EARTH = "EPSG:3857";
    //static String EPSG_TWD67 = "EPSG:3828";

//    static String FUNC_WGS84 = "+proj=longlat +datum=WGS84 +no_defs";
//    static String FUNC_TWD97 = "+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=公尺 +no_defs";
//    static String FUNC_TWD67 = "+proj=tmerc  +towgs84=-752,-358,-179,-.0000011698,.0000018398,.0000009822,.00002329 +lat_0=0 +lon_0=121 +x_0=250000 +y_0=0 +k=0.9999 +ellps=aust_SA  +units=公尺";

    public static ProjCoordinate latlonToGoogleEarthGcs(double lng, double lat) {

        CoordinateReferenceSystem crs1 = mCsFactory.createFromName(EPSG_WGS84);
        CoordinateReferenceSystem crs2 = mCsFactory.createFromName(EPSG_GOOGLE_EARTH);
        CoordinateTransform trans = mCtFactory.createTransform(crs1, crs2);
        ProjCoordinate p1 = new ProjCoordinate();
        ProjCoordinate p2 = new ProjCoordinate();
        p1.x = lng;
        p1.y = lat;
        trans.transform(p1, p2);


//        CoordinateReferenceSystem crs1 = mCsFactory.createFromParameters(EPSG_WGS84, FUNC_WGS84);
//        CoordinateReferenceSystem crs2 = mCsFactory.createFromParameters(EPSG_TWD97, FUNC_TWD97);
//        CoordinateTransform trans = mCtFactory.createTransform(crs1, crs2);
//        ProjCoordinate p1 = new ProjCoordinate();
//        ProjCoordinate p2 = new ProjCoordinate();
//        p1.x = latLng.longitude;
//        p1.y = latLng.latitude;
//        trans.transform(p1, p2);

        return p2;
    }
}


class GtfsGraphLogger {

    public enum NodeLogType {
        OSM_NODE, ENTER_EXIT_PT, BOARD_NODE, ARRIVAL_STOP_TIME_NODE, DEPARTURE_STOP_TIME_NODE, ALIGHT_NODE, BLOCK_TRANSFER_NODE
    }

    public static void main(String[] args) throws Exception {
        final GtfsGraphLogger graphLogger = new GtfsGraphLogger();
        graphLogger.addNode("node1", 0, 0, NodeLogType.DEPARTURE_STOP_TIME_NODE, "");
        graphLogger.addNode("node2", 50, 50, NodeLogType.ARRIVAL_STOP_TIME_NODE, "");
        graphLogger.addEdge("HOP", "edge1", "node1", "node2");
        graphLogger.exportGraphmlToFile("/Users/mathieu.stpierre/Documents/Iterations/January2020/gtfs_graph_logger/gtfsGraph.graphml");
    }

    private DocumentBuilderFactory dbf;
    private DocumentBuilder db;
    private Document dom;
    private Element graphEle;

    class NodeInfo {

        NodeInfo(int xPos, int yPos) {
            this.xPos = xPos;
            this.yPos = yPos;
        }

        public int xPos;
        public int yPos;
    }

    private final Map<String, NodeInfo> insertedNodes = new HashMap<>();
    private int currentTripIndex = 0;
    private Color currentTripColor = new Color((int)(Math.random() * 0x1000000));
    private static Color OSM_NODE_COLOR = new Color(0,0,0);
    private static Color STOP_NODE_COLOR = new Color(200,0,0);
    private static Color NODE_TEXT_COLOR = new Color(255,255, 255);



    private int OSM_NODE_Y_POS = -20;
    private int TRIP_HEIGHT_SPACE = 70;
    private int BOARD_NODE_Y_DISTANCE_FROM_BASE = 10;
    private int TIME_NODE_Y_DISTANCE_FROM_BASE = 40;
    private int ALIGH_NODE_Y_DISTANCE_FROM_BASE = 50;
    private int BLOCK_TRANSFER_NODE_Y_DISTANCE_FROM_BASE = 60;

    private int BOARD_NODE_X_DISTANCE_FROM_CURRENT = 10;
    private int TIME_NODE_X_DISTANCE_FROM_CURRENT = 10;
    private int DEPARTURE_TIME_NODE_X_DISTANCE_INCREMENT = 150;
    private int ARRIVAL_TIME_NODE_X_DISTANCE_INCREMENT = 150;
    private int ALIGHT_NODE_X_DISTANCE_FROM_BASE = 10;

    private int currentXPos = 0;

    private Element appendXmlNode(final Element parentEle, final String nodeName, final String attributes) {
        Element keyEle = dom.createElement(nodeName);
        final String[] attributeList = attributes.split(" ");
        for (String attr : attributeList) {
            String[] attVal = attr.split("=");
            if (attVal.length > 1) {
                keyEle.setAttribute(attVal[0], attVal[1]);
            }
        }
        parentEle.appendChild(keyEle);
        return keyEle;
    }
    
    GtfsGraphLogger() throws ParserConfigurationException {

        try {
            String val = System.getenv("GTFS_GRAPH_LOGGER_Y_SPACE_SCALE");
            
            if (val != null) {
                int spaceYScaleFactor = Integer.parseInt(val);

                OSM_NODE_Y_POS *= spaceYScaleFactor;
                TRIP_HEIGHT_SPACE *= spaceYScaleFactor;
                BOARD_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
                TIME_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
                ALIGH_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
                BLOCK_TRANSFER_NODE_Y_DISTANCE_FROM_BASE *= spaceYScaleFactor;
            }
        }
        catch (Exception e) {
        }

        try {
            String val = System.getenv("GTFS_GRAPH_LOGGER_X_SPACE_SCALE");

            if (val != null) {
                int spaceYScaleFactor = Integer.parseInt(val);

                BOARD_NODE_X_DISTANCE_FROM_CURRENT *= spaceYScaleFactor;
                TIME_NODE_X_DISTANCE_FROM_CURRENT *= spaceYScaleFactor;
                DEPARTURE_TIME_NODE_X_DISTANCE_INCREMENT *= spaceYScaleFactor;
                ARRIVAL_TIME_NODE_X_DISTANCE_INCREMENT *= spaceYScaleFactor;
                ALIGHT_NODE_X_DISTANCE_FROM_BASE *= spaceYScaleFactor;
            }
        }
        catch (Exception e) {
        }
        

        dbf = DocumentBuilderFactory.newInstance();
        db = dbf.newDocumentBuilder();
        dom = db.newDocument();

        Element rootEle = dom.createElement("graphml");
        rootEle.setAttribute("xmlns", "http://graphml.graphdrawing.org/xmlns");
        rootEle.setAttribute("xmlns:java", "http://www.yworks.com/xml/yfiles-common/1.0/java");
        rootEle.setAttribute("xmlns:sys", "http://www.yworks.com/xml/yfiles-common/markup/primitives/2.0");
        rootEle.setAttribute("xmlns:x", "http://www.yworks.com/xml/yfiles-common/markup/2.0");
        rootEle.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootEle.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootEle.setAttribute("xmlns:y", "http://www.yworks.com/xml/graphml");
        rootEle.setAttribute("xmlns:yed", "http://www.yworks.com/xml/yed/3");
        rootEle.setAttribute("xsi:schemaLocation", "http://graphml.graphdrawing.org/xmlns http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd");

        appendXmlNode(rootEle, "key", "attr.name=Description attr.type=string for=graph id=d0");
        appendXmlNode(rootEle, "key", "for=port id=d1 yfiles.type=portgraphics");
        appendXmlNode(rootEle, "key", "for=port id=d2 yfiles.type=portgeometry");
        appendXmlNode(rootEle, "key", "for=port id=d3 yfiles.type=portuserdata");
        appendXmlNode(rootEle, "key", "attr.name=url attr.type=string for=node id=d4");
        appendXmlNode(rootEle, "key", "attr.name=description attr.type=string for=node id=d5");
        appendXmlNode(rootEle, "key", "for=node id=d6 yfiles.type=nodegraphics");
        appendXmlNode(rootEle, "key", "for=graphml id=d7 yfiles.type=resources");
        appendXmlNode(rootEle, "key", "attr.name=url attr.type=string for=edge id=d8");
        appendXmlNode(rootEle, "key", "attr.name=description attr.type=string for=edge id=d9");
        appendXmlNode(rootEle, "key", "for=edge id=d10 yfiles.type=edgegraphics");

        graphEle = dom.createElement("graph");
        graphEle.setAttribute("edgedefault", "directed");
        graphEle.setAttribute("id", "G");

        rootEle.appendChild(graphEle);

        dom.appendChild(rootEle);
    }

    private int getXPos(NodeLogType type) {

        switch (type) {
            case OSM_NODE: return currentXPos;
            case ENTER_EXIT_PT: return currentXPos;
            case BOARD_NODE: return currentXPos + BOARD_NODE_X_DISTANCE_FROM_CURRENT;
            case ARRIVAL_STOP_TIME_NODE: {
                int x = currentXPos + TIME_NODE_X_DISTANCE_FROM_CURRENT;
                currentXPos += DEPARTURE_TIME_NODE_X_DISTANCE_INCREMENT;
                return x;
            }
            case DEPARTURE_STOP_TIME_NODE: {
                int x = currentXPos + TIME_NODE_X_DISTANCE_FROM_CURRENT;
                currentXPos += ARRIVAL_TIME_NODE_X_DISTANCE_INCREMENT;
                return x;
            }
            case ALIGHT_NODE:
                return currentXPos + ALIGHT_NODE_X_DISTANCE_FROM_BASE;
        }

        return currentXPos;
    }

    private int getYPos(NodeLogType type) {

        int yBasePos = currentTripIndex * TRIP_HEIGHT_SPACE;

        switch (type) {
            case OSM_NODE: return OSM_NODE_Y_POS;
            case ENTER_EXIT_PT: return yBasePos;
            case BOARD_NODE: return yBasePos + BOARD_NODE_Y_DISTANCE_FROM_BASE;
            case ARRIVAL_STOP_TIME_NODE:
            case DEPARTURE_STOP_TIME_NODE:
                return yBasePos + TIME_NODE_Y_DISTANCE_FROM_BASE;
            case ALIGHT_NODE: return yBasePos + ALIGH_NODE_Y_DISTANCE_FROM_BASE;
            case BLOCK_TRANSFER_NODE: return yBasePos + BLOCK_TRANSFER_NODE_Y_DISTANCE_FROM_BASE;
            default :
        }

        return yBasePos;
    }

    boolean areColorsSimilar(Color a, Color b, int threshold) {
        return (Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue())) < threshold;
    }



    public void incrementTrip() {
        currentTripIndex++;
        do {
            currentTripColor = new Color((int) (Math.random() * 0x1000000));
        } while (areColorsSimilar(currentTripColor, OSM_NODE_COLOR, 30) || areColorsSimilar(currentTripColor, STOP_NODE_COLOR, 30)
                || areColorsSimilar(currentTripColor, NODE_TEXT_COLOR, 170));
    }

    public void addNode(int id, double x, double y, NodeLogType type, String nodeText) {
        addNode(String.valueOf(id), x, y, type, nodeText);
    }

    public void addNode(String id, double x, double y, NodeLogType type, String nodeText) {

        //Avoid creating duplicate nodes.
        if (insertedNodes.containsKey(id)) {
            return;
        }

        ProjCoordinate coord = ProjFuncs.latlonToGoogleEarthGcs(x, y);
        currentXPos = (int)coord.x;
        int xPos = getXPos(type);
        int yPos = getYPos(type);

        insertedNodes.put(id, new NodeInfo(xPos, yPos));

        Element nodeEle = dom.createElement("node");
        nodeEle.setAttribute("id", id);
        graphEle.appendChild(nodeEle);

        Element dataEle = dom.createElement("data");
        dataEle.setAttribute("key", "d0");
        graphEle.appendChild(dataEle);

        Element dataNodeEle = dom.createElement("data");
        dataNodeEle.setAttribute("key", "d6");
        nodeEle.appendChild(dataNodeEle);

        Element shapeNodeEle = dom.createElement("y:ShapeNode");
        dataNodeEle.appendChild(shapeNodeEle);

        Element geomEle = dom.createElement("y:Geometry");
        geomEle.setAttribute("key", "d6");
        geomEle.setAttribute("height", "40.0");
        geomEle.setAttribute("width", "75.0");
        geomEle.setAttribute("x", String.valueOf(xPos));
        geomEle.setAttribute("y", String.valueOf(yPos));
        shapeNodeEle.appendChild(geomEle);

        Element fillEle = dom.createElement("y:Fill");

        switch (type) {
            case OSM_NODE:
                fillEle.setAttribute("color", String.format("#%02x%02x%02x", OSM_NODE_COLOR.getRed(), OSM_NODE_COLOR.getGreen(), OSM_NODE_COLOR.getBlue())); //Black
                break;
            case ENTER_EXIT_PT:
                fillEle.setAttribute("color", String.format("#%02x%02x%02x", STOP_NODE_COLOR.getRed(), STOP_NODE_COLOR.getGreen(), STOP_NODE_COLOR.getBlue())); //Red
                break;
            default :
                fillEle.setAttribute("color", String.format("#%02x%02x%02x", currentTripColor.getRed(), currentTripColor.getGreen(), currentTripColor.getBlue()));
                break;
        }

        fillEle.setAttribute("transparent", "false");
        shapeNodeEle.appendChild(fillEle);

        Element borderStyleEle = dom.createElement("y:BorderStyle");
        borderStyleEle.setAttribute("color", "#000000");
        borderStyleEle.setAttribute("type", "line");
        borderStyleEle.setAttribute("width", "1.0");
        shapeNodeEle.appendChild(borderStyleEle);

        Element nodeLabelEle = dom.createElement("y:NodeLabel");
        nodeLabelEle.setAttribute("alignment", "center");
        nodeLabelEle.setAttribute("autoSizePolicy", "content");
        nodeLabelEle.setAttribute("fontFamily", "Dialog");
        nodeLabelEle.setAttribute("fontSize", "16");
        nodeLabelEle.setAttribute("fontStyle", "plain");
        nodeLabelEle.setAttribute("hasBackgroundColor", "false");
        nodeLabelEle.setAttribute("hasLineColor", "false");
        nodeLabelEle.setAttribute("hasText", "true");
        nodeLabelEle.setAttribute("height", "4.0");
        nodeLabelEle.setAttribute("modelName", "custom");
        nodeLabelEle.setAttribute("textColor", String.format("#%02x%02x%02x", NODE_TEXT_COLOR.getRed(), NODE_TEXT_COLOR.getGreen(), NODE_TEXT_COLOR.getBlue()));
        nodeLabelEle.setAttribute("visible", "true");
        nodeLabelEle.setAttribute("width", "4.0");
        nodeLabelEle.setAttribute("x", "13.0");
        nodeLabelEle.setAttribute("y", "13.0");

        if (!nodeText.isEmpty()) {
            Text textNode = dom.createTextNode(nodeText);
            nodeLabelEle.appendChild(textNode);
        }

        shapeNodeEle.appendChild(nodeLabelEle);

        Element labelModelEle = dom.createElement("y:LabelModel");
        nodeLabelEle.appendChild(labelModelEle);

        Element smartNodeLabelModel = dom.createElement("y:SmartNodeLabelModel");
        smartNodeLabelModel.setAttribute("distance", "4.0");
        labelModelEle.appendChild(smartNodeLabelModel);

        Element modelParamEle = dom.createElement("y:ModelParameter");
        nodeLabelEle.appendChild(modelParamEle);

        Element smartNodeLabelModelEle = dom.createElement("y:SmartNodeLabelModelParameter");
        smartNodeLabelModelEle.setAttribute("labelRatioX", "0.0");
        smartNodeLabelModelEle.setAttribute("labelRatioY", "0.0");
        smartNodeLabelModelEle.setAttribute("nodeRatioX", "0.0");
        smartNodeLabelModelEle.setAttribute("nodeRatioY", "0.0");
        smartNodeLabelModelEle.setAttribute("offsetX", "0.0");
        smartNodeLabelModelEle.setAttribute("offsetY", "0.0");
        smartNodeLabelModelEle.setAttribute("upX", "0.0");
        smartNodeLabelModelEle.setAttribute("upY", "-1.0");
        modelParamEle.appendChild(smartNodeLabelModelEle);

        Element shapeEle = dom.createElement("y:Shape");
        shapeEle.setAttribute("type", "ellipse");

        shapeNodeEle.appendChild(shapeEle);
    }

    void addEdge(String edgeType, int id, int srcNodeId, int targetNodeId) {
        addEdge(edgeType, String.valueOf(id), String.valueOf(srcNodeId), String.valueOf(targetNodeId));
    }

    void addEdge(String edgeType, String id, String srcNodeId, String targetNodeId) {
        Element edgeEle = appendXmlNode(graphEle, "edge", "id=" + id + " source=" + srcNodeId + " target=" + targetNodeId);
        Element dataEle = appendXmlNode(edgeEle, "data", "key=d10");
        Element polyEdgeEle = appendXmlNode(dataEle, "y:PolyLineEdge", "");

        appendXmlNode(polyEdgeEle, "y:Path", "sx=0.0 sy=0.0 tx=0.0 ty=0.0");
        appendXmlNode(polyEdgeEle, "y:LineStyle", "color=#000000 type=line width=1.0");
        appendXmlNode(polyEdgeEle, "y:Arrows", "source=none target=standard");
        Element edgeLabelEle = appendXmlNode(polyEdgeEle, "y:EdgeLabel", "alignment=center anchorX=27.526667606424326 anchorY=50.05534221010657 configuration=AutoFlippingLabel distance=2.0 fontFamily=Dialog fontSize=12 fontStyle=plain hasBackgroundColor=false hasLineColor=false height=18.1328125 modelName=custom preferredPlacement=anywhere ratio=0.5 textColor=#000000 upX=0.30976697067661274 upY=-0.9508125072157152 visible=true width=28.7734375 x=27.526667606424326 y=32.81443729410911");

        Text textNode = dom.createTextNode(edgeType);
        edgeLabelEle.appendChild(textNode);

        Element labelModelEle = appendXmlNode(edgeLabelEle, "y:LabelModel", "");
        appendXmlNode(labelModelEle, "y:SmartEdgeLabelModel", "autoRotationEnabled=true defaultAngle=0.0 defaultDistance=10.0");

        Element modelParamEle = appendXmlNode(edgeLabelEle, "y:ModelParameter", "");
        appendXmlNode(modelParamEle, "y:SmartEdgeLabelModelParameter", "angle=0.0 distance=30.0 distanceToCenter=true position=right ratio=0.5 segment=0");
        appendXmlNode(edgeLabelEle, "y:PreferredPlacementDescriptor", "angle=0.0 angleOffsetOnRightSide=0 angleReference=absolute angleRotationOnRightSide=co distance=-1.0 frozen=true placement=anywhere side=anywhere sideReference=relative_to_edge_flow");
        appendXmlNode(polyEdgeEle, "y:BendStyle", "smoothed=false");
    }

    void exportGraphmlToFile(final String filename) {

        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.setOutputProperty(OutputKeys.METHOD, "xml");
            tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            //tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
            tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            // send DOM to file
            tr.transform(new DOMSource(dom), new StreamResult(new FileOutputStream(filename)));
        } catch (TransformerException te) {
            System.out.println(te.getMessage());
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        }
    }
}
