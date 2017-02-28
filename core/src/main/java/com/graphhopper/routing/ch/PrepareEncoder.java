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
package com.graphhopper.routing.ch;

/**
 * The flags are stored differently for shortcuts: just one weight and the direction flags.
 * <p>
 *
 * @author Peter Karich
 */
public class PrepareEncoder {
    // shortcut goes in one or both directions is also possible if weight is identical    
    private static final long scFwdDir = 0x1;
    private static final long scBwdDir = 0x2;
    private static final long scDirMask = 0x3;

    public static final long getScDirMask() {
        return scDirMask;
    }

    public static final long getScFwdDir() {
        return scFwdDir;
    }

    public static final long getScBwdDir() {
        return scBwdDir;
    }

    /**
     * Returns 1 if existingEdgeFlags can be overwritten in the edge by newEdgeFlags without limiting or
     * changing the directions of existingEdgeFlags. It returns 2 for the same condition AND if the edge
     * with newEdgeFlags has to be added even if weight is higher than existing edge weight.
     * <pre>
     *                    | newEdgeFlags:
     * existingEdgeFlags  | -> | <- | <->
     * ->                 |  1 | 0  | 2
     * <-                 |  0 | 1  | 2
     * <->                |  0 | 0  | 1
     * </pre>
     *
     * @return 1 if newEdgeFlags is enabled in both directions or if both flags are pointing into the
     * same direction.
     */

    public static final int getMergeStatus(long existingEdgeFlags, long newEdgeFlags) {
        if ((existingEdgeFlags & scDirMask) == (newEdgeFlags & scDirMask))
            return 1;
        else if ((newEdgeFlags & scDirMask) == scDirMask)
            return 2;

        return 0;
    }
}
