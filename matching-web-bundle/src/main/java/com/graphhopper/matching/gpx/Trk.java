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
package com.graphhopper.matching.gpx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.graphhopper.util.GPXEntry;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Trk {

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<Trkseg> trkseg;
    public String name;

    public List<GPXEntry> getEntries() {
        ArrayList<GPXEntry> gpxEntries = new ArrayList<>();
        for (Trkseg t : trkseg) {
            for (Trkpt trkpt : t.trkpt) {
                gpxEntries.add(new GPXEntry(trkpt.lat, trkpt.lon, trkpt.ele, trkpt.time != null ? trkpt.time.getTime() : 0));
            }
        }
        return gpxEntries;
    }

}
