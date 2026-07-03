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
package com.graphhopper.util.details

import com.graphhopper.util.EdgeIteratorState

/**
 * Return a String value from the key-values
 *
 * @author Robin Boldt
 */
class KVStringDetails(name: String) : AbstractPathDetailsBuilder(name) {

    private var curString: String? = null
    private var initial = true

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        val value = edge.getValue(name) as String?
        if (initial) {
            curString = value
            initial = false
            return true
        } else if (curString == null) {
            curString = value
            // do not create separate details if value stays null
            return value != null
        } else if (curString != value) {
            curString = value
            return true
        }
        return false
    }

    public override fun getCurrentValue(): Any? = curString
}
