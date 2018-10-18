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
package com.graphhopper.routing.flex;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.profiles.RoadEnvironment;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Every property like road_environment can influence one or more aspects of the FlexWeighting: the timeOffset, the factor
 * and the speed.
 */
public class FlexModel {
    // is only used for import configuration
    private String name = "";
    private String base;
    private double maxSpeed;
    private Speed speed = new Speed();
    private Factor factor = new Factor();
    private TimeOffset timeOffset = new TimeOffset();
    private NoAccess noAccess = new NoAccess();
    private boolean considerOneway;

    public String getName() {
        return name;
    }

    public String getBase() {
        return base;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public Speed getSpeed() {
        return speed;
    }

    public Factor getFactor() {
        return factor;
    }

    public TimeOffset getTimeOffset() {
        return timeOffset;
    }

    public boolean isConsiderOneway() {
        return considerOneway;
    }

    public NoAccess getNoAccess() {
        return noAccess;
    }

    public static class Speed {
        Map<String, Double> roadClass = Collections.emptyMap();
        Map<String, Double> roadEnvironment = Collections.emptyMap();

        public Map<String, Double> getRoadClass() {
            return roadClass;
        }

        public Map<String, Double> getRoadEnvironment() {
            return roadEnvironment;
        }
    }

    public static class Factor {
        Map<String, Double> roadClass = Collections.emptyMap();
        Map<String, Double> roadEnvironment = Collections.emptyMap();
        Map<String, Double> toll = Collections.emptyMap();
        boolean reverseOneway;

        public Map<String, Double> getRoadClass() {
            return roadClass;
        }

        public Map<String, Double> getRoadEnvironment() {
            return roadEnvironment;
        }

        public Map<String, Double> getToll() {
            return toll;
        }

        public boolean isReverseOneway() {
            return reverseOneway;
        }
    }

    /**
     * The time offset in seconds for different road class or environment.
     */
    public static class TimeOffset {
        Map<String, Double> roadClass = Collections.emptyMap();
        Map<String, Double> roadEnvironment = Collections.emptyMap();

        public Map<String, Double> getRoadClass() {
            return roadClass;
        }

        public Map<String, Double> getRoadEnvironment() {
            return roadEnvironment;
        }
    }

    public static class NoAccess {
        Set<RoadClass> roadClass = Collections.emptySet();
        Set<RoadEnvironment> roadEnvironment = Collections.emptySet();
        double maxHeight;
        double maxWeight;
        double maxWidth;

        public double getMaxHeight() {
            return maxHeight;
        }

        public double getMaxWeight() {
            return maxWeight;
        }

        public double getMaxWidth() {
            return maxWidth;
        }

        public Set<RoadClass> getRoadClass() {
            return roadClass;
        }

        public Set<RoadEnvironment> getRoadEnvironment() {
            return roadEnvironment;
        }
    }
}
