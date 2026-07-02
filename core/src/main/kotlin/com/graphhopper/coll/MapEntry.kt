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
package com.graphhopper.coll

import java.io.Serializable

/**
 * Simple impl of Map.Entry. So that we can have ordered maps.
 *
 * @author Peter Karich
 */
class MapEntry<K, V>(override val key: K, private var _value: V) : MutableMap.MutableEntry<K, V>, Serializable {

    override val value: V
        get() = _value

    // returns the new value (not the previous one as the Map.Entry contract suggests), exactly
    // like the original java version
    override fun setValue(newValue: V): V {
        this._value = newValue
        return newValue
    }

    override fun toString(): String = "$key, $value"

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (javaClass != other.javaClass) return false
        val o = other as MapEntry<*, *>
        return key == o.key && value == o.value
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 19 * hash + (key?.hashCode() ?: 0)
        hash = 19 * hash + (value?.hashCode() ?: 0)
        return hash
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
