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
package com.graphhopper.routing.profiles;

import java.util.Arrays;

public class MaxWeight {
    public static final String KEY = "max_weight";

    public static DecimalEncodedValue create() {
        return new MappedDecimalEncodedValue(KEY, Arrays.asList(1.5, 2.0, 2.5, 2.8, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5,
                6.0, 6.5, 7.0, 7.5, 8.0, 9.0, 10.0, 12.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0, 30.0), .1, false);
    }
}
