/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;

import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.graphhopper.reader.osgb.OsItnReader;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.ExtendedStorage;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;

/**
 *
 * @author Peter Karich
 */
public class OsItnReaderTest extends DefaultHandler {
    private static final InputStream COMPLEX_ITN_EXAMPLE = OsItnReader.class.getResourceAsStream("os-itn-sample.xml");
	private EncodingManager encodingManager = new EncodingManager("CAR");
    private EdgeFilter carOutEdges = new DefaultEdgeFilter(encodingManager.getEncoder("CAR"), false, true);
	private Set<String> themes = new HashSet<>();
	private boolean inTheme;

    @Test
    public void testReadSimpleCrossRoads() throws IOException
    {
    	boolean turnRestrictionsImport=false;
		boolean is3D=false;
		String directory = "/tmp";
		GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager,
                is3D, turnRestrictionsImport ? new TurnCostStorage() : new ExtendedStorage.NoExtendedStorage());
        
        OsItnReader osItnReader = new OsItnReader(graph);
        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-crossroad.xml");
        osItnReader.setOSMFile(file);
        osItnReader.setEncodingManager(new EncodingManager("CAR,FOOT"));
        osItnReader.readGraph();
        assertEquals(5, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
    }
    
    @Test
    public void testReadSimpleCrossRoadsWithTurnRestriction() throws IOException
    {
    	boolean turnRestrictionsImport=false;
		boolean is3D=false;
		String directory = "/tmp";
		GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager,
                is3D, turnRestrictionsImport ? new TurnCostStorage() : new ExtendedStorage.NoExtendedStorage());
        
        OsItnReader osItnReader = new OsItnReader(graph);
        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-restricted-crossroad.xml");
        osItnReader.setOSMFile(file);
        osItnReader.setEncodingManager(new EncodingManager("CAR,FOOT"));
        osItnReader.readGraph();
        assertEquals(5, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
    }
    
    @Test
    @Ignore ("don't actually know the details on this route yet as it is a real life example file")
    public void testReadSample() throws IOException
    {
    	boolean turnRestrictionsImport=false;
		boolean is3D=false;
		String directory = "/tmp";
		GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager,
                is3D, turnRestrictionsImport ? new TurnCostStorage() : new ExtendedStorage.NoExtendedStorage());
        
        OsItnReader osItnReader = new OsItnReader(graph);
        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-sample.xml");
        osItnReader.setOSMFile(file);
        osItnReader.setEncodingManager(new EncodingManager("CAR,FOOT"));
        osItnReader.readGraph();
        assertEquals(5, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
    }
    
    
    @Test 
    @Ignore
    public void testXMLThemes() throws XMLStreamException, ParserConfigurationException, SAXException, IOException {
    	SAXParser newSAXParser = SAXParserFactory.newInstance().newSAXParser();
    	newSAXParser.parse(COMPLEX_ITN_EXAMPLE, this);
    	for (String theme : themes) {
			System.out.println("THEME:" + theme);
		}
    }
    
    
    @Override
    public void startElement(String uri, String localName, String qName,
    		Attributes attributes) throws SAXException {
    	int length = attributes.getLength();
//    	for (int i = 0; i < length; i++) {
//			String attrib = attributes.getValue(i);
//			System.out.println("ATTRIBUTE:" + attrib);
//		}
    	if("osgb:theme".equals(qName)) {
    		
    		inTheme = true;
    	}
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
    	if("osgb:theme".equals(qName)) {
    		inTheme = false;
    	}
    	
    }
    
    
    @Override
    public void characters(char[] ch, int start, int length)
    		throws SAXException {
    	if(inTheme) {
    		String nodeData = new String(ch, start, length);
    		themes.add(nodeData);
    	}
    }
}
