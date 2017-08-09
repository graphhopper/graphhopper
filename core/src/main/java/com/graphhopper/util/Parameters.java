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
package com.graphhopper.util;

/**
 * @author Peter Karich
 */
public class Parameters {
    /* Parameters with an 'INIT' prefix are used as defaults and/or are configured at start.*/
    static final String ROUTING_INIT_PREFIX = "routing.";

    /**
     * Parameters that can be used for algorithm.
     */
    public static final class Algorithms {
        /**
         * Bidirectional Dijkstra
         */
        public static final String DIJKSTRA_BI = "dijkstrabi";
        /**
         * Unidirectional Dijkstra (not for CH)
         */
        public static final String DIJKSTRA = "dijkstra";
        /**
         * one to many Dijkstra (not yet for edge based #394, not yet for CH)
         */
        public static final String DIJKSTRA_ONE_TO_MANY = "dijkstra_one_to_many";
        /**
         * Unidirectional A* (not for CH)
         */
        public static final String ASTAR = "astar";
        /**
         * Bidirectional A*
         */
        public static final String ASTAR_BI = "astarbi";
        /**
         * alternative route algorithm (not yet for CH)
         */
        public static final String ALT_ROUTE = "alternative_route";
        /**
         * round trip algorithm (not yet for CH)
         */
        public static final String ROUND_TRIP = "round_trip";

        /**
         * All public properties for alternative routing.
         */
        public static final class AltRoute {
            public static final String MAX_PATHS = ALT_ROUTE + ".max_paths";

            public static final String MAX_WEIGHT = ALT_ROUTE + ".max_weight_factor";

            public static final String MAX_SHARE = ALT_ROUTE + ".max_share_factor";
        }

        public static final class AStar {
            public static final String EPSILON = ASTAR + ".epsilon";
        }

        public static final class AStarBi {
            public static final String EPSILON = ASTAR_BI + ".epsilon";
        }

        /**
         * All public properties for round trip calculation.
         */
        public static final class RoundTrip {
            public static final String DISTANCE = ROUND_TRIP + ".distance";
            public static final String SEED = ROUND_TRIP + ".seed";
            public static final String HEADING = "heading";
            public static final String INIT_MAX_RETRIES = ROUTING_INIT_PREFIX + ROUND_TRIP + ".max_retries";
        }
    }

    /**
     * Parameters that can be passed as hints and influence routing per request.
     */
    public static final class Routing {
        public static final String EDGE_BASED = "edge_based";
        public static final String MAX_VISITED_NODES = "max_visited_nodes";
        public static final String INIT_MAX_VISITED_NODES = ROUTING_INIT_PREFIX + "max_visited_nodes";
        /**
         * if true the response will contain turn instructions
         */
        public static final String INSTRUCTIONS = "instructions";
        /**
         * if true the response will contain a point list
         */
        public static final String CALC_POINTS = "calc_points";
        /**
         * configure simplification of returned point list
         */
        public static final String WAY_POINT_MAX_DISTANCE = "way_point_max_distance";
        public static final String INIT_WAY_POINT_MAX_DISTANCE = ROUTING_INIT_PREFIX + "way_point_max_distance";
        /**
         * true or false. If routes at via points should avoid u-turns. (not for CH) See related
         * 'heading' parameter:
         * https://github.com/graphhopper/graphhopper/blob/master/docs/core/routing.md#heading
         */
        public static final String PASS_THROUGH = "pass_through";
        public static final String POINT_HINT = "point_hint";
        /**
         * default heading penalty in seconds
         */
        public static final double DEFAULT_HEADING_PENALTY = 300;
        public static final String HEADING_PENALTY = "heading_penalty";
        /**
         * block road access via a point in the format lat,lon or an area defined by a circle lat,lon,radius or
         * a rectangle lat1,lon1,lat2,lon2
         */
        public static final String BLOCK_AREA = "block_area";
    }

    /**
     * Properties for routing with contraction hierarchies speedup
     */
    public static final class CH {
        public static final String PREPARE = "prepare.ch.";
        /**
         * This property name in HintsMap configures at runtime if CH routing should be ignored.
         */
        public static final String DISABLE = "ch.disable";
        /**
         * This property name configures at start if the DISABLE parameter can have an effect.
         */
        public static final String INIT_DISABLING_ALLOWED = ROUTING_INIT_PREFIX + "ch.disabling_allowed";
        /**
         * The property name in HintsMap if heading should be used for CH regardless of the possible
         * routing errors.
         */
        public static final String FORCE_HEADING = "ch.force_heading";
    }

    /**
     * Properties for routing with landmark speedup
     */
    public static final class Landmark {
        public static final String PREPARE = "prepare.lm.";
        /**
         * This property name in HintsMap configures at runtime if CH routing should be ignored.
         */
        public static final String DISABLE = "lm.disable";
        /**
         * Specifies how many active landmarks should be used when routing
         */
        public static final String ACTIVE_COUNT = "lm.active_landmarks";
        /**
         * Default for active count
         */
        public static final String ACTIVE_COUNT_DEFAULT = ROUTING_INIT_PREFIX + ACTIVE_COUNT;
        /**
         * Specifies how many landmarks should be created
         */
        public static final String COUNT = PREPARE + "landmarks";
        /**
         * This property name configures at start if the DISABLE parameter can have an effect.
         */
        public static final String INIT_DISABLING_ALLOWED = ROUTING_INIT_PREFIX + "lm.disabling_allowed";
    }

    /**
     * Properties for non-CH routing
     */
    public static final class NON_CH {

        private static final String NON_CH_PREFIX = "non_ch.";

        /**
         * Describes the maximum allowed distance between two consecutive waypoints of a non-CH request. Distance is in meter.
         */
        public static final String MAX_NON_CH_POINT_DISTANCE = ROUTING_INIT_PREFIX + NON_CH_PREFIX + "max_waypoint_distance";
    }

    /**
     * Properties for the details response
     */
    public static final class DETAILS {

        public static final String PATH_DETAILS = "details";

        public static final String AVERAGE_SPEED = "average_speed";

    }

    public static final class PT {
        public static final String EARLIEST_DEPARTURE_TIME = "pt.earliest_departure_time";
        public static final String PROFILE_QUERY = "pt.profile";
        public static final String ARRIVE_BY = "pt.arrive_by";
        public static final String IGNORE_TRANSFERS = "pt.ignore_transfers";
        public static final String WALK_SPEED = "pt.walk_speed";
        public static final String MAX_WALK_DISTANCE_PER_LEG = "pt.max_walk_distance_per_leg";
        public static final String MAX_TRANSFER_DISTANCE_PER_LEG = "pt.max_transfer_distance_per_leg";
        public static final String LIMIT_SOLUTIONS = "pt.limit_solutions";

    }
}
