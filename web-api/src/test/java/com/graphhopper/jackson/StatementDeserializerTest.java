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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.json.Statement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatementDeserializerTest {

    @Test
    void conditionAsBoolean_expressionAsNumber() throws JsonProcessingException {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Statement.class, new StatementDeserializer());
        ObjectMapper objectMapper = new ObjectMapper().registerModule(module);
        // true instead of "true" or 100 instead of "100" also work because they are parsed to strings
        // We probably need to accept numbers instead of strings for legacy support, but maybe we should reject true/false
        Statement statement = objectMapper.readValue("{\"if\":true,\"limit_to\":100}", Statement.class);
        assertEquals(Statement.Keyword.IF, statement.getKeyword());
        assertEquals("true", statement.getCondition());
        assertEquals(Statement.Op.LIMIT, statement.getOperation());
        assertEquals("100", statement.getValue());
    }

    @Test
    void else_null() throws JsonProcessingException {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Statement.class, new StatementDeserializer());
        ObjectMapper objectMapper = new ObjectMapper().registerModule(module);
        // There is no error for `"else": null` currently, even though there is no real reason to support this.
        // The value will actually be null, but the way we use it at the moment this is not a problem.
        Statement statement = objectMapper.readValue("{\"else\":null,\"limit_to\":\"abc\"}", Statement.class);
        assertEquals(Statement.Keyword.ELSE, statement.getKeyword());
        assertNull(statement.getCondition());
        assertEquals(Statement.Op.LIMIT, statement.getOperation());
        assertEquals("abc", statement.getValue());
    }

}