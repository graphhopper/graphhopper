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
 * The algorithm options. Create an immutable object via:
 * <pre>
 * AlgorithmOptions algoOpts = AlgorithmOptions.start().
 *        algorithm(AlgorithmOptions.DIJKSTRA).
 *        weighting(weighting).
 *        build();
 * </pre>
 * <p>
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
    /**
     * alternative route algorithm
     */
    public static final String ALT_ROUTE = "alternativeRoute";
    /**
     * round trip algorithm based on alternative route algorithm
     */
    public static final String ROUND_TRIP_ALT = "roundTripAlt";
    private String algorithm = DIJKSTRA_BI;
    private Weighting weighting;
    private TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private FlagEncoder flagEncoder;
    private final PMap hints = new PMap(5);

    private AlgorithmOptions()
    {
    }

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
     * @return the traversal mode, where node-based is the default.
     */
    public TraversalMode getTraversalMode()
    {
        return traversalMode;
    }

    public Weighting getWeighting()
    {
        assertNotNull(weighting, "weighting");
        return weighting;
    }

    public String getAlgorithm()
    {
        assertNotNull(algorithm, "algorithm");
        return algorithm;
    }

    public FlagEncoder getFlagEncoder()
    {
        assertNotNull(flagEncoder, "flagEncoder");
        return flagEncoder;
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

    /**
     * This method starts the building process for AlgorithmOptions.
     */
    public static Builder start()
    {
        return new Builder();
    }

    /**
     * This method clones the specified AlgorithmOption object with the possibility for further
     * changes.
     */
    public static Builder start( AlgorithmOptions opts )
    {
        Builder b = new Builder();
        if (opts.algorithm != null)
            b.algorithm(opts.getAlgorithm());
        if (opts.flagEncoder != null)
            b.flagEncoder(opts.getFlagEncoder());
        if (opts.traversalMode != null)
            b.traversalMode(opts.getTraversalMode());
        if (opts.weighting != null)
            b.weighting(opts.getWeighting());
        return b;
    }

    public static class Builder
    {
        private final AlgorithmOptions opts = new AlgorithmOptions();

        public Builder traversalMode( TraversalMode traversalMode )
        {
            if (traversalMode == null)
                throw new IllegalArgumentException("null as traversal mode is not allowed");

            this.opts.traversalMode = traversalMode;
            return this;
        }

        public Builder weighting( Weighting weighting )
        {
            this.opts.weighting = weighting;
            return this;
        }

        /**
         * For possible values see AlgorithmOptions.*
         */
        public Builder algorithm( String algorithm )
        {
            this.opts.algorithm = algorithm;
            return this;
        }

        public Builder flagEncoder( FlagEncoder flagEncoder )
        {
            this.opts.flagEncoder = flagEncoder;
            return this;
        }

        public Builder hints( PMap hints )
        {
            this.opts.hints.put(hints);
            return this;
        }

        public AlgorithmOptions build()
        {
            return opts;
        }
    }
}
