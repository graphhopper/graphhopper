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
package com.graphhopper.routing.ev

// topological sort with a depth first search
class ImportUnitSorter(private val map: Map<String, ImportUnit>) {
    private val permanentMarked = HashSet<String>()
    private val temporaryMarked = HashSet<String>()
    private val result = ArrayList<String>()

    fun sort(): List<String> {
        for (strN in map.keys) {
            visit(strN)
        }
        return result
    }

    private fun visit(strN: String) {
        if (permanentMarked.contains(strN)) return
        val importUnit = map[strN]
                ?: throw IllegalArgumentException("cannot find import unit $strN")
        if (temporaryMarked.contains(strN))
            throw IllegalArgumentException("import units with cyclic dependencies are not allowed: $importUnit ${importUnit.requiredImportUnits}")

        temporaryMarked.add(strN)
        for (strM in importUnit.requiredImportUnits) {
            visit(strM)
        }
        temporaryMarked.remove(strN)
        permanentMarked.add(strN)
        result.add(strN)
    }
}
