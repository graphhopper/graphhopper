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
package com.graphhopper.routing.util;

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

/**
 * This class provides methods to define how a value (like speed or direction) converts to a flag
 * (currently an integer value), which is stored in an edge.
 *
 * @author Peter Karich
 */
public interface FlagEncoder extends TurnCostEncoder, EncodedValueLookup {

    /**
     * @return the version of this FlagEncoder to enforce none-compatibility when new attributes are
     * introduced
     */
    int getVersion();

    /**
     * @return the maximum speed in km/h
     */
    double getMaxSpeed();

    /**
     * This method returns the EncodedValue used for the direction-dependent access properties of this encoder.
     */
    BooleanEncodedValue getAccessEnc();

    /**
     * This method returns the EncodedValue used for the average speed of this encoder.
     */
    DecimalEncodedValue getAverageSpeedEnc();

    /**
     * Returns true if the feature class is supported like TurnWeighting or PriorityWeighting.
     * Use support(String) instead.
     */
    boolean supports(Class<?> feature);

    /**
     * @return additional cost or warning information for an instruction like ferry or road charges.
     */
    InstructionAnnotation getAnnotation(IntsRef intsRef, Translation tr);

    /**
     * @return true if already registered in an EncodingManager
     */
    boolean isRegistered();
}
