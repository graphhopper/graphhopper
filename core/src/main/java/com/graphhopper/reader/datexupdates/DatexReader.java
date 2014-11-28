package com.graphhopper.reader.datexupdates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.hsqldb.lib.StringInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DatexReader {

	private String curValue;
	private Map<String, String> locSpeedMap;
	private List<LatLongMetaData> list = new ArrayList<>();
	
	private DefaultHandler datexStreamHandler = new DefaultHandler() {
		private boolean speedChars=false;
		
		@Override
		public void startElement(String uri, String localName,
	            String qName, Attributes attributes)
	    throws SAXException {
			switch (qName) {
			case "measurementSiteReference":
				processSiteRef(attributes);
				break;
			case "speed":
				speedChars = true;

			default:
				break;
			}

	    }

		private void processSiteRef(Attributes attributes) {
			curValue=attributes.getValue("id");
		}

		@Override
	    public void endElement(String uri, String localName, String qName)
	    throws SAXException {
			switch (qName) {
			case "speed":
				speedChars = false;
				break;

			default:
				break;
			}
	    }

		@Override
	    public void characters(char ch[], int start, int length)
	    throws SAXException {
			if(speedChars) {
				String str = new String(ch, start, length);
				locSpeedMap.put(curValue, str);
			}
	    }

		
	};
	
	private DefaultHandler datexModelStreamHandler = new DefaultHandler() {
		private boolean lon;
		private boolean lat;
		private String latValue;
		private String lonValue;

		@Override
		public void startElement(String uri, String localName,
	            String qName, Attributes attributes)
	    throws SAXException {
			switch (qName.substring(5)) {
			case "measurementSiteRecord":
				processSiteRef(attributes);
				break;
			case "latitude":
				lat = true;
				break;
			case "longitude":
				lon = true;
			default:
				break;
			}
	    }

		private void processSiteRef(Attributes attributes) {
			curValue=attributes.getValue("id");
		}

		@Override
	    public void endElement(String uri, String localName, String qName)
	    throws SAXException {
			switch (qName.substring(5)) {
			case "locationForDisplay":
				String speed = locSpeedMap.get(curValue);
				LatLongMetaData data = new LatLongMetaData(speed, latValue, lonValue);
				list.add(data);
				break;

			default:
				lat = false;
				lon = false;
				break;
			}
	    }

		@Override
	    public void characters(char ch[], int start, int length)
	    throws SAXException {
			if(lat || lon) {
				String str = new String(ch, start, length);
				if(lat) {
					latValue = str;
				}
				if(lon) {
					lonValue = str;
				}
			}
	    }

		
	};
	private SAXParser saxParser;

	public List<LatLongMetaData> read(String datexModelStream,
			String datexStream) throws ParserConfigurationException, SAXException, IOException  {
		locSpeedMap = new HashMap<>();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(false);
		saxParser = factory.newSAXParser();
		
		readDatexStream(datexStream);
		readDatexModelStream(datexModelStream);
		return list;
	}

	private void readDatexModelStream(String datexModelStream) throws SAXException, IOException {
		saxParser.parse(new StringInputStream(datexModelStream), datexModelStreamHandler);
	}

	private void readDatexStream(String datexStream) throws SAXException, IOException {
		saxParser.parse(new StringInputStream(datexStream), datexStreamHandler);
	}
	
		
	
}
