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

import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

/**
 * This class provides methods to define how a value (like speed or direction) converts to a flag
 * (currently an integer value), which is stored in an edge .
 * <p>
 *
 * @author Peter Karich
 */
public interface FlagEncoder extends TurnCostEncoder {
    /**
     * Reports whether this edge is part of a roundabout.
     */
    int K_ROUNDABOUT = 2;

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
     * @return the speed in km/h for this direction, for backward direction use getReverseSpeed
     */
    double getSpeed(IntsRef intsRef);

    /**
     * Sets the speed in km/h.
     *
     * @return modified setProperties
     */
    IntsRef setSpeed(IntsRef intsRef, double speed);

    /**
     * @return the speed of the reverse direction in km/h
     */
    double getReverseSpeed(IntsRef intsRef);

    /**
     * Sets the reverse speed in the intsRef.
     */
    IntsRef setReverseSpeed(IntsRef intsRef, double speed);

    /**
     * Sets the access of the edge.
     *
     * @return modified intsRef
     */
    IntsRef setAccess(IntsRef intsRef, boolean forward, boolean backward);

    /**
     * Reports whether the edge is available in forward direction (i.e. from base node to adj node)
     * for a certain vehicle.
     */
    boolean isForward(IntsRef intsRef);

    /*
     * Simple rules for every subclass which introduces a new key. It has to use the prefix K_ and
     * uses a minimum value which is two magnitudes higher than in the super class.
     * Currently this means starting from 100, and subclasses of this class start from 10000 and so on.
     */

    /**
     * Reports whether the edge is available in backward direction (i.e. from adj node to base node)
     * for a certain vehicle.
     */
    boolean isBackward(IntsRef intsRef);

    /**
     * Returns arbitrary boolean value identified by the specified key.
     */
    boolean isBool(IntsRef intsRef, int key);

    IntsRef setBool(IntsRef intsRef, int key, boolean value);

    /**
     * Returns arbitrary long value identified by the specified key. E.g. can be used to return the
     * way or surface type of an edge
     */
    long getLong(IntsRef intsRef, int key);

    IntsRef setLong(IntsRef intsRef, int key, long value);

    /**
     * Returns arbitrary double value identified by the specified key. E.g. can be used to return
     * the maximum width or height allowed for an edge.
     */
    double getDouble(IntsRef intsRef, int key);

    IntsRef setDouble(IntsRef intsRef, int key, double value);

    /**
     * Returns true if the feature class is supported like TurnWeighting or PriorityWeighting.
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
