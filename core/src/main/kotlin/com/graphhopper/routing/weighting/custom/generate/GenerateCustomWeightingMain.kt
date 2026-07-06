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
package com.graphhopper.routing.weighting.custom.generate

import com.graphhopper.GraphHopper
import com.graphhopper.jackson.Jackson
import com.graphhopper.routing.ev.DefaultImportRegistry
import com.graphhopper.routing.ev.ImportUnit
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Helper
import com.graphhopper.util.PMap
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Build-time command line tool that turns custom model JSON files into Kotlin sources (see
 * [CustomWeightingSourceGenerator]) plus a registration snippet for the
 * [GeneratedWeightingRegistry]. Typical use: run it from the mobile app's build (e.g. a
 * Gradle `JavaExec` task with graphhopper-core on its classpath, wired before
 * `compileKotlin` — the app-side Gradle wiring itself is out of scope here) and compile the
 * written files with the app.
 *
 * Usage (key=value arguments, order-independent):
 * ```
 * java -cp graphhopper-core-with-deps.jar \
 *   com.graphhopper.routing.weighting.custom.generate.GenerateCustomWeightingMain \
 *   custom_models=path/to/car.json,path/to/truck.json \
 *   encoded_values="car_access, car_average_speed, road_environment, ferry_speed, max_speed" \
 *   output=build/generated/customWeightings \
 *   package=com.example.weightings
 * ```
 *
 *  - `custom_models` (required): comma-separated custom model JSON paths (`//` comments allowed,
 *    like GraphHopper's bundled models)
 *  - `encoded_values` (required): the EV configuration in exactly the `graph.encoded_values`
 *    config syntax incl. properties (e.g. `car_average_speed|speed_bits=5`); it is resolved
 *    with the same [DefaultImportRegistry] mechanism the server uses, so the generated code
 *    sees identical encoded-value metadata — NO loaded graph is needed
 *  - `output` (optional, default `.`): directory for the written `.kt` files
 *  - `package` (optional, default `com.graphhopper.generated`): package of the written classes
 *
 * Per model `foo_bar.json` it writes `GeneratedFooBarCustomWeighting.kt`, plus one
 * `GeneratedCustomWeightings.kt` whose `registerAll()` registers every class under the
 * model's identity key captured now (valid as long as the JSON content stays unchanged —
 * registering with the runtime [CustomModel] instance instead is always safe):
 * ```
 * GeneratedCustomWeightings.registerAll()
 * CustomWeightingBackends.setDefault(RegistryBackend)
 * ```
 */
object GenerateCustomWeightingMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val argMap = HashMap<String, String>()
        for (arg in args) {
            val idx = arg.indexOf('=')
            require(idx > 0) { "arguments must be key=value pairs, got: $arg" }
            argMap[arg.substring(0, idx)] = arg.substring(idx + 1)
        }
        val modelPaths = argMap["custom_models"]
                ?: throw IllegalArgumentException("missing argument custom_models=<path.json>[,<path.json>...]")
        val encodedValues = argMap["encoded_values"]
                ?: throw IllegalArgumentException("missing argument encoded_values=<graph.encoded_values syntax>")
        val outputDir = File(argMap["output"] ?: ".")
        val packageName = argMap["package"] ?: "com.graphhopper.generated"

        val lookup = buildEncodingManager(encodedValues)
        outputDir.mkdirs()

        val registrations = ArrayList<Pair<String, String>>() // className -> model key
        for (path in modelPaths.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val file = File(path)
            val customModel = loadCustomModel(file)
            val className = classNameFor(file.name)
            val source = CustomWeightingSourceGenerator.generate(customModel, lookup, packageName, className)
            val outFile = File(outputDir, "$className.kt")
            outFile.writeText(source, StandardCharsets.UTF_8)
            registrations.add(Pair(className, customModel.toString()))
            println("wrote ${outFile.path} (custom model: $path)")
        }

        val registrationFile = File(outputDir, "GeneratedCustomWeightings.kt")
        registrationFile.writeText(registrationSnippet(packageName, registrations), StandardCharsets.UTF_8)
        println("wrote ${registrationFile.path} - call GeneratedCustomWeightings.registerAll() at startup " +
                "and select the backend via CustomWeightingBackends.setDefault(RegistryBackend)")
    }

    /**
     * Builds an [EncodingManager] from a `graph.encoded_values` config string WITHOUT a graph
     * or OSM import — the same parse + [DefaultImportRegistry] resolution (incl. transitively
     * required encoded values) as `GraphHopper.prepareImport`.
     */
    @JvmStatic
    fun buildEncodingManager(encodedValuesString: String): EncodingManager {
        val encodedValuesWithProps = GraphHopper.parseEncodedValueString(encodedValuesString)
        val importRegistry = DefaultImportRegistry()
        val activeImportUnits = LinkedHashMap<String, ImportUnit>()
        val deque = ArrayDeque(encodedValuesWithProps.keys)
        while (deque.isNotEmpty()) {
            val ev = deque.removeFirst()
            val importUnit = importRegistry.createImportUnit(ev)
                    ?: throw IllegalArgumentException("Unknown encoded value: $ev")
            if (activeImportUnits.put(ev, importUnit) == null)
                deque.addAll(importUnit.requiredImportUnits)
        }
        val builder = EncodingManager.Builder()
        activeImportUnits.forEach { (name, importUnit) ->
            val create = importUnit.createEncodedValue
            if (create != null)
                builder.add(create.apply(encodedValuesWithProps.getOrDefault(name, PMap())))
        }
        return builder.build()
    }

    /** Loads a custom model JSON file, allowing `//` comments like GHUtility.loadCustomModelFromJar. */
    @JvmStatic
    fun loadCustomModel(file: File): CustomModel {
        val json = Helper.readJSONFileWithoutComments(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8))
        return Jackson.newObjectMapper().readValue(json, CustomModel::class.java)
    }

    /** `foo_bar.json` (or `foo-bar.json`) -> `GeneratedFooBarCustomWeighting`. */
    @JvmStatic
    fun classNameFor(fileName: String): String {
        val base = fileName.removeSuffix(".json")
        val camel = base.split('_', '-', '.')
                .filter { it.isNotEmpty() }
                .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        require(camel.isNotEmpty() && camel.all { it.isLetterOrDigit() }) { "cannot derive a class name from: $fileName" }
        return "Generated${camel}CustomWeighting"
    }

    private fun registrationSnippet(packageName: String, registrations: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("// Generated by GraphHopper's GenerateCustomWeightingMain - DO NOT EDIT.\n")
        sb.append("package ").append(packageName).append("\n\n")
        sb.append("import com.graphhopper.routing.weighting.custom.generate.GeneratedWeightingRegistry\n\n")
        sb.append("object GeneratedCustomWeightings {\n")
        sb.append("    /** Call once at startup, before any weighting is created. */\n")
        sb.append("    @JvmStatic\n")
        sb.append("    fun registerAll() {\n")
        for ((className, key) in registrations) {
            sb.append("        GeneratedWeightingRegistry.register(\n")
            sb.append("            ").append(CustomWeightingSourceGenerator.renderString(key)).append(",\n")
            sb.append("            ::").append(className).append(")\n")
        }
        sb.append("    }\n")
        sb.append("}\n")
        return sb.toString()
    }
}
