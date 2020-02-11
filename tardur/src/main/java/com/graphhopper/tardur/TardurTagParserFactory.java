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

package com.graphhopper.tardur;

import com.graphhopper.routing.util.parsers.DefaultTagParserFactory;
import com.graphhopper.routing.util.parsers.OSMIDParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;

public class TardurTagParserFactory extends DefaultTagParserFactory {


    @Override
    public TagParser create(String name, PMap configuration) {
        if (name.equals("conditional"))
            return new OSMConditionalRestrictionParser();
        else if (name.equals("osmid"))
            return new OSMIDParser();
        return super.create(name, configuration);
    }
}
