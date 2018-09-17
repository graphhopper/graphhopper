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

package com.conveyal.gtfs.error;

import java.io.Serializable;

/**
 * Created by landon on 10/14/16.
 */
public class TableInSubdirectoryError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    public final String directory;
    public final Priority priority = Priority.HIGH;

    public TableInSubdirectoryError(String file, String directory) {
        super(file, 0, null);
        this.directory = directory;
    }

    @Override public String getMessage() {
        return String.format("All GTFS files (including %s.txt) should be at root of zipfile, not nested in subdirectory (%s)", file, directory);
    }
}
