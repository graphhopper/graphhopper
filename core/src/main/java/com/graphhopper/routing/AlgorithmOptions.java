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
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.util.PMap;

/**
 *
 * @author Peter Karich
 */
public class AlgorithmOptions
{
    /**
     * Bidirectional Dijkstra
     */
    public static final String DIJKSTRA_BI = "dijkstrabi";
    /**
     * Unidirectional Dijkstra
     */
    public static final String DIJKSTRA = "dijkstra";
    /**
     * one to many Dijkstra
     */
    public static final String DIJKSTRA_ONE_TO_MANY = "dijkstraOneToMany";
    /**
     * Unidirectional A*
     */
    public static final String ASTAR = "astar";
    /**
     * Bidirectional A*
     */
    public static final String ASTAR_BI = "astarbi";
    private String algorithm;
    private Weighting weighting;
    private TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private FlagEncoder flagEncoder;
    private final PMap hints = new PMap(5);

    /**
     * Default traversal mode NODE_BASED is used.
     */
    public AlgorithmOptions( String algorithm, FlagEncoder flagEncoder, Weighting weighting )
    {
        this.algorithm = algorithm;
        this.weighting = weighting;
        this.flagEncoder = flagEncoder;
    }

    public AlgorithmOptions( String algorithm, FlagEncoder flagEncoder, Weighting weighting, TraversalMode tMode )
    {
        this.algorithm = algorithm;
        this.weighting = weighting;
        this.flagEncoder = flagEncoder;
        this.traversalMode = tMode;
    }

    /**
     * This constructor requires to set the flagEncoder, weighting and in most cases also algorithm.
     */
    public AlgorithmOptions()
    {
    }

    /**
     * @return the traversal mode, where node-based is the default.
     */
    public TraversalMode getTraversalMode()
    {
        return traversalMode;
    }

    public AlgorithmOptions setTraversalMode( TraversalMode traversalMode )
    {
        if (traversalMode == null)
            throw new IllegalArgumentException("null as traversal mode is not allowed");

        this.traversalMode = traversalMode;
        return this;
    }

    public Weighting getWeighting()
    {
        assertNotNull(weighting, "weighting");
        return weighting;
    }

    public AlgorithmOptions setWeighting( Weighting weighting )
    {
        this.weighting = weighting;
        return this;
    }

    public String getAlgorithm()
    {
        assertNotNull(algorithm, "algorithm");
        return algorithm;
    }

    /**
     * For possible values see AlgorithmOptions.*
     */
    public AlgorithmOptions setAlgorithm( String algorithm )
    {
        this.algorithm = algorithm;
        return this;
    }

    public FlagEncoder getFlagEncoder()
    {
        assertNotNull(flagEncoder, "flagEncoder");
        return flagEncoder;
    }

    public AlgorithmOptions setFlagEncoder( FlagEncoder flagEncoder )
    {
        this.flagEncoder = flagEncoder;
        return this;
    }

    public PMap getHints()
    {
        return hints;
    }

    private void assertNotNull( Object optionValue, String optionName )
    {
        if (optionValue == null)
            throw new NullPointerException("Option '" + optionName + "' must NOT be null");
    }

    @Override
    public String toString()
    {
        return algorithm + ", " + weighting + ", " + flagEncoder + ", " + traversalMode;
    }        
}
