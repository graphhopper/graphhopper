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
package com.graphhopper.reader;

import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * @author Peter Karich
 */
public interface DataReader {
    DataReader setFile(File file);

    DataReader setElevationProvider(ElevationProvider ep);

    DataReader setWorkerThreads(int workerThreads);

    DataReader setWayPointMaxDistance(double wayPointMaxDistance);

    DataReader setSmoothElevation(boolean smoothElevation);

    /**
     * This method triggers reading the underlying data to create a graph
     */
    void readGraph() throws IOException;

    /**
     * This method returns the date of the most recent change for the underlying data or null if not
     * found.
     */
    Date getDataDate();
}
