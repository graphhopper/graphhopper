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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.core.util.details.PathDetail;

import java.io.IOException;
import java.util.Map;

public class PathDetailSerializer extends JsonSerializer<PathDetail> {

    @Override
    public void serialize(PathDetail value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();

        gen.writeNumber(value.getFirst());
        gen.writeNumber(value.getLast());

        if (value.getValue() instanceof Double)
            gen.writeNumber((Double) value.getValue());
        else if (value.getValue() instanceof Long)
            gen.writeNumber((Long) value.getValue());
        else if (value.getValue() instanceof Integer)
            gen.writeNumber((Integer) value.getValue());
        else if (value.getValue() instanceof Boolean)
            gen.writeBoolean((Boolean) value.getValue());
        else if (value.getValue() instanceof String)
            gen.writeString((String) value.getValue());
        else if (value.getValue() instanceof Map)
            gen.writeObject(value.getValue());
        else if (value.getValue() == null)
            gen.writeNull();
        else
            throw new JsonGenerationException("Unsupported type for PathDetail.value" + value.getValue().getClass(), gen);

        gen.writeEndArray();
    }
}
