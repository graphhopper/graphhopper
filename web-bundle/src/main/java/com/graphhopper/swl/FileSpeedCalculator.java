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

package com.graphhopper.swl;

import com.csvreader.CsvReader;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class FileSpeedCalculator implements SpeedCalculator {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FileSpeedCalculator.class);

    private final SpeedCalculator delegateTravelTimeCalculator;

    private Map<Integer, short[]> linkTravelTimes;

    /**
     *  The class behaves differently, depending on the value of the parameter.
     *
     *  If the parameter is a local path, then the file at that path is read into `linkTravelTimes`.
     *
     *  If the parameter is a GCS path, than the contents of the file (let's call it a "meta" file) is treated as a
     *  path to a congestion file on GCS. The contents of the "meta" file is monitored, and whenever it is updated,
     *  `linkTravelTimes` is updated with the contents of the new congestion file.
     *
     * @param path Either:
     *             - path to the local file with congestion data.
     *             - path on GCS to a text file, containing the path on GCS to a file with congestion data.
     */
    public FileSpeedCalculator(String path) {
        linkTravelTimes = readTravelTimes(new File(path));
        delegateTravelTimeCalculator = new DefaultSpeedCalculator();
    }

    @Override
    public double getSpeed(EdgeIteratorState edgeState, boolean reverse, int currentTimeSeconds, FlagEncoder encoder) {
        if (linkTravelTimes != null) {
            short[] speeds = linkTravelTimes.get(EdgeKeys.getEdgeKey(edgeState));
            if (speeds != null) {
                int timebinIndex = (currentTimeSeconds / (60 * 15)) % (24 * 4);
                double speedms = speeds[timebinIndex] / 3.6;
                return speedms;
            }
        }
        return delegateTravelTimeCalculator.getSpeed(edgeState, reverse, currentTimeSeconds, encoder);
    }

    private static Map<Integer, short[]> readTravelTimes(File file) {
        Map<Integer, short[]> res = new HashMap<>();
        LOG.warn("Processing {}", file.toString());
        try (InputStream is = new FileInputStream(file)) {
            CsvReader reader = new CsvReader(is, ',', Charset.forName("UTF-8"));
            reader.readHeaders();
            while (reader.readRecord()) {
                int edgeId = Integer.parseInt(reader.get("edgeId"));
                int[] speeds = IntStream.range(0, 24).mapToObj(Integer::toString).flatMap(hour -> {
                    try {
                        return Stream.of(Short.parseShort(reader.get(hour + "h_1")),
                                Short.parseShort(reader.get(hour + "h_2")), Short.parseShort(reader.get(hour + "h_3")),
                                Short.parseShort(reader.get(hour + "h_4")));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).mapToInt(v -> v).toArray();
                // TODO(sindelar): temporary hack to decrease the memory footprint for congestion
                short shortSpeeds[] = new short[speeds.length];
                for (int i = 0; i < speeds.length; i++) {
                    shortSpeeds[i] = (short) speeds[i];
                }
                res.put(edgeId, shortSpeeds);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Exception while loading travel times.");
        }
        LOG.warn("Done.");

        return res;
    }

}
