/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.storage;

import de.jetsli.graph.util.Helper;
import java.io.File;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class MemoryGraphSafeStorage extends DefaultStorage {

    private final String folder;

    public MemoryGraphSafeStorage(String file, int expectedNodes) {
        super(expectedNodes);
        this.folder = file;
    }

    @Override
    public void createNew() {
        Helper.deleteDir(new File(folder));
        // in order to avoid reallocation allocate enough memory. 
        // edges will be incrementally allocated so it is not that important to match the size up front
        g = new MemoryGraphSafe(folder, osmIdToIndexMap.size(), Math.round(0.8f * osmIdToIndexMap.size()));
    }

    @Override
    public boolean loadExisting() {
        g = new MemoryGraphSafe(folder, 0);
        return g.getNodes() > 0;
    }

    @Override
    public void flush() {
        ((MemoryGraphSafe) g).flush();
        super.flush();
    }
}
