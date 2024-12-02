/*
 * Copyright 2024 KJ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 // Start block - Added by KJ for RideSense 16062024

 package com.graphhopper.routing.ev;

 import com.graphhopper.util.Helper;
 import com.graphhopper.util.PMap;

public enum RSBikeAccess {
    NO, YES;

    public static final String KEY = "rsbikeaccess";

    public static EnumEncodedValue<RSBikeAccess> create() {
        return new EnumEncodedValue<>(KEY, RSBikeAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static RSBikeAccess find(String name) {
        if (Helper.isEmpty(name))
            return YES;
        try {
            return RSBikeAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }
}
// End block - Added by KJ for RideSense 16062024