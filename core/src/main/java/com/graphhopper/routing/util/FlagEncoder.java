/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
     * @return the speed in km/h
     */
    int getSpeed( int flags );

    /**
     * Sets the speed in km/h.
     * <p>
     * @return modified setProperties
     */
    int setSpeed( int flags, int speed );

    /**
     * Sets the access of the edge.
     * <p>
     * @return modified setProperties
     */
    int setAccess( int flags, boolean forward, boolean backward );

    /**
     * Sets speed and access properties.
     * <p>
     * @return created setProperties
     */
    int setProperties( int speed, boolean forward, boolean backward );

    boolean isForward( int flags );

    boolean isBackward( int flags );

    /**
     * @return the maximum speed in km/h
     */
    int getMaxSpeed();

    /**
     * Returns true if setProperties1 can be overwritten by setProperties2 without restricting or changing the
 directions of setProperties1.
     */
    //        \  flags2:
    // flags1  \ -> | <- | <->
    // ->         t | f  | t
    // <-         f | t  | t
    // <->        f | f  | t
    boolean canBeOverwritten( int flags1, int flags2 );
}
