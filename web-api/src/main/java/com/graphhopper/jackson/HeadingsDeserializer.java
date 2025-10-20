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
package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer to allow users to use null in JSON requests instead of the string "NaN".
 */
public class HeadingsDeserializer extends JsonDeserializer<List<Double>> {
    @Override
    public List<Double> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<Double> headings = new ArrayList<>();

        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw ctxt.wrongTokenException(p, List.class, JsonToken.START_ARRAY, "Expected array for headings");
        }

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.VALUE_NULL) {
                headings.add(Double.NaN);
            } else if (p.currentToken() == JsonToken.VALUE_NUMBER_FLOAT || p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                headings.add(p.getDoubleValue());
            } else if (p.currentToken() == JsonToken.VALUE_STRING) {
                // Handle "NaN" string for backward compatibility
                String value = p.getText();
                if ("NaN".equals(value)) {
                    headings.add(Double.NaN);
                } else {
                    // Try to parse as double
                    try {
                        headings.add(Double.parseDouble(value));
                    } catch (NumberFormatException e) {
                        throw ctxt.weirdStringException(value, Double.class, "Invalid heading value: " + value);
                    }
                }
            } else {
                throw ctxt.wrongTokenException(p, Double.class, p.currentToken(), "Expected number, string or null for heading value");
            }
        }

        return headings;
    }
}
