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
package com.graphhopper.json.geo;

import com.graphhopper.json.GHJson;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.change.ChangeGraphHelper;

import java.io.*;

import static com.graphhopper.util.Helper.UTF_CS;

/**
 * Creates JsonFeature out of files and applies them to the graph.
 * Currently not used in GraphHopper itself.
 *
 * @author Peter Karich
 */
public class JsonFeatureConverter {
    private final ChangeGraphHelper changeGraphHelper;
    private final GHJson json;
    private final EncodingManager encodingManager;

    public JsonFeatureConverter(GHJson json, ChangeGraphHelper changeGraphHelper, EncodingManager encodingManager) {
        this.json = json;
        this.changeGraphHelper = changeGraphHelper;
        this.encodingManager = encodingManager;
    }

    /**
     * Easily read GeoJSON files from disc and apply the specified changes to the graph.
     */
    public long applyChanges(String fileOrFolderStr) {
        File fileOrFolder = new File(fileOrFolderStr);
        try {
            if (fileOrFolder.isFile()) {
                return applyChanges(new InputStreamReader(new FileInputStream(fileOrFolder), UTF_CS));
            }

            long sum = 0;
            File[] fList = new File(fileOrFolderStr).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".json");
                }
            });
            for (File f : fList) {
                sum += applyChanges(new InputStreamReader(new FileInputStream(f), UTF_CS));
            }
            return sum;

        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This method applies changes to the graph, specified by the reader.
     *
     * @return number of successfully applied edge changes
     */
    public long applyChanges(Reader reader) {
        // read full file, later support one json feature or collection per line to avoid high mem consumption
        JsonFeatureCollection data = json.fromJson(reader, JsonFeatureCollection.class);
        return changeGraphHelper.applyChanges(encodingManager, data.getFeatures());
    }
}
