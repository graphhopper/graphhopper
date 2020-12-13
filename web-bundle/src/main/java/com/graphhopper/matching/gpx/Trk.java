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
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.shapes.GHPoint3D;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Trk {

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<Trkseg> trkseg = new ArrayList<>();
    public String name;

    public List<Observation> getEntries() {
        ArrayList<Observation> gpxEntries = new ArrayList<>();
        for (Trkseg t : trkseg) {
            for (Trkpt trkpt : t.trkpt) {
                gpxEntries.add(new Observation(new GHPoint3D(trkpt.lat, trkpt.lon, trkpt.ele)));
            }
        }
        return gpxEntries;
    }

    public Optional<Date> getStartTime() {
        return trkseg.stream().flatMap(trkseg -> trkseg.trkpt.stream()).findFirst().flatMap(trkpt -> Optional.ofNullable(trkpt.time));
    }

}
