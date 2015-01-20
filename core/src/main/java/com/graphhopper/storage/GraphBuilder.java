/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.storage;

import com.graphhopper.routing.util.EncodingManager;

/**
 * For now this is just a helper class to quickly create a GraphStorage.
 * <p/>
 * @author Peter Karich
 */
public class GraphBuilder
{
    private final EncodingManager encodingManager;
    private String location;
    private boolean mmap;
    private boolean store;
    private boolean level;
    private boolean elevation;
    private long byteCapacity = 100;

    public GraphBuilder( EncodingManager encodingManager )
    {
        this.encodingManager = encodingManager;
    }

    /**
     * If true builder will create a LevelGraph
     * <p/>
     * @see LevelGraph
     */
    public GraphBuilder setLevelGraph( boolean level )
    {
        this.level = level;
        return this;
    }

    public GraphBuilder setLocation( String location )
    {
        this.location = location;
        return this;
    }

    public GraphBuilder setStore( boolean store )
    {
        this.store = store;
        return this;
    }

    public GraphBuilder setMmap( boolean mmap )
    {
        this.mmap = mmap;
        return this;
    }

    public GraphBuilder setExpectedSize( byte cap )
    {
        this.byteCapacity = cap;
        return this;
    }

    public GraphBuilder set3D( boolean withElevation )
    {
        this.elevation = withElevation;
        return this;
    }

    public boolean hasElevation()
    {
        return elevation;
    }

    public LevelGraphStorage levelGraphBuild()
    {
        return (LevelGraphStorage) setLevelGraph(true).build();
    }

    /**
     * Creates a LevelGraphStorage
     */
    public LevelGraphStorage levelGraphCreate()
    {
        return (LevelGraphStorage) setLevelGraph(true).create();
    }

    /**
     * Default graph is a GraphStorage with an in memory directory and disabled storing on flush.
     * Afterwards you'll need to call GraphStorage.create to have a useable object. Better use
     * create.
     */
    public GraphStorage build()
    {
        Directory dir;
        if (mmap)
            dir = new MMapDirectory(location);
        else
            dir = new RAMDirectory(location, store);

        GraphStorage graph;
        if (level)
            graph = new LevelGraphStorage(dir, encodingManager, elevation);
        else
        {
            if (encodingManager.needsTurnCostsSupport())
                graph = new GraphHopperStorage(dir, encodingManager, elevation, new TurnCostExtension());
            else
                graph = new GraphHopperStorage(dir, encodingManager, elevation);
        }

        return graph;
    }

    /**
     * Default graph is a GraphStorage with an in memory directory and disabled storing on flush.
     */
    public GraphStorage create()
    {
        return build().create(byteCapacity);
    }

    /**
     * @throws IllegalStateException if not loadable.
     */
    public GraphStorage load()
    {
        GraphStorage gs = build();
        if (!gs.loadExisting())
        {
            throw new IllegalStateException("Cannot load graph " + location);
        }
        return gs;
    }
}
