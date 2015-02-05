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
package com.graphhopper.matching;

import com.graphhopper.routing.Path;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import java.io.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A simple utility method to import from and export to GPX files.
 * <p>
 * @author Peter Karich
 */
public class GPXFile {

    static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    static final String DATE_FORMAT_Z = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    static final String DATE_FORMAT_Z_MS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private final List<GPXEntry> entries;
    private final boolean includeElevation = false;

    public GPXFile() {
        entries = new ArrayList<GPXEntry>();
    }

    public GPXFile(List<GPXEntry> entries) {
        this.entries = entries;
    }

    public GPXFile(MatchResult mr) {
        entries = new ArrayList<GPXEntry>(mr.getEdgeMatches().size());
        // TODO fetch time from GPX or from calculated route?
        long time = 0;
        for (int emIndex = 0; emIndex < mr.getEdgeMatches().size(); emIndex++) {
            EdgeMatch em = mr.getEdgeMatches().get(emIndex);
            PointList pl = em.getEdgeState().fetchWayGeometry(emIndex == 0 ? 3 : 2);
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
            return doImport(new FileInputStream(fileStr));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public GPXFile doImport(InputStream is) {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
        SimpleDateFormat formatterZ = new SimpleDateFormat(DATE_FORMAT_Z);
        SimpleDateFormat formatterZMS = new SimpleDateFormat(DATE_FORMAT_Z_MS);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringElementContentWhitespace(true);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            NodeList nl = doc.getElementsByTagName("trkpt");
            for (int index = 0; index < nl.getLength(); index++) {
                Node n = nl.item(index);
                if (!(n instanceof Element)) {
                    continue;
                }

                Element e = (Element) n;
                double lat = Double.parseDouble(e.getAttribute("lat"));
                double lon = Double.parseDouble(e.getAttribute("lon"));
                NodeList timeNodes = e.getElementsByTagName("time");
                if (timeNodes.getLength() == 0) {
                    throw new IllegalStateException("GPX without time is illegal");
                }

                String text = timeNodes.item(0).getTextContent();
                long millis;
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

                NodeList eleNodes = e.getElementsByTagName("ele");
                if (eleNodes.getLength() == 0) {
                    entries.add(new GPXEntry(lat, lon, millis));
                } else {
                    double ele = Double.parseDouble(eleNodes.item(0).getTextContent());
                    entries.add(new GPXEntry(lat, lon, ele, millis));
                }
            }
            return this;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Hack to parse time in Java and convert +0100 into +01:00
     */
    private static String revertTZHack(String str) {
        return str.substring(0, str.length() - 3) + str.substring(str.length() - 2);
    }

    // TODO DUPLICATE CODE from GraphHopper InstructionList!
    /**
     * Hack to form valid timezone ala +01:00 instead +0100
     */
    private static String tzHack(String str) {
        return str.substring(0, str.length() - 2) + ":" + str.substring(str.length() - 2);
    }

    @Override
    public String toString() {
        return "entries " + entries.size() + ", " + entries;
    }

    public String createString() {
        long startTimeMillis = 0;
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
        String header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>"
                + "<gpx xmlns='http://www.topografix.com/GPX/1/1' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                + " creator='Graphhopper' version='1.1'"
                // This xmlns:gh acts only as ID, no valid URL necessary.
                // Use a separate namespace for custom extensions to make basecamp happy.
                + " xmlns:gh='https://graphhopper.com/public/schema/gpx/1.1'>"
                + "\n<metadata>"
                + "<copyright author=\"OpenStreetMap contributors\"/>"
                + "<link href='http://graphhopper.com'>"
                + "<text>GraphHopper GPX</text>"
                + "</link>"
                + "<time>" + tzHack(formatter.format(startTimeMillis)) + "</time>"
                + "</metadata>";
        StringBuilder track = new StringBuilder(header);
        track.append("\n<trk><name>").append("GraphHopper MapMatching").append("</name>");

        track.append("<trkseg>");
        for (GPXEntry entry : entries) {
            track.append("\n<trkpt lat='").append(Helper.round6(entry.getLat()));
            track.append("' lon='").append(Helper.round6(entry.getLon())).append("'>");
            if (includeElevation) {
                track.append("<ele>").append(Helper.round2(entry.getEle())).append("</ele>");
            }
            track.append("<time>").append(tzHack(formatter.format(startTimeMillis + entry.getMillis()))).append("</time>");
            track.append("</trkpt>");
        }
        track.append("</trkseg>");
        track.append("</trk>");

        // we could now use 'wpt' for via points
        track.append("</gpx>");
        return track.toString().replaceAll("\\'", "\"");
    }

    public GPXFile doExport(String gpxFile) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(gpxFile));
            writer.append(createString());
            return this;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            Helper.close(writer);
        }
    }

    public static void write(Path path, String gpxFile, Translation translation) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(gpxFile));
            writer.append(path.calcInstructions(translation).createGPX());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            Helper.close(writer);
        }
    }
}
