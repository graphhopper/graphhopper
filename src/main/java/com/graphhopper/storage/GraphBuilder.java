/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

/**
 * For now this is just a helper class to quickly create a GraphStorage.
 *
 * @author Peter Karich
 */
public class GraphBuilder {

    private String location;
    private boolean mmap;
    private boolean store;
    private boolean level;
    private int size = 100;

    public GraphBuilder() {
    }

    /**
     * If true builder will create a LevelGraph
     *
     * @see LevelGraph
     */
    GraphBuilder levelGraph(boolean level) {
        this.level = level;
        return this;
    }

    public GraphBuilder location(String location) {
        this.location = location;
        return this;
    }

    public GraphBuilder store(boolean store) {
        this.store = store;
        return this;
    }

    public GraphBuilder mmap(boolean mmap) {
        this.mmap = mmap;
        return this;
    }

    public GraphBuilder size(int size) {
        this.size = size;
        return this;
    }

    public LevelGraphStorage levelGraphBuild() {
        return (LevelGraphStorage) levelGraph(true).build();
    }

    /**
     * Creates a LevelGraphStorage
     */
    public LevelGraphStorage levelGraphCreate() {
        return (LevelGraphStorage) levelGraph(true).create();
    }

    /**
     * Default graph is a GraphStorage with an in memory directory and disabled
     * storing on flush. Afterwards you'll need to call GraphStorage.create
     * to have a useable object. Better use create.
     */
    GraphStorage build() {
        Directory dir;
        if (mmap) {
            dir = new MMapDirectory(location);
        } else {
            dir = new RAMDirectory(location, store);
        }
        GraphStorage graph;
        if (level)
            graph = new LevelGraphStorage(dir);
        else
            graph = new GraphStorage(dir);
        return graph;
    }

    /**
     * Default graph is a GraphStorage with an in memory directory and disabled
     * storing on flush.
     */
    public GraphStorage create() {
        return build().create(size);
    }

    /**
     * @throws IllegalStateException if not loadable.
     */
    public GraphStorage load() {
        GraphStorage gs = build();
        if (!gs.loadExisting())
            throw new IllegalStateException("Cannot load graph " + location);
        return gs;
    }
}
