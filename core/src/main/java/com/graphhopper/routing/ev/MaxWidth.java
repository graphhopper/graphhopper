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

/**
 * One of the four logistic attributes that can be stored per edge.
 *
 * @see MaxHeight
 * @see MaxLength
 * @see MaxWeight
 */
public class MaxWidth {
    public static final String KEY = "max_width";

    /**
     * Currently enables to store 0.1 to max=0.1*2⁷m and infinity. If a value is between the maximum and infinity
     * it is assumed to use the maximum value.
     */
    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 7, 0, 0.1, false, false, true, true);
    }
}
