package com.graphhopper.tools;

import gnu.trove.TLongCollection;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.procedure.TLongProcedure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RelationMember;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.OSITNElement;
import com.graphhopper.reader.osgb.OsItnInputFile;
import com.graphhopper.reader.osgb.OsItnMetaData;
import com.graphhopper.util.CmdArgs;
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
	private TLongCollection fullWayList = new TLongArrayList(1000);
	private TLongCollection fullNodeList = new TLongArrayList(1000);
	private String workingRoadName;
	protected Set<String> notHighwaySet = new HashSet<String>();
	
	private abstract class ProcessVisitor<T> {
		abstract void processVisitor(T element) throws XMLStreamException, IOException, ParserConfigurationException, SAXException, TransformerConfigurationException, TransformerException;
	}
	
	private abstract class ProcessFileVisitor<T> extends ProcessVisitor<File> {
		protected ProcessVisitor<T> innerProcess;
		void setInnerProcess(ProcessVisitor<T> process) {
			innerProcess = process;
		}
	}

	private ProcessFileVisitor<RoutingElement> fileProcessProcessor = new ProcessFileVisitor<RoutingElement>() {
		
		@Override
		void processVisitor(File file) throws XMLStreamException, IOException, TransformerConfigurationException, ParserConfigurationException, SAXException, TransformerException {
			OsItnInputFile in = null;
			try {
				System.err.println(file.getAbsolutePath());
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
					fullWayList.add(way.getId());
				}
			}
			if (item.isType(OSMElement.RELATION)) {
				final Relation relation = (Relation) item;
//				if (!relation.isMetaRelation()
//						&& relation.hasTag(OSITNElement.TAG_KEY_TYPE, "route"))
//					prepareWaysWithRelationInfo(relation);

				if (relation.hasTag("name", workingRoadName)) {
					prepareNameRelation(relation, fullWayList);
				}
			}
		}
	};
	
	private ProcessVisitor<RoutingElement> extractNodeIds = new ProcessVisitor<RoutingElement>() {

		@Override
		void processVisitor(RoutingElement item) {
			if (item.isType(OSMElement.WAY)) {
				final Way way = (Way) item;
				if(item.hasTag("nothighway")) {
					notHighwaySet.add(item.getTag("nothighway"));
				}
				if(fullWayList.contains(way.getId())) {
					TLongList nodes = way.getNodes();
					long startNode = nodes.get(0);
					long endNode = nodes.get(nodes.size() -1);
					fullNodeList.add(startNode);
					fullNodeList.add(endNode);
				}
			}
		}
	};
	
	private ProcessVisitor<File> extractProcessor = new ProcessVisitor<File>() {
		void processVisitor(File element) throws XMLStreamException ,IOException, ParserConfigurationException, SAXException, TransformerException {
			SAXParserFactory saxFactory = SAXParserFactory.newInstance();
			saxFactory.setNamespaceAware(true);
		    SAXParser parser = saxFactory.newSAXParser(); 
		    XMLReader reader = parser.getXMLReader(); 

		    TransformerFactory factory = TransformerFactory.newInstance(); 
		    Transformer transformer = factory.newTransformer(); 
		    transformer.setOutputProperty(OutputKeys.INDENT, "no"); 
		    DOMResult result = new DOMResult(); 
		    InputSource is = new InputSource(new FileInputStream(element));
			SAXSource ss = new SAXSource(reader, is);
		    transformer.transform(ss, result); 
		    System.err.println((Document)result.getNode()); 
		};
	};
	private String workingLinkRoad;
	private TLongCollection origFullNodeList;
	private TLongCollection origFullWayList;

	public static void main(String[] strs) throws Exception {
		CmdArgs args = CmdArgs.read(strs);
		String fileOrDirName = args.get("osmreader.osm", null);
		String namedRoad = args.get("roadName", null);
		String namedLinkRoad = args.get("linkRoadName", null);
		OsITNProblemRouteExtractor extractor = new OsITNProblemRouteExtractor(
				fileOrDirName, namedRoad, namedLinkRoad);
		extractor.process();
	}

	public OsITNProblemRouteExtractor(String fileOrDirName, String namedRoad, String namedLinkRoad) {
		workingStore = fileOrDirName;
		workingRoadName = namedRoad;
		workingLinkRoad = namedLinkRoad;
	}

	private void process() throws TransformerException, FileNotFoundException, ParserConfigurationException, SAXException {
		System.err.println("STAGE ONE");
		File itnFile = new File(workingStore);
		fileProcessProcessor.setInnerProcess(extractWayIds);
		process(itnFile, fileProcessProcessor);
		System.err.println("STAGE TWO");
		fileProcessProcessor.setInnerProcess(extractNodeIds);
		process(itnFile, fileProcessProcessor);
		
		TLongProcedure nodeOutput = new TLongProcedure() {
			@Override
			public boolean execute(long arg0) {
				System.err.println("node:" + arg0);
				return true;
			}
		};
		
		TLongProcedure wayOutput = new TLongProcedure() {
			@Override
			public boolean execute(long arg0) {
				System.err.println("node:" + arg0);
				return true;
			}
		};
		
		if(null!=workingLinkRoad) {
			origFullNodeList = fullNodeList;
			origFullWayList = fullWayList;
			fullNodeList =new TLongArrayList(1000); 
			fullWayList =new TLongArrayList(1000);
			workingRoadName = workingLinkRoad;
			System.err.println("STAGE THREE");
			fileProcessProcessor.setInnerProcess(extractWayIds);
			process(itnFile, fileProcessProcessor);
			System.err.println("STAGE FOUR");
			fileProcessProcessor.setInnerProcess(extractNodeIds);
			process(itnFile, fileProcessProcessor);
			origFullNodeList.retainAll(fullNodeList);
			origFullNodeList.forEach(nodeOutput);
			//TODO filter way list as well
		} else {
			fullNodeList.forEach(nodeOutput);
			fullWayList.forEach(wayOutput);
		}
	}

	void process(File itnFile, ProcessVisitor<File> processVisitor) {
		try {
			processDirOrFile(itnFile, processVisitor);
		} catch (Exception ex) {
			throw new RuntimeException("Problem while parsing file", ex);
		}
	}

	private void processDirOrFile(File osmFile, ProcessVisitor<File> processVisitor) throws XMLStreamException,
			IOException, TransformerConfigurationException, ParserConfigurationException, SAXException, TransformerException {
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

	private void processSingleFile(File osmFile, ProcessVisitor<File> processVisitor) throws XMLStreamException,
			IOException, TransformerConfigurationException, ParserConfigurationException, SAXException, TransformerException {
		processVisitor.processVisitor(osmFile);
	}

	private void prepareNameRelation(Relation relation, TLongCollection wayList) {
		ArrayList<? extends RelationMember> members = relation.getMembers();
		for (RelationMember relationMember : members) {
			System.err.println("PREPPING");
			wayList.add(relationMember.ref());
		}
	}

	private void prepareWaysWithRelationInfo(Relation relation) {
		// TODO Auto-generated method stub

	}
}
