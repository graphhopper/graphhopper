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

import com.graphhopper.routing.profiles.*;

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
    static final int getScDirMask() {
        return scDirMask;
    }

    /**
     * The bit for forward direction
     */
    static final int getScFwdDir() {
        return scFwdDir;
    }

    /**
     * The EncodedValue for access property of the shortcut
     */
    public static final BooleanEncodedValue SC_ACCESS_ENC = new SimpleBooleanEncodedValue("access", true);
    /**
     * The EncodedValue for the weight property of the shortcut
     */
    // low level integer value instead of decimal, to make same safety conversions when setting the value
    public static final IntEncodedValue WEIGHT_ENC = new SimpleIntEncodedValue("weight", 30);

    static {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        SC_ACCESS_ENC.init(config);
        WEIGHT_ENC.init(config);
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
    public static final int getScMergeStatus(int existingScFlags, boolean newFwd, boolean newBwd) {
        if (newFwd) {
            if (!newBwd)
                return (existingScFlags & scBwdDir) == 0 ? 1 : 0;
        } else if (newBwd) {
            return (existingScFlags & scFwdDir) == 0 ? 1 : 0;
        }
        return (existingScFlags & scDirMask) == scDirMask ? 1 : 2;
    }
}
