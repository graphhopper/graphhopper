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
package com.graphhopper.gpx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.graphhopper.util.GPXEntry;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Trk {

    @JacksonXmlElementWrapper(localName="trkseg")
    @JsonDeserialize(contentAs = Trkpnt.class)
    private List<Trkpnt> trkpt;

    public List<GPXEntry> getEntries() {
        ArrayList<GPXEntry> gpxEntries = new ArrayList<>();
        for (Trkpnt trkpnt : trkpt) {
            gpxEntries.add(new GPXEntry(trkpnt.lat, trkpnt.lon, trkpnt.ele, trkpnt.time != null ? trkpnt.time.getTime() : 0));
        }
        return gpxEntries;
    }

    public static Trk doImport(String fileStr) {
        try (InputStream is = new FileInputStream(fileStr)) {
            XmlMapper xmlMapper = new XmlMapper();
            XMLStreamReader xsr = XMLInputFactory.newFactory().createXMLStreamReader(is);
            Gpx gpx = xmlMapper.readValue(xsr, Gpx.class);
            return gpx.trk;
        } catch (IOException | XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

}
