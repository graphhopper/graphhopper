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

import com.graphhopper.util.Helper;

public class DefaultEncodedValueFactory implements EncodedValueFactory {
    @Override
    public EncodedValue create(String string) {
        if (Helper.isEmpty(string))
            throw new IllegalArgumentException("No string provided to load EncodedValue");

        final EncodedValue enc;
        String name = string.split("\\|")[0];
        if (name.isEmpty())
            throw new IllegalArgumentException("To load EncodedValue name is required. " + string);

        if (Roundabout.KEY.equals(name)) {
            enc = Roundabout.create();
        } else if (RoadClassLink.KEY.equals(name)) {
            enc = RoadClassLink.create();
        } else if (RoadClass.KEY.equals(name)) {
            enc = RoadClass.create();
        } else if (RoadEnvironment.KEY.equals(name)) {
            enc = RoadEnvironment.create();
        } else if (RoadAccess.KEY.equals(name)) {
            enc = RoadAccess.create();
        } else if (CarMaxSpeed.KEY.equals(name)) {
            enc = CarMaxSpeed.create();
        } else if (MaxWeight.KEY.equals(name)) {
            enc = MaxWeight.create();
        } else if (MaxHeight.KEY.equals(name)) {
            enc = MaxHeight.create();
        } else if (MaxWidth.KEY.equals(name)) {
            enc = MaxWidth.create();
        } else if (Surface.KEY.equals(name)) {
            enc = Surface.create();
        } else {
            throw new IllegalArgumentException("DefaultEncodedValueFactory cannot find EncodedValue " + name);
        }
        return enc;
    }
}
