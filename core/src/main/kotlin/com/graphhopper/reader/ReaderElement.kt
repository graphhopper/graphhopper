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
package com.graphhopper.reader

/**
 * Base class for all network objects
 *
 * @author Nop
 * @author Peter
 */
abstract class ReaderElement protected constructor(
    val id: Long,
    val type: Type,
    private val properties: MutableMap<String, Any>
) {
    enum class Type {
        NODE,
        WAY,
        RELATION,
        FILEHEADER
    }

    init {
        if (id < 0)
            throw IllegalArgumentException("Invalid OSM $type Id: $id; Ids must not be negative")
    }

    protected constructor(id: Long, type: Type) : this(id, type, LinkedHashMap(4))

    protected fun tagsToString(): String {
        if (properties.isEmpty())
            return "<empty>"

        val tagTxt = StringBuilder()
        for ((key, value) in properties) {
            tagTxt.append(key)
            tagTxt.append("=")
            tagTxt.append(value)
            tagTxt.append("\n")
        }
        return tagTxt.toString()
    }

    fun getTags(): MutableMap<String, Any> = properties

    fun setTags(newTags: Map<String, Any>?) {
        properties.clear()
        if (newTags != null)
            for ((key, value) in newTags)
                setTag(key, value)
    }

    fun hasTags(): Boolean = properties.isNotEmpty()

    fun getTag(name: String): String? = properties[name] as String?

    @Suppress("UNCHECKED_CAST")
    fun <T> getTag(key: String, defaultValue: T): T {
        val value = properties[key] as T?
        return value ?: defaultValue
    }

    fun setTag(name: String, value: Any) {
        properties[name] = value
    }

    /**
     * Check that the object has a given tag with a given value.
     */
    fun hasTag(key: String, value: Any): Boolean =
        value == (properties[key] ?: "")

    /**
     * Check that a given tag has one of the specified values. If no values are given, just checks
     * for presence of the tag
     */
    fun hasTag(key: String, vararg values: String): Boolean {
        val value = properties[key] ?: return false

        // tag present, no values given: success
        if (values.isEmpty())
            return true

        for (v in values) {
            if (v == value)
                return true
        }
        return false
    }

    /**
     * Check that a given tag has one of the specified values.
     */
    @Suppress("UNCHECKED_CAST")
    fun hasTag(key: String, values: Collection<String>): Boolean =
        (values as Collection<Any>).contains(properties[key] ?: "")

    /**
     * Check a number of tags in the given order for any of the given values.
     */
    @Suppress("UNCHECKED_CAST")
    fun hasTag(keyList: List<String>, values: Collection<String>): Boolean {
        for (key in keyList) {
            if ((values as Collection<Any>).contains(properties[key] ?: ""))
                return true
        }
        return false
    }

    /**
     * Check a number of tags in the given order if their value is equal to the specified value.
     */
    fun hasTag(keyList: List<String>, value: Any): Boolean {
        for (key in keyList) {
            if (value == properties[key])
                return true
        }
        return false
    }

    /**
     * Returns the first existing value of the specified list of keys where the order is important.
     *
     * @return an empty string if nothing found
     */
    fun getFirstValue(searchedTags: List<String>): String {
        for (str in searchedTags) {
            val value = properties[str]
            if (value != null)
                return value as String
        }
        return ""
    }

    /**
     * @return -1 if not found
     */
    fun getFirstIndex(searchedTags: List<String>): Int {
        for (i in searchedTags.indices) {
            val str = searchedTags[i]
            if (properties[str] != null)
                return i
        }
        return -1
    }

    fun removeTag(name: String) {
        properties.remove(name)
    }

    fun clearTags() {
        properties.clear()
    }

    override fun toString(): String = properties.toString()
}
