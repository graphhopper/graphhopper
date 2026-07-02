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
package com.graphhopper.storage

import java.io.Closeable

/**
 * Interface for a storage abstraction. Currently is serves just the purpose to ensure the same
 * methods and names through all kind of 'storable' things in graphhopper.
 *
 * Then the lifecycle is identical for all such objects:
 *  1. object creation via new
 *  2. optional configuration via additional setters and getters which are not in this interface
 *  3. if(!storable.loadExisting()) storable.create()
 *  4. usage storable and optional flush() calls in-between. Keep in mind that some data structure
 *     could require a call to increase memory while usage. E.g. DataAccess.ensureCapacity()
 *  5. Finally do close() which does no flush()
 *
 * @author Peter Karich
 */
interface Storable<T> : Closeable {
    val isClosed: Boolean
}
