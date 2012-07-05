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
package de.jetsli.compare.tinkergraph;

import de.jetsli.graph.storage.DefaultStorage;
import de.jetsli.graph.util.Helper;
import java.io.File;

/**
 * @author Peter Karich
 */
public class TinkerStorage extends DefaultStorage {

    String dir;

    public TinkerStorage(String storeDir, int expectedNodes) {
        super(expectedNodes);
        dir = storeDir;
        g = new TinkerGraphImpl(storeDir);
    }

    @Override
    public boolean loadExisting() {
        return g.getNodes() > 0;
    }

    @Override
    public void createNew() {
        Helper.deleteDir(new File(dir));
        g = new TinkerGraphImpl(dir);
    }

    @Override
    public void close() {
        super.close();
        ((TinkerGraphImpl) g).close();
    }
}
