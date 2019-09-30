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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

public class FlexYConfigParser implements FlexConfigParser {

    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public FlexConfig parse(InputStream is) {
        try {
            return mapper.readValue(is, FlexConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FlexConfig parse(String string) {
        try {
            return mapper.readValue(string, FlexConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
