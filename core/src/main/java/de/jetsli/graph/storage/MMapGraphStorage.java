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

/**
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraphStorage extends DefaultStorage {

    private final String file;

    public MMapGraphStorage(String file, int expectedNodes) {
        super(expectedNodes);
        this.file = file;
    }

    @Override
    public boolean loadExisting() {
        g = new MMapGraph(file, -1);
        return getMMapGraph().loadExisting();
    }
    
    private MMapGraph getMMapGraph() {
        return (MMapGraph) g;
    }

    @Override
    public void createNew() {
        g = new MMapGraph(file, osmIdToIndexMap.size());
        // createNew(*true*) to avoid slow down for mmap files (and RAM bottlenecks)
        // but still write to disc at the end!
        getMMapGraph().createNew(true);
    }
    
    @Override public void stats() {
        getMMapGraph().stats(true);
    }

    @Override
    public void flush() {
        getMMapGraph().flush();
        super.flush();
    }
}
