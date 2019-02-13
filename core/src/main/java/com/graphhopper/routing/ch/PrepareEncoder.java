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
 * The flags are stored differently for shortcuts: just one weight and the two direction bits which is handled by this
 * class for now as static methods.
 *
 * @author Peter Karich
 */
public class PrepareEncoder {
    // shortcut goes in one or both directions is also possible if weight is identical    
    private static final int scFwdDir = 0x1;
    private static final int scBwdDir = 0x2;
    private static final int scDirMask = 0x3;

    /**
     * A bitmask for two directions
     */
    public static final int getScDirMask() {
        return scDirMask;
    }

    /**
     * The bit for forward direction
     */
    public static final int getScFwdDir() {
        return scFwdDir;
    }

    /**
     * The bit for backward direction
     */
    public static final int getScBwdDir() {
        return scBwdDir;
    }

    /**
     * Returns 1 if existingScFlags of an existing shortcut can be overwritten with a new shortcut by
     * newScFlags without limiting or changing the directions of the existing shortcut.
     * The method returns 2 for the same condition but only if the new shortcut has to be added
     * even if weight is higher than existing shortcut weight.
     * <pre>
     *                 | newScFlags:
     * existingScFlags | -> | <- | <->
     * ->              |  1 | 0  | 2
     * <-              |  0 | 1  | 2
     * <->             |  0 | 0  | 1
     * </pre>
     *
     * @return 1 if newScFlags is identical to existingScFlags for the two direction bits and 0 otherwise.
     * There are two special cases when it returns 2.
     */
    public static final int getScMergeStatus(int existingScFlags, int newScFlags) {
        if ((existingScFlags & scDirMask) == (newScFlags & scDirMask))
            return 1;
        else if ((newScFlags & scDirMask) == scDirMask)
            return 2;

        return 0;
    }
}
