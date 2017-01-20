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
package com.graphhopper.json;

import java.io.Reader;

/**
 * A simple JSON (de)serialization facade. E.g. to be easily replaced with platform specific
 * implementations.
 *
 * @author Peter Karich
 */
public interface GHJson {
    /**
     * This method reads JSON data from the provided source and creates an instance of the provided
     * class.
     */
    <T> T fromJson(Reader source, Class<T> aClass);
}
