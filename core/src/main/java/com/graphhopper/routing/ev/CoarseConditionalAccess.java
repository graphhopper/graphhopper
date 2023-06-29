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
package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * This encoded value detects only the access tags valid for one day or more and uses the import
 * time as a reference. E.g. access:conditional=no @ (Su 20:00 - 06:00) will be considered as NO_MATCH
 * and e.g. access:conditional=no @ ( 2023 Mar 23 ) will return NO only on 23. March 2023 and otherwise
 * NO_MATCH.
 */
public enum CoarseConditionalAccess {
    NO_MATCH, YES, NO;

    public static final String KEY = "coarse_conditional_access";

    public static EnumEncodedValue<CoarseConditionalAccess> create() {
        return new EnumEncodedValue<>(KEY, CoarseConditionalAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
