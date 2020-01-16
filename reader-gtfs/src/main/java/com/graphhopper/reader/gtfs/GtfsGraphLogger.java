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

import java.io.FileOutputStream;
import java.io.IOException;

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

class GtfsGraphLogger {

    public static void main(String[] args) throws Exception {
        final GtfsGraphLogger graphLogger = new GtfsGraphLogger();
        graphLogger.addNode("node1", 0, 0);
        graphLogger.addNode("node2", 50, 50);
        graphLogger.addEdge("HOP", "edge1", "node1", "node2");
        graphLogger.exportGraphmlToFile("/Users/mathieu.stpierre/Documents/Iterations/January2020/gtfs_graph_logger/gtfsGraph.graphml");
    }

    private DocumentBuilderFactory dbf;
    private DocumentBuilder db;
    private Document dom;
    private Element graphEle;

    private Element addNode(final Element parentEle, final String nodeName, final String attributes) {
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

        addNode(rootEle, "key", "attr.name=Description attr.type=string for=graph id=d0");
        addNode(rootEle, "key", "for=port id=d1 yfiles.type=portgraphics");
        addNode(rootEle, "key", "for=port id=d2 yfiles.type=portgeometry");
        addNode(rootEle, "key", "for=port id=d3 yfiles.type=portuserdata");
        addNode(rootEle, "key", "attr.name=url attr.type=string for=node id=d4");
        addNode(rootEle, "key", "attr.name=description attr.type=string for=node id=d5");
        addNode(rootEle, "key", "for=node id=d6 yfiles.type=nodegraphics");
        addNode(rootEle, "key", "for=graphml id=d7 yfiles.type=resources");
        addNode(rootEle, "key", "attr.name=url attr.type=string for=edge id=d8");
        addNode(rootEle, "key", "attr.name=description attr.type=string for=edge id=d9");
        addNode(rootEle, "key", "for=edge id=d10 yfiles.type=edgegraphics");

        graphEle = dom.createElement("graph");
        graphEle.setAttribute("edgedefault", "directed");
        graphEle.setAttribute("id", "G");

        rootEle.appendChild(graphEle);

        dom.appendChild(rootEle);
    }

    void addNode(String id, double x, double y) {
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
        geomEle.setAttribute("height", "30.0");
        geomEle.setAttribute("width", "30.0");
        geomEle.setAttribute("x", Double.valueOf(x).toString());
        geomEle.setAttribute("y", Double.valueOf(y).toString());
        shapeNodeEle.appendChild(geomEle);

        Element fillEle = dom.createElement("y:Fill");
        fillEle.setAttribute("color", "#CC99FF");
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
        nodeLabelEle.setAttribute("fontSize", "12");
        nodeLabelEle.setAttribute("fontStyle", "plain");
        nodeLabelEle.setAttribute("hasBackgroundColor", "false");
        nodeLabelEle.setAttribute("hasLineColor", "false");
        nodeLabelEle.setAttribute("hasText", "false");
        nodeLabelEle.setAttribute("height", "4.0");
        nodeLabelEle.setAttribute("modelName", "custom");
        nodeLabelEle.setAttribute("textColor", "#000000");
        nodeLabelEle.setAttribute("visible", "true");
        nodeLabelEle.setAttribute("width", "4.0");
        nodeLabelEle.setAttribute("x", "13.0");
        nodeLabelEle.setAttribute("y", "13.0");
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

    void addEdge(String edgeType, String id, String srcNodeId, String targetNodeId) {
        Element edgeEle = addNode(graphEle, "edge", "id=" + id + " source=" + srcNodeId + " target=" + targetNodeId);
        Element dataEle = addNode(edgeEle, "data", "key=d10");
        Element polyEdgeEle = addNode(dataEle, "y:PolyLineEdge", "");

        addNode(polyEdgeEle, "y:Path", "sx=0.0 sy=0.0 tx=0.0 ty=0.0");
        addNode(polyEdgeEle, "y:LineStyle", "color=#000000 type=line width=1.0");
        addNode(polyEdgeEle, "y:Arrows", "source=none target=standard");
        Element edgeLabelEle = addNode(polyEdgeEle, "y:EdgeLabel", "alignment=center anchorX=27.526667606424326 anchorY=50.05534221010657 configuration=AutoFlippingLabel distance=2.0 fontFamily=Dialog fontSize=12 fontStyle=plain hasBackgroundColor=false hasLineColor=false height=18.1328125 modelName=custom preferredPlacement=anywhere ratio=0.5 textColor=#000000 upX=0.30976697067661274 upY=-0.9508125072157152 visible=true width=28.7734375 x=27.526667606424326 y=32.81443729410911");

        Text textNode = dom.createTextNode(edgeType);
        edgeLabelEle.appendChild(textNode);

        Element labelModelEle = addNode(edgeLabelEle, "y:LabelModel", "");
        addNode(labelModelEle, "y:SmartEdgeLabelModel", "autoRotationEnabled=true defaultAngle=0.0 defaultDistance=10.0");

        Element modelParamEle = addNode(edgeLabelEle, "y:ModelParameter", "");
        addNode(modelParamEle, "y:SmartEdgeLabelModelParameter", "angle=0.0 distance=30.0 distanceToCenter=true position=right ratio=0.5 segment=0");
        addNode(edgeLabelEle, "y:PreferredPlacementDescriptor", "angle=0.0 angleOffsetOnRightSide=0 angleReference=absolute angleRotationOnRightSide=co distance=-1.0 frozen=true placement=anywhere side=anywhere sideReference=relative_to_edge_flow");
        addNode(polyEdgeEle, "y:BendStyle", "smoothed=false");
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
