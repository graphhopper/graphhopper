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
package com.graphhopper.routing.util;

/**
 * This class provides methods to define how a value (like speed or direction) converts to a flag
 * (currently an integer value), which is stored in an edge .
 * <p/>
 * @author Peter Karich
 */
public interface FlagEncoder
{
    /**
     * @return the maximum speed in km/h
     */
    double getMaxSpeed();

    /**
     * @return the speed in km/h for this direction, for backward direction use getReverseSpeed
     */
    double getSpeed( long flags );

    /**
     * Sets the speed in km/h.
     * <p>
     * @return modified setProperties
     */
    long setSpeed( long flags, double speed );

    /**
     * @return the speed of the reverse direction in km/h
     */
    double getReverseSpeed( long flags );

    /**
     * Sets the reverse speed in the flags.
     */
    long setReverseSpeed( long flags, double speed );

    /**
     * Sets the access of the edge.
     * <p>
     * @return modified flags
     */
    long setAccess( long flags, boolean forward, boolean backward );

    /**
     * Sets speed and access properties.
     * <p>
     * @return created flags
     */
    long setProperties( double speed, boolean forward, boolean backward );

    boolean isForward( long flags );

    boolean isBackward( long flags );    

    /**
     * @return the number to identify a pavement of a road.
     * @see InstructionList#getWayName
     */
    int getPavementType( long flags );

    /**
     * @return the number to identify a pushing section, cycle way etc.
     * @see InstructionList#getWayName
     */
    int getWayType( long flags );
}
