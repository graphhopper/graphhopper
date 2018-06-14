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
package com.graphhopper.matching;

import com.graphhopper.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A simple utility method to import from and export to GPX files.
 * <p>
 *
 * @author Peter Karich
 */
public class GPXFile {

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String DATE_FORMAT_Z = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String DATE_FORMAT_Z_MS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private final List<GPXEntry> entries;
    private boolean includeElevation = false;
    private InstructionList instructions;

    public GPXFile() {
        entries = new ArrayList<>();
    }

    public GPXFile(MatchResult mr, InstructionList il) {
        this.instructions = il;
        this.entries = new ArrayList<>(mr.getEdgeMatches().size());
        // TODO fetch time from GPX or from calculated route?
        long time = 0;
        for (int emIndex = 0; emIndex < mr.getEdgeMatches().size(); emIndex++) {
            EdgeMatch em = mr.getEdgeMatches().get(emIndex);
            PointList pl = em.getEdgeState().fetchWayGeometry(emIndex == 0 ? 3 : 2);
            if (pl.is3D()) {
                includeElevation = true;
            }
            for (int i = 0; i < pl.size(); i++) {
                if (pl.is3D()) {
                    entries.add(new GPXEntry(pl.getLatitude(i), pl.getLongitude(i), pl.getElevation(i), time));
                } else {
                    entries.add(new GPXEntry(pl.getLatitude(i), pl.getLongitude(i), time));
                }
            }
        }
    }

    public List<GPXEntry> getEntries() {
        return entries;
    }

    public GPXFile doImport(String fileStr) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringElementContentWhitespace(true);
            return doImport(factory.newDocumentBuilder().parse(new FileInputStream(fileStr)), 20);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method creates a GPXFile object filled with lat,lon values from the
     * xml inputstream is.
     *
     * @param doc the GPX XML document
     * @param defaultSpeed if no time element is found the time value will be
     */
    public GPXFile doImport(Document doc, double defaultSpeed) {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
        SimpleDateFormat formatterZ = new SimpleDateFormat(DATE_FORMAT_Z);
        SimpleDateFormat formatterZMS = new SimpleDateFormat(DATE_FORMAT_Z_MS);
        DistanceCalc distCalc = Helper.DIST_PLANE;
        try {
            NodeList nl = doc.getElementsByTagName("trkpt");
            double prevLat = 0, prevLon = 0;
            long prevMillis = 0;
            for (int index = 0; index < nl.getLength(); index++) {
                Node n = nl.item(index);
                if (!(n instanceof Element)) {
                    continue;
                }

                Element e = (Element) n;
                double lat = Double.parseDouble(e.getAttribute("lat"));
                double lon = Double.parseDouble(e.getAttribute("lon"));
                NodeList timeNodes = e.getElementsByTagName("time");
                long millis = prevMillis;
                if (timeNodes.getLength() == 0) {
                    if (index > 0) {
                        millis += Math.round(distCalc.calcDist(prevLat, prevLon, lat, lon) * 3600 / defaultSpeed);
                    }

                } else {
                    String text = timeNodes.item(0).getTextContent();
                    if (text.contains("Z")) {
                        try {
                            // Try whole second matching
                            millis = formatterZ.parse(text).getTime();
                        } catch (ParseException ex) {
                            // Error: try looking at milliseconds
                            millis = formatterZMS.parse(text).getTime();
                        }
                    } else {
                        millis = formatter.parse(revertTZHack(text)).getTime();
                    }
                }

                NodeList eleNodes = e.getElementsByTagName("ele");
                if (eleNodes.getLength() == 0) {
                    entries.add(new GPXEntry(lat, lon, millis));
                } else {
                    double ele = Double.parseDouble(eleNodes.item(0).getTextContent());
                    entries.add(new GPXEntry(lat, lon, ele, millis));
                }
                prevLat = lat;
                prevLon = lon;
                prevMillis = millis;
            }
            if (entries.size() == 0) {
                throw new ParseException("No trackpoints found in GPX file", 0);
            }
            return this;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Hack to parse time in Java and convert +0100 into +01:00
     */
    private static String revertTZHack(String str) {
        return str.substring(0, str.length() - 3) + str.substring(str.length() - 2);
    }

    @Override
    public String toString() {
        return "entries " + entries.size() + ", " + entries;
    }

    // TODO DUPLICATE CODE from GraphHopper InstructionList!
    //
    private String createString() {
        long startTimeMillis = 0;
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT_Z);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        DecimalFormat decimalFormat = new DecimalFormat("#");
        decimalFormat.setMinimumFractionDigits(1);
        decimalFormat.setMaximumFractionDigits(6);
        decimalFormat.setMinimumIntegerDigits(1);
        decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));

        String header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>"
                + "<gpx xmlns='http://www.topografix.com/GPX/1/1' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                + " creator='Graphhopper MapMatching " + Constants.VERSION + "' version='1.1'"
                // This xmlns:gh acts only as ID, no valid URL necessary.
                // Use a separate namespace for custom extensions to make basecamp happy.
                + " xmlns:gh='https://graphhopper.com/public/schema/gpx/1.1'>"
                + "\n<metadata>"
                + "<copyright author=\"OpenStreetMap contributors\"/>"
                + "<link href='http://graphhopper.com'>"
                + "<text>GraphHopper GPX</text>"
                + "</link>"
                + "<time>" + formatter.format(startTimeMillis) + "</time>"
                + "</metadata>";
        StringBuilder gpxOutput = new StringBuilder(header);
        gpxOutput.append("\n<trk><name>").append("GraphHopper MapMatching").append("</name>");

        if (instructions != null && !instructions.isEmpty()) {
            gpxOutput.append("\n<rte>");
            Instruction nextInstr = null;
            for (Instruction currInstr : instructions) {
                if (null != nextInstr) {
                    instructions.createRteptBlock(gpxOutput, nextInstr, currInstr, decimalFormat);
                }

                nextInstr = currInstr;
            }
            instructions.createRteptBlock(gpxOutput, nextInstr, null, decimalFormat);
            gpxOutput.append("\n</rte>");
        }

        gpxOutput.append("<trkseg>");
        for (GPXEntry entry : entries) {
            gpxOutput.append("\n<trkpt lat='").append(Helper.round6(entry.getLat()));
            gpxOutput.append("' lon='").append(Helper.round6(entry.getLon())).append("'>");
            if (includeElevation) {
                gpxOutput.append("<ele>").append(Helper.round2(entry.getEle())).append("</ele>");
            }
            gpxOutput.append("<time>").append(formatter.format(startTimeMillis + entry.getTime())).append("</time>");
            gpxOutput.append("</trkpt>");
        }
        gpxOutput.append("</trkseg>");
        gpxOutput.append("</trk>");

        // we could now use 'wpt' for via points
        gpxOutput.append("</gpx>");
        return gpxOutput.toString().replaceAll("\\'", "\"");
    }

    public void doExport(String gpxFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(gpxFile))) {
            writer.append(createString());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
