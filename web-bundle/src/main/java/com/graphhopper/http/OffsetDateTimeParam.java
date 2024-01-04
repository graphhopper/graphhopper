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

package com.graphhopper.http;

import io.dropwizard.jersey.params.AbstractParam;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.time.OffsetDateTime;

public class OffsetDateTimeParam extends AbstractParam<OffsetDateTime> {
    public OffsetDateTimeParam(@Nullable String input) {
        super(input);
    }

    public OffsetDateTimeParam(@Nullable String input, String parameterName) {
        super(input, parameterName);
    }

    @Override
    protected String errorMessage(Exception e) {
        return "%s must be in a ISO-8601 format.";
    }

    @Override
    protected OffsetDateTime parse(@Nullable String input) throws Exception {
        if (input == null)
            return null;
        return OffsetDateTime.parse(input);
    }
}
