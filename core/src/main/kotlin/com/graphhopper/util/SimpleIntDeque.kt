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
package com.graphhopper.util

/**
 * push to end, pop from beginning
 *
 * @author Peter Karich
 */
class SimpleIntDeque @JvmOverloads constructor(initSize: Int, growFactor: Float = 2f) {
    private var arr: IntArray
    private var growFactor: Float
    private var frontIndex = 0
    private var endIndexPlusOne = 0

    constructor() : this(100, 2f)

    init {
        if ((initSize * growFactor).toInt() <= initSize)
            throw RuntimeException("initial size or increasing grow-factor too low!")

        this.growFactor = growFactor
        this.arr = IntArray(initSize)
    }

    @JvmName("getCapacity")
    internal fun getCapacity(): Int = arr.size

    fun setGrowFactor(factor: Float) {
        this.growFactor = factor
    }

    fun isEmpty(): Boolean = frontIndex >= endIndexPlusOne

    fun pop(): Int {
        val tmp = arr[frontIndex]
        frontIndex++

        // removing the empty space of the front if too much is unused
        val smallerSize = (arr.size / growFactor).toInt()
        if (frontIndex > smallerSize) {
            endIndexPlusOne = getSize()
            // ensure that there are at least 10 entries
            val newArr = IntArray(endIndexPlusOne + 10)
            System.arraycopy(arr, frontIndex, newArr, 0, endIndexPlusOne)
            arr = newArr
            frontIndex = 0
        }

        return tmp
    }

    fun getSize(): Int = endIndexPlusOne - frontIndex

    fun push(v: Int) {
        if (endIndexPlusOne >= arr.size)
            arr = arr.copyOf((arr.size * growFactor).toInt())

        arr[endIndexPlusOne] = v
        endIndexPlusOne++
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in frontIndex until endIndexPlusOne) {
            if (i > frontIndex)
                sb.append(", ")
            sb.append(arr[i])
        }
        return sb.toString()
    }
}
