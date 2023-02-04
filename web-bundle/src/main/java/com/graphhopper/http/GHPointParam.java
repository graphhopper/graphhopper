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

import com.graphhopper.core.util.shapes.GHPoint;
import io.dropwizard.jersey.params.AbstractParam;

import javax.annotation.Nullable;

/**
 * This is a glue type, used to plug GHPoint as a custom web resource parameter type into Dropwizard,
 * in order to get the best handling of exceptions, validation messages, and such.
 *
 * The structure of this class (including both delegating constructors) should probably be left exactly as it is,
 * and behavior implemented with this should be under tests, because Dropwizard slurps it in by reflection,
 * and I don't know how stable that is over Dropwizard versions.
 *
 * This is only for annotated parameters that go directly into web resources, especially as a QueryParam.
 * Don't use it for fields in body types that go through Jackson ("entities").
 *
 * @author michaz
 */
public class GHPointParam extends AbstractParam<GHPoint> {

    public GHPointParam(@Nullable String input) {
        super(input);
    }

    public GHPointParam(@Nullable String input, String parameterName) {
        super(input, parameterName);
    }

    @Override
    protected GHPoint parse(@Nullable String input) throws Exception {
        if (input == null)
            return null;
        return GHPoint.fromString(input);
    }

}
