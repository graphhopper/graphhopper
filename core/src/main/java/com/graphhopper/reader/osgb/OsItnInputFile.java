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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.osgb.dpn.OsDpnWay;
import com.graphhopper.reader.pbf.Sink;

/**
 * A readable OS ITN file.
 * <p/>
 * 
 * @author Stuart Adam
 */
public class OsItnInputFile implements Sink, Closeable {
	private boolean eof;
	private InputStream bis;
	// for xml parsing
	private XMLStreamReader parser;
	// for pbf parsing
	private boolean binary = false;
	private final BlockingQueue<RoutingElement> itemQueue;
	private boolean hasIncomingData;
	private int workerThreads = -1;
	private static final Logger logger = LoggerFactory
			.getLogger(OsItnInputFile.class);
	private String name;

	public OsItnInputFile(File file) throws IOException {
		name = file.getAbsolutePath();
		bis = decode(file);
		itemQueue = new LinkedBlockingQueue<RoutingElement>(50000);
	}

	public OsItnInputFile open() throws XMLStreamException {
		openXMLStream(bis);
		return this;
	}
	
	public InputStream getInputStream() {
		return bis;
	}

	/**
	 * Currently on for pbf format. Default is number of cores.
	 */
	public OsItnInputFile setWorkerThreads(int num) {
		workerThreads = num;
		return this;
	}

	@SuppressWarnings("unchecked")
	private InputStream decode(File file) throws IOException {
		final String name = file.getName();

		InputStream ips = null;
		try {
			ips = new BufferedInputStream(new FileInputStream(file), 50000);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		ips.mark(10);

		// check file header
		byte header[] = new byte[6];
		ips.read(header);

		/*
		 * can parse bz2 directly with additional lib if (header[0] == 'B' &&
		 * header[1] == 'Z') { return new CBZip2InputStream(ips); }
		 */
		if (header[0] == 31 && header[1] == -117) {
			ips.reset();
			return new GZIPInputStream(ips, 50000);
		} else if (header[0] == 0 && header[1] == 0 && header[2] == 0
				&& header[4] == 10 && header[5] == 9
				&& (header[3] == 13 || header[3] == 14)) {
			ips.reset();
			binary = true;
			return ips;
		} else if (header[0] == 'P' && header[1] == 'K') {
			ips.reset();
			ZipInputStream zip = new ZipInputStream(ips);
			zip.getNextEntry();

			return zip;
		} else if (name.endsWith(".gml") || name.endsWith(".xml")) {
			ips.reset();
			return ips;
		} else if (header[0] == 60 && header[1] == 63 && header[3] == 120
				&& header[4] == 109 && header[5] == 108) {
			ips.reset();
			return ips;
		} else if (name.endsWith(".bz2") || name.endsWith(".bzip2")) {
			String clName = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream";
			try {
				Class clazz = Class.forName(clName);
				ips.reset();
				Constructor<InputStream> ctor = clazz.getConstructor(
						InputStream.class, boolean.class);
				return ctor.newInstance(ips, true);
			} catch (Exception e) {
				throw new IllegalArgumentException("Cannot instantiate "
						+ clName, e);
			}
		} else {
			throw new IllegalArgumentException(
					"Input file is not of valid type " + file.getPath());
		}
	}

	private void openXMLStream(InputStream in) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		parser = factory.createXMLStreamReader(bis, "UTF-8");
		int event;
		do {
			event = parser.next();
		} while (event == XMLStreamConstants.COMMENT);

		if (event != XMLStreamConstants.START_ELEMENT
				|| !parser.getLocalName().equalsIgnoreCase("FeatureCollection")) {
			throw new IllegalArgumentException(String.format(
					"File %s not a valid OS ITN stream", name));
		}

		eof = false;
	}

	public RoutingElement getNext() throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
		if (eof)
			throw new IllegalStateException("EOF reached");

		RoutingElement item;
		item = getNextXML();

		if (item != null)
			return item;

		eof = true;
		return null;
	}

	private OSITNElement getNextXML() throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {

		int event = parser.next();
		while (event != XMLStreamConstants.END_DOCUMENT) {
			if (event == XMLStreamConstants.START_ELEMENT) {
				String idStr = parser.getAttributeValue(null, "fid");
				if (null == idStr) {
					idStr = parser.getAttributeValue(
							"http://www.opengis.net/gml/3.2", "id");
				}
				if (idStr != null) {
					String name = parser.getLocalName();
					idStr = idStr.substring(4);
					logger.info(idStr + ":" + name + ":");

					long id;
					try {
						id = Long.parseLong(idStr);
					} catch (NumberFormatException nfe) {
						BigDecimal bd = new BigDecimal(idStr);
						id = bd.longValue();
					}
					logger.info(id + ":" + name + ":");
					switch (name) {
					case "RoadNode":
					case "RouteNode": {
						return OSITNNode.create(id, parser);
					}
					case "RoadLink": {
						return OSITNWay.create(id, parser);
					}
					case "RouteLink": {
						return OsDpnWay.create(id, parser);
					}
					case "RoadRouteInformation": {
						return OSITNRelation.create(id, parser);
					}

					case "Road": {
						return OsItnMetaData.create(id, parser);
					}
					case "RoadLinkInformation": {
					}
					case "RoadNodeInformation": {
					}
					default: {

					}

					}
				}
			}
			event = parser.next();
		}
		parser.close();
		return null;
	}

	public boolean isEOF() {
		return eof;
	}

	@Override
	public void close() throws IOException {
		try {
			if (!binary)
				parser.close();
		} catch (XMLStreamException ex) {
			throw new IOException(ex);
		} finally {
			eof = true;
			bis.close();
		}
	}

	@Override
	public void process(RoutingElement item) {
		try {
			// blocks if full
			itemQueue.put(item);
		} catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		}

		// throw exception if full
		// itemQueue.add(item);
	}

	@Override
	public void complete() {
		hasIncomingData = false;
	}
}
