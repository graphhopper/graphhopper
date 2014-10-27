package uk.co.ordnancesurvey.gpx.graphhopper;

import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class GraphHopperXMLUnitParserRouteTest extends XMLTestCase {

	private DocumentBuilder builder;
	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperXMLUnitParserRouteTest.class);

	public GraphHopperXMLUnitParserRouteTest(String gpxFileName) {
		try {
			init(gpxFileName);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void init(String gpxFileName)throws ParserConfigurationException, SAXException, IOException {
		XMLUnit.setControlParser("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
		// this next line is strictly not required - if no test parser is
		// explicitly specified then the same factory class will be used for
		// both test and control
		XMLUnit.setTestParser("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");

		XMLUnit.setSAXParserFactory("org.apache.xerces.jaxp.SAXParserFactoryImpl");
		XMLUnit.setTransformerFactory("org.apache.xalan.processor.TransformerFactoryImpl");
		builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		String path = getClass().getResource("sampleGraphHopper.gpx").getPath();
		builder.parse(new FileInputStream(gpxFileName));
	}
}
