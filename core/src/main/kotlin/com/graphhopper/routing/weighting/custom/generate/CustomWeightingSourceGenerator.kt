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

import com.graphhopper.json.Statement
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.Orientation
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.weighting.custom.ClosureBackend
import com.graphhopper.routing.weighting.custom.CustomModelParser
import com.graphhopper.routing.weighting.custom.CustomWeightingHelper
import com.graphhopper.routing.weighting.custom.expression.ExprNode
import com.graphhopper.routing.weighting.custom.expression.ExpressionContext
import com.graphhopper.routing.weighting.custom.expression.ExpressionScope
import com.graphhopper.routing.weighting.custom.expression.ExpressionScopes
import com.graphhopper.routing.weighting.custom.expression.ExpressionValidator
import com.graphhopper.routing.weighting.custom.expression.LiteralKind
import com.graphhopper.routing.weighting.custom.expression.SemType
import com.graphhopper.routing.weighting.custom.expression.TypedCompiler
import com.graphhopper.util.CustomModel
import com.graphhopper.util.JsonFeature
import org.locationtech.jts.geom.Polygonal

/**
 * BUILD-TIME Kotlin source generation for custom models (stage 5 of the custom-model platform
 * work): given a [CustomModel] plus the encoded-value metadata it references, emits a
 * self-contained Kotlin source file with a class extending [CustomWeightingHelper] that
 * computes exactly what the Janino back-end's runtime-generated class computes — for
 * platforms where runtime codegen is impossible (Android has no runtime DEX generation,
 * iOS is AOT-compiled). The generated class is compiled by the app's own build (kotlinc)
 * and registered in the [GeneratedWeightingRegistry], where the [RegistryBackend] picks it
 * up by the custom model's identity.
 *
 * INPUT DESIGN — no loaded graph is required: the generator only reads encoded-value
 * METADATA (name, kind, enum constants, storeTwoDirections) through the [EncodedValueLookup]
 * interface. Any of the following works at build/config time:
 *
 *  - an `EncodingManager` built from a `graph.encoded_values` config string (see
 *    [GenerateCustomWeightingMain.buildEncodingManager] — the same `DefaultImportRegistry`
 *    mechanism GraphHopper's import uses, so properties like `car_average_speed|speed_bits=5`
 *    yield identical value ranges), or
 *  - the `EncodingManager` of a loaded GraphHopper instance (e.g. right after import).
 *
 * SEMANTICS — the emitted method bodies are an unparse of the validated stage-3 AST
 * ([ExprNode]) into Kotlin source, with the typing/promotion decisions of the stage-4
 * [TypedCompiler] made EXPLICIT, because Kotlin has no implicit numeric widening:
 *
 *  - binary numeric promotion int → long → float → double is emitted as explicit
 *    `.toDouble()`/`.toFloat()`/`.toLong()` conversions on the narrower operand
 *  - `int / int` stays `Int` (Kotlin's `/` truncates like Java's)
 *  - Java literals are re-typed exactly ([TypedCompiler.parseJavaNumericLiteral]) and
 *    rendered as canonical Kotlin literals (Kotlin has no octal literals and types
 *    `-2147483648` as Long, so constants are folded and rendered per type)
 *  - `String == String` (turn-penalty street names) becomes identity `===` like Java's
 *    reference `==`; string literals are interned on the JVM in both languages
 *  - enum comparisons stay identity `==` against the qualified constant of the left-hand
 *    side's enum type; `& | ^` become `and or xor` (non-short-circuit for booleans,
 *    bitwise for integrals); shifts become `shl shr ushr` with an `Int` shift distance
 *  - `Math.sqrt`/`Math.abs` become `kotlin.math.sqrt`/`kotlin.math.abs` (same IEEE-754
 *    semantics, same overload resolution by argument type)
 *
 * The class structure mirrors the Janino template of `CustomModelParser.createClassTemplate`
 * 1:1: encoded values are bound to fields in `init(customModel, lookup, areas)` (so ONE
 * generated class works with any compatible graph/lookup at runtime), and
 * `getSpeed`/`getPriority`/`getTurnPenalty` declare one local per guessed variable and fold
 * the statement list into a `value` accumulator via if/else-if/else chains
 * (`limit_to` → `kotlin.math.min`, `multiply_by` → `*=`, `add` → `+=`).
 *
 * Validation: generation performs the same accept/reject checks as [ClosureBackend]
 * (differentially locked against Janino by ClosureBackendDifferentialTest), so a model the
 * server would reject fails HERE, at build time, with the same message.
 *
 * ATTRIBUTION NOTE: the emitted template mirrors GraphHopper's OWN Janino template
 * (`CustomModelParser.createClassTemplate`/`parseExpressions`, Apache License 2.0) and this
 * generator consumes GraphHopper's own expression AST — no Janino source code or API
 * concepts are used here, so no BSD attribution applies to this file (the idea-level Janino
 * attribution for the expression front-end itself is in the `expression` package + NOTICE.md).
 */
object CustomWeightingSourceGenerator {

    /**
     * Generates the complete Kotlin source file (package declaration, imports, one class
     * `className` extending [CustomWeightingHelper]) for the given custom model.
     *
     * @param lookup encoded-value metadata; see the class KDoc for how to obtain one at build time
     * @throws IllegalArgumentException for every custom model the Janino back-end would reject
     */
    @JvmStatic
    fun generate(customModel: CustomModel, lookup: EncodedValueLookup, packageName: String, className: String): String {
        require(className.isNotEmpty() && className[0].isUpperCase()
                && className.all { it.isLetterOrDigit() || it == '_' }) { "invalid class name: $className" }
        require(packageName.isNotEmpty() && packageName.split('.').all { part ->
            part.isNotEmpty() && !part[0].isDigit() && part.all { it.isLetterOrDigit() || it == '_' }
        }) { "invalid package name: $packageName" }
        try {
            return generateInternal(customModel, lookup, packageName, className)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Cannot generate custom weighting source: " + ex.message, ex)
        }
    }

    // ------------------------------------------------------------------

    private fun generateInternal(customModel: CustomModel, lookup: EncodedValueLookup,
                                 packageName: String, className: String): String {
        val areas = CustomModel.getAreasAsMap(customModel.getAreas())
        val conditionScope = ExpressionScopes.conditionScope(lookup, areas.keys)
        val valueScope = ExpressionScopes.valueScope(lookup)

        // ---- priority (validation order mirrors ClosureBackend.createParameters / createClazz)
        ClosureBackend.validateValues(customModel.getPriority(), valueScope)
        for (s in customModel.getPriority())
            if (s.operation() == Statement.Op.ADD)
                throw IllegalArgumentException("'priority' statement must not have the operation 'add'")
        val priorityVariables = ClosureBackend.collectVariables(customModel.getPriority(),
                conditionScope, valueScope, ExpressionContext.EDGE, "priority entry")

        // ---- speed (structural check of the first group, exactly like createClazz)
        if (customModel.getSpeed().isEmpty())
            throw IllegalArgumentException("At least one initial statement under 'speed' is required.")
        val firstGroup = CustomModelParser.splitIntoGroup(customModel.getSpeed())[0]
        if (firstGroup.size > 1) {
            val lastSt = firstGroup[firstGroup.size - 1]
            if (lastSt.operation() != Statement.Op.LIMIT || lastSt.keyword() != Statement.Keyword.ELSE)
                throw IllegalArgumentException("The first group needs to end with an 'else' (or contain a single unconditional 'if' statement).")
        } else {
            val firstSt = firstGroup[0]
            if ("true" != firstSt.condition() || firstSt.operation() != Statement.Op.LIMIT || firstSt.keyword() != Statement.Keyword.IF)
                throw IllegalArgumentException("The first group needs to contain a single unconditional 'if' statement (or end with an 'else').")
        }
        ClosureBackend.validateValues(customModel.getSpeed(), valueScope)
        val speedVariables = ClosureBackend.collectVariables(customModel.getSpeed(),
                conditionScope, valueScope, ExpressionContext.EDGE, "speed entry")

        // ---- turn penalty (mirrors createGetTurnPenaltyStatements' checks)
        for (s in customModel.getTurnPenalty()) {
            if (s.operation() == Statement.Op.ADD && s.value().trim().startsWith("-"))
                throw IllegalArgumentException("The value for the 'add' operation must be positive, but was: " + s.value())
            if (s.isBlock())
                throw IllegalArgumentException("'turn_penalty' statement cannot be a block (not yet implemented)")
            if (s.operation() != Statement.Op.ADD)
                throw IllegalArgumentException("'turn_penalty' statement must have the operation 'add' but was: " + s.operation() + " (not yet implemented)")
        }
        ClosureBackend.validateValues(customModel.getTurnPenalty(), valueScope)
        val turnVariables = ClosureBackend.collectVariables(customModel.getTurnPenalty(),
                conditionScope, valueScope, ExpressionContext.TURN_PENALTY, "turn_penalty entry")
        var needTwoDirections = false
        for (name in turnVariables) {
            val enc = ClosureBackend.simplifiedEncodedValue(lookup, name)
            if (enc != null && enc.isStoreTwoDirections || name == CustomModelParser.CHANGE_ANGLE) {
                needTwoDirections = true
                break
            }
        }

        // ---- variable declarations + field bindings (mirror getVariableDeclaration/
        //      getTurnPenaltyVariableDeclaration/createClassTemplate)
        val ctx = GenContext(lookup, areas)
        val priorityVars = LinkedHashMap<String, GenVar>()
        for (name in priorityVariables) priorityVars[name] = ctx.edgeVar(name)
        val speedVars = LinkedHashMap<String, GenVar>()
        for (name in speedVariables) speedVars[name] = ctx.edgeVar(name)
        val turnVars = LinkedHashMap<String, GenVar>()
        for (name in turnVariables) turnVars[name] = ctx.turnVar(name, needTwoDirections)

        // ---- method bodies
        val priorityBody = emitEdgeMethodBody(CustomWeightingHelper.GLOBAL_PRIORITY, priorityVars,
                customModel.getPriority(), conditionScope, valueScope, "priority entry")
        val speedBody = emitEdgeMethodBody(CustomWeightingHelper.GLOBAL_MAX_SPEED, speedVars,
                customModel.getSpeed(), conditionScope, valueScope, "speed entry")
        val turnBody = emitTurnMethodBody(turnVars, customModel.getTurnPenalty(), needTwoDirections,
                conditionScope, valueScope)

        // ---- assemble the file
        val sb = StringBuilder(4096)
        sb.append("// Generated by GraphHopper's CustomWeightingSourceGenerator - DO NOT EDIT.\n")
        sb.append("// Semantics mirror the Janino runtime template of CustomModelParser (GraphHopper, Apache License 2.0).\n")
        sb.append("// Register at app startup: GeneratedWeightingRegistry.register(customModel, Supplier { ").append(className).append("() })\n")
        sb.append("package ").append(packageName).append("\n\n")
        sb.append("import com.graphhopper.routing.ev.*\n")
        sb.append("import com.graphhopper.routing.weighting.custom.CustomWeightingHelper\n")
        sb.append("import com.graphhopper.storage.BaseGraph\n")
        sb.append("import com.graphhopper.util.CustomModel\n")
        sb.append("import com.graphhopper.util.EdgeIteratorState\n")
        sb.append("import com.graphhopper.util.JsonFeature\n")
        if (ctx.usesAreas) {
            sb.append("import com.graphhopper.util.shapes.Polygon\n")
            sb.append("import org.locationtech.jts.geom.Polygonal\n")
            sb.append("import org.locationtech.jts.geom.prep.PreparedPolygon\n")
        }
        sb.append("\nclass ").append(className).append(" : CustomWeightingHelper() {\n")
        for (field in ctx.fields.values)
            sb.append("    ").append(field.declaration).append("\n")
        sb.append("\n")
        sb.append("    override fun init(customModel: CustomModel?, lookup: EncodedValueLookup?, areas: Map<String, JsonFeature>?) {\n")
        sb.append("        super.init(customModel, lookup, areas)\n")
        for (field in ctx.fields.values)
            for (line in field.initLines)
                sb.append("        ").append(line).append("\n")
        sb.append("    }\n\n")
        sb.append("    override fun getPriority(edge: EdgeIteratorState, reverse: Boolean): Double {\n")
        sb.append(priorityBody)
        sb.append("    }\n\n")
        sb.append("    override fun getSpeed(edge: EdgeIteratorState, reverse: Boolean): Double {\n")
        sb.append(speedBody)
        sb.append("    }\n\n")
        sb.append("    override fun getTurnPenalty(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, viaNode: Int, outEdge: Int): Double {\n")
        sb.append(turnBody)
        sb.append("    }\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun emitEdgeMethodBody(initial: Double, vars: LinkedHashMap<String, GenVar>, statements: List<Statement>,
                                   conditionScope: ExpressionScope, valueScope: ExpressionScope, info: String): String {
        val sb = StringBuilder()
        sb.append("        var value = ").append(renderDouble(initial)).append("\n")
        for (v in vars.values)
            if (v.declaration != null) sb.append("        ").append(v.declaration).append("\n")
        val emitter = KotlinExprEmitter(vars)
        emitStatements(sb, statements, emitter, conditionScope, valueScope, ExpressionContext.EDGE, info, "        ")
        sb.append("        return value\n")
        return sb.toString()
    }

    private fun emitTurnMethodBody(vars: LinkedHashMap<String, GenVar>, statements: List<Statement>,
                                   needTwoDirections: Boolean, conditionScope: ExpressionScope,
                                   valueScope: ExpressionScope): String {
        val sb = StringBuilder()
        sb.append("        var value = 0.0\n")
        if (needTwoDirections) {
            // mirrors the performance optimization comment in createGetTurnPenaltyStatements
            sb.append("        val inEdgeReverse = !graph.isAdjNode(inEdge, viaNode)\n")
            sb.append("        val outEdgeReverse = graph.isAdjNode(outEdge, viaNode)\n")
        }
        for (v in vars.values)
            if (v.declaration != null) sb.append("        ").append(v.declaration).append("\n")
        val emitter = KotlinExprEmitter(vars)
        emitStatements(sb, statements, emitter, conditionScope, valueScope, ExpressionContext.TURN_PENALTY,
                "turn_penalty entry", "        ")
        sb.append("        return value\n")
        return sb.toString()
    }

    /** Mirrors CustomModelParser.parseExpressions (statement structure + operations). */
    private fun emitStatements(sb: StringBuilder, list: List<Statement>, emitter: KotlinExprEmitter,
                               conditionScope: ExpressionScope, valueScope: ExpressionScope,
                               context: ExpressionContext, info: String, indent: String) {
        for (statement in list) {
            when (statement.keyword()) {
                Statement.Keyword.ELSE -> {
                    if (!statement.condition().isNullOrEmpty())
                        throw IllegalArgumentException("condition must be empty but was " + statement.condition())
                    sb.append(indent)
                    if (statement.isBlock()) {
                        sb.append("else {\n")
                        emitStatements(sb, statement.doBlock(), emitter, conditionScope, valueScope, context, info, "$indent    ")
                        sb.append(indent).append("}\n")
                    } else {
                        sb.append("else { ").append(operationLine(statement, emitter, valueScope)).append(" }\n")
                    }
                }
                Statement.Keyword.IF, Statement.Keyword.ELSEIF -> {
                    val v = ExpressionValidator.conditionStrict(statement.condition(), conditionScope, context)
                    if (!v.ok)
                        throw IllegalArgumentException(info + " invalid condition \"" + statement.condition() + "\"" +
                                (if (v.invalidMessage == null) "" else ": " + v.invalidMessage))
                    val condition = emitter.emitCondition(v.node!!)
                    sb.append(indent)
                    if (statement.keyword() == Statement.Keyword.ELSEIF) sb.append("else ")
                    if (statement.isBlock()) {
                        sb.append("if (").append(condition).append(") {\n")
                        emitStatements(sb, statement.doBlock(), emitter, conditionScope, valueScope, context, info, "$indent    ")
                        sb.append(indent).append("}\n")
                    } else {
                        sb.append("if (").append(condition).append(") { ")
                                .append(operationLine(statement, emitter, valueScope)).append(" }\n")
                    }
                }
                else -> throw IllegalArgumentException("The statement must be either 'if', 'else_if' or 'else'")
            }
        }
    }

    /** Mirrors Statement.Op.build, with the value expression widened to double like compileValue. */
    private fun operationLine(statement: Statement, emitter: KotlinExprEmitter, valueScope: ExpressionScope): String {
        val vv = ExpressionValidator.valueStrict(statement.value(), valueScope, statement.operation() == Statement.Op.ADD)
        if (!vv.ok) throw IllegalArgumentException(vv.invalidMessage)
        // Statement.Op.build maps the exact string "Infinity" to Double.POSITIVE_INFINITY for 'add'
        val value = if (statement.operation() == Statement.Op.ADD && statement.value() == "Infinity")
            "Double.POSITIVE_INFINITY"
        else
            emitter.emitValue(vv.node!!)
        return when (statement.operation()) {
            Statement.Op.MULTIPLY -> "value *= $value"
            Statement.Op.LIMIT -> "value = kotlin.math.min(value, $value)"
            Statement.Op.ADD -> "value += $value"
            else -> throw IllegalArgumentException("Unsupported operation " + statement.operation())
        }
    }

    // ------------------------------------------------------------------
    // variable environments = the generated code's variable declarations
    // ------------------------------------------------------------------

    private class FieldSpec(val declaration: String, val initLines: List<String>)

    /**
     * A declared variable of a generated method: its static [SemType], the Kotlin expression
     * that references it and (unless referenced inline, like areas) its local declaration.
     */
    private class GenVar(
            val type: SemType,
            val ref: String,
            val declaration: String?,
            val enumType: Class<*>? = null,
            val enumConstants: Set<String> = emptySet()
    )

    private class GenContext(val lookup: EncodedValueLookup, val areas: Map<String, JsonFeature>) {
        val fields = LinkedHashMap<String, FieldSpec>()
        val enumTypeIds = HashMap<Class<*>, Int>()
        var usesAreas = false

        /** Mirrors CustomModelParser.getVariableDeclaration + the area part of createClassTemplate. */
        fun edgeVar(name: String): GenVar {
            if (lookup.hasEncodedValue(name))
                return evEdgeVar(name, lookup.getEncodedValue(name, EncodedValue::class.java), false)
            if (name.startsWith(CustomModelParser.BACKWARD_PREFIX)) {
                val sub = name.substring(CustomModelParser.BACKWARD_PREFIX.length)
                if (lookup.hasEncodedValue(sub))
                    return evEdgeVar(name, lookup.getEncodedValue(sub, EncodedValue::class.java), true)
                throw IllegalArgumentException("Not supported for backward: $sub")
            }
            if (name.startsWith(CustomModelParser.IN_AREA_PREFIX))
                return areaVar(name)
            throw IllegalArgumentException("Not supported $name")
        }

        private fun evEdgeVar(name: String, enc: EncodedValue, backward: Boolean): GenVar {
            val field = bindField(if (backward) name.substring(CustomModelParser.BACKWARD_PREFIX.length) else name, enc)
            val get = if (backward) "edge.get(this.$field)" else "edge.getReverse(this.$field)"
            val getOther = if (backward) "edge.getReverse(this.$field)" else "edge.get(this.$field)"
            val access = "if (reverse) $get else $getOther"
            val id = kotlinId(name)
            // order matters, exactly like getReturnType: Enum, String, Decimal, Boolean, Int
            return when (enc) {
                is EnumEncodedValue<*> -> GenVar(enumSemType(enc.enumType), id,
                        "val $id: ${enumTypeRef(enc.enumType)} = $access",
                        enc.enumType, enumConstantNames(enc))
                is StringEncodedValue -> GenVar(SemType.INT, id, "val $id: Int = $access") // int index, like getInterface
                is DecimalEncodedValue -> GenVar(SemType.DOUBLE, id, "val $id: Double = $access")
                is BooleanEncodedValue -> GenVar(SemType.BOOL, id, "val $id: Boolean = $access")
                is IntEncodedValue -> GenVar(SemType.INT, id, "val $id: Int = $access")
                else -> throw IllegalArgumentException("Unsupported EncodedValue: " + enc.javaClass)
            }
        }

        private fun areaVar(name: String): GenVar {
            if (!JsonFeature.isValidId(name))
                throw IllegalArgumentException("Area has invalid name: $name")
            val id = name.substring(CustomModelParser.IN_AREA_PREFIX.length)
            val feature = areas[id] ?: throw IllegalArgumentException("Area '$id' wasn't found")
            val geometry = feature.getGeometry()
                    ?: throw IllegalArgumentException("Area '$id' does not contain a geometry")
            if (geometry !is Polygonal)
                throw IllegalArgumentException("Currently only type=Polygon is supported for areas but was " + geometry.getGeometryType())
            if (feature.getBBox() != null)
                throw IllegalArgumentException("Bounding box of area $id must be empty")
            usesAreas = true
            fields.getOrPut(name) {
                FieldSpec("private lateinit var $name: Polygon",
                        listOf("this.$name = Polygon(PreparedPolygon(areas!![\"$id\"]!!.getGeometry() as Polygonal))"))
            }
            // referenced inline per occurrence, like the generated CustomWeightingHelper.in(...) call
            return GenVar(SemType.BOOL, "CustomWeightingHelper.`in`(this.$name, edge)", null)
        }

        /** Mirrors CustomModelParser.getTurnPenaltyVariableDeclaration (incl. its check order). */
        fun turnVar(name: String, needTwoDirections: Boolean): GenVar {
            if (name == CustomModelParser.CHANGE_ANGLE) {
                // the Janino class binds orientation_enc via the lookup - missing EV = rejection
                if (!lookup.hasEncodedValue(Orientation.KEY))
                    throw IllegalArgumentException("Variable not supported: " + Orientation.KEY)
                bindField(Orientation.KEY, lookup.getEncodedValue(Orientation.KEY, EncodedValue::class.java))
                // calcChangeAngle expects the orientation slot at the viaNode side of outEdge;
                // since outEdgeReverse means direction of travel, invert it here (see Janino template)
                return GenVar(SemType.DOUBLE, "change_angle",
                        "val change_angle: Double = CustomWeightingHelper.calcChangeAngle(edgeIntAccess, this.orientation_enc, inEdge, inEdgeReverse, outEdge, !outEdgeReverse)")
            }
            if (name == CustomModelParser.STREET_NAME)
                return GenVar(SemType.STRING, "street_name",
                        "val street_name: String = graph.getEdgeIteratorState(outEdge, Int.MIN_VALUE)!!.name")
            if (name == CustomModelParser.PREV_PREFIX + CustomModelParser.STREET_NAME)
                return GenVar(SemType.STRING, "prev_street_name",
                        "val prev_street_name: String = graph.getEdgeIteratorState(inEdge, Int.MIN_VALUE)!!.name")
            if (lookup.hasEncodedValue(name))
                return evTurnVar(name, lookup.getEncodedValue(name, EncodedValue::class.java), false, needTwoDirections)
            if (name.startsWith(CustomModelParser.PREV_PREFIX)) {
                val sub = name.substring(CustomModelParser.PREV_PREFIX.length)
                if (lookup.hasEncodedValue(sub))
                    return evTurnVar(name, lookup.getEncodedValue(sub, EncodedValue::class.java), true, needTwoDirections)
                throw IllegalArgumentException("Not supported for prev: $sub")
            }
            throw IllegalArgumentException("Not supported for turn_penalty: $name")
        }

        private fun evTurnVar(name: String, enc: EncodedValue, prevSide: Boolean, needTwoDirections: Boolean): GenVar {
            val field = bindField(if (prevSide) name.substring(CustomModelParser.PREV_PREFIX.length) else name, enc)
            val reverseExpr = if (needTwoDirections) (if (prevSide) "inEdgeReverse" else "outEdgeReverse") else "false"
            val edgeExpr = if (prevSide) "inEdge" else "outEdge"
            val id = kotlinId(name)
            fun access(method: String) = "this.$field.$method($reverseExpr, $edgeExpr, edgeIntAccess)"
            // order matters, exactly like getTurnPenaltyAccessor: Enum, Boolean, Decimal, Int
            return when (enc) {
                is EnumEncodedValue<*> -> GenVar(enumSemType(enc.enumType), id,
                        "val $id: ${enumTypeRef(enc.enumType)} = ${access("getEnum")}",
                        enc.enumType, enumConstantNames(enc))
                is BooleanEncodedValue -> GenVar(SemType.BOOL, id, "val $id: Boolean = ${access("getBool")}")
                is DecimalEncodedValue -> GenVar(SemType.DOUBLE, id, "val $id: Double = ${access("getDecimal")}")
                is IntEncodedValue -> GenVar(SemType.INT, id, "val $id: Int = ${access("getInt")}") // incl. StringEncodedValue: int index
                else -> throw IllegalArgumentException("Unsupported EncodedValue for turn penalty: " + enc.javaClass)
            }
        }

        /** Registers the `<name>_enc` field + its init binding; mirrors createClassTemplate/getInterface. */
        private fun bindField(name: String, enc: EncodedValue): String {
            val field = name + "_enc"
            fields.getOrPut(field) {
                // order matters, exactly like getInterface: String first (int index), then the interfaces
                val spec = when (enc) {
                    is EnumEncodedValue<*> -> FieldSpec(
                            "private lateinit var $field: EnumEncodedValue<${enumTypeRef(enc.enumType)}>",
                            listOf("this.$field = lookup!!.getEnumEncodedValue(\"$name\", ${enumTypeRef(enc.enumType)}::class.java)"))
                    is StringEncodedValue -> FieldSpec(
                            "private lateinit var $field: IntEncodedValue",
                            listOf("this.$field = lookup!!.getEncodedValue(\"$name\", IntEncodedValue::class.java)"))
                    is DecimalEncodedValue -> FieldSpec(
                            "private lateinit var $field: DecimalEncodedValue",
                            listOf("this.$field = lookup!!.getDecimalEncodedValue(\"$name\")"))
                    is BooleanEncodedValue -> FieldSpec(
                            "private lateinit var $field: BooleanEncodedValue",
                            listOf("this.$field = lookup!!.getBooleanEncodedValue(\"$name\")"))
                    is IntEncodedValue -> FieldSpec(
                            "private lateinit var $field: IntEncodedValue",
                            listOf("this.$field = lookup!!.getIntEncodedValue(\"$name\")"))
                    else -> throw IllegalArgumentException("Unsupported EncodedValue: " + enc.javaClass)
                }
                spec
            }
            return field
        }

        private fun enumSemType(cl: Class<*>): SemType =
                SemType.ENUM(enumTypeIds.getOrPut(cl) { enumTypeIds.size }, cl.simpleName)

        private fun enumConstantNames(enc: EnumEncodedValue<*>): Set<String> =
                enc.getValues().mapTo(LinkedHashSet()) { (it as Enum<*>).name }
    }

    /** Kotlin type reference of an enum class; mirrors getReturnType's simpleName-for-builtins rule. */
    private fun enumTypeRef(cl: Class<*>): String =
            if (cl.getPackage() == EnumEncodedValue::class.java.getPackage()) cl.simpleName
            else cl.canonicalName

    // ------------------------------------------------------------------
    // expression unparser: validated ExprNode -> Kotlin source, typing/promotion
    // decisions transcribed 1:1 from TypedCompiler (stage 4)
    // ------------------------------------------------------------------

    private class KotlinExprEmitter(private val variables: Map<String, GenVar>) {

        /** [cst] carries literal constants so negation/widening can be folded into typed Kotlin literals. */
        private class Out(val type: SemType, val src: String, val cst: Any? = null)

        fun emitCondition(root: ExprNode): String {
            val o = emit(root)
            if (o.type !== SemType.BOOL) err("condition must be a boolean expression")
            return o.src
        }

        fun emitValue(root: ExprNode): String {
            val o = emit(root)
            if (!o.type.isNumeric) err("value must be a numeric expression")
            return toDouble(o).src
        }

        private fun err(message: String): Nothing = throw IllegalArgumentException(message)

        private fun emit(node: ExprNode): Out = when (node) {
            is ExprNode.Paren -> emit(node.inner)
            is ExprNode.Literal -> literal(node)
            is ExprNode.Name -> name(node)
            is ExprNode.Unary -> unary(node)
            is ExprNode.Binary -> binary(node)
            is ExprNode.Call -> call(node)
            is ExprNode.Ternary -> err("ternary operator not allowed")
        }

        private fun name(node: ExprNode.Name): Out {
            val n = node.single ?: err("identifier $node invalid")
            val v = variables[n] ?: err("'$n' not available")
            return Out(v.type, v.ref)
        }

        private fun literal(node: ExprNode.Literal): Out = when (node.kind) {
            LiteralKind.NUMBER -> numericConst(TypedCompiler.parseJavaNumericLiteral(node.text, false))
            LiteralKind.STRING -> {
                val s = TypedCompiler.unescape(node.text.substring(1, node.text.length - 1))
                Out(SemType.STRING, renderString(s), s)
            }
            LiteralKind.CHAR -> {
                val c = TypedCompiler.unescape(node.text.substring(1, node.text.length - 1))[0].code
                Out(SemType.INT, renderInt(c), c)
            }
            LiteralKind.BOOLEAN -> Out(SemType.BOOL, node.text, node.text == "true")
            LiteralKind.NULL -> Out(SemType.NULL, "null")
        }

        private fun numericConst(v: Any): Out = when (v) {
            is Int -> Out(SemType.INT, renderInt(v), v)
            is Long -> Out(SemType.LONG, renderLong(v), v)
            is Float -> Out(SemType.FLOAT, renderFloat(v), v)
            else -> Out(SemType.DOUBLE, renderDouble(v as Double), v)
        }

        private fun unary(node: ExprNode.Unary): Out = when (node.op) {
            "!" -> {
                val o = emit(node.operand)
                if (o.type !== SemType.BOOL) err("operator ! cannot be applied to a non-boolean operand")
                Out(SemType.BOOL, "(!${o.src})")
            }
            "-" -> {
                // Java treats "-<decimal literal>" as one literal: -2147483648 is a valid int
                val operand = node.operand
                if (operand is ExprNode.Literal && operand.kind == LiteralKind.NUMBER) {
                    numericConst(TypedCompiler.parseJavaNumericLiteral(operand.text, true))
                } else {
                    val o = emit(node.operand)
                    when {
                        o.cst is Int -> numericConst(-o.cst)       // wraps at Int.MIN_VALUE, like Java
                        o.cst is Long -> numericConst(-o.cst)
                        o.cst is Float -> numericConst(-o.cst)
                        o.cst is Double -> numericConst(-o.cst)
                        o.type === SemType.INT || o.type === SemType.LONG
                                || o.type === SemType.FLOAT || o.type === SemType.DOUBLE ->
                            Out(o.type, "(-${o.src})")
                        else -> err("operator - cannot be applied to a non-numeric operand")
                    }
                }
            }
            else -> err("operator ${node.op} not allowed")
        }

        private fun binary(node: ExprNode.Binary): Out = when (node.op) {
            "&&", "||" -> {
                val l = emit(node.lhs)
                val r = emit(node.rhs)
                if (l.type !== SemType.BOOL || r.type !== SemType.BOOL)
                    err("operator ${node.op} requires boolean operands")
                Out(SemType.BOOL, "(${l.src} ${node.op} ${r.src})")
            }
            "==", "!=" -> equality(node)
            "<", "<=", ">", ">=" -> {
                val l = emit(node.lhs)
                val r = emit(node.rhs)
                if (!l.type.isNumeric || !r.type.isNumeric)
                    err("operator ${node.op} requires numeric operands")
                comparison(node.op, l, r)
            }
            "+", "-", "*", "/", "%" -> {
                val l = emit(node.lhs)
                val r = emit(node.rhs)
                if (!l.type.isNumeric || !r.type.isNumeric)
                    err("operator ${node.op} cannot be applied to non-numeric operands")
                arithmetic(node.op, l, r)
            }
            "<<", ">>", ">>>" -> {
                val l = emit(node.lhs)
                val r = emit(node.rhs)
                if (!l.type.isIntegral || !r.type.isIntegral)
                    err("shift operator ${node.op} requires integral operands")
                val op = when (node.op) {
                    "<<" -> "shl"
                    ">>" -> "shr"
                    else -> "ushr"
                }
                // result type is the (promoted) left operand; Kotlin's shift distance is an Int
                // whose low bits are used, exactly like Java's masked distance
                val distance = if (r.type === SemType.INT) r else toInt(r)
                Out(l.type, "(${l.src} $op ${distance.src})")
            }
            "&", "|", "^" -> {
                val l = emit(node.lhs)
                val r = emit(node.rhs)
                val op = when (node.op) {
                    "&" -> "and"
                    "|" -> "or"
                    else -> "xor"
                }
                if (l.type === SemType.BOOL && r.type === SemType.BOOL)
                    Out(SemType.BOOL, "(${l.src} $op ${r.src})") // non-short-circuit, like Java's boolean & |
                else if (l.type.isIntegral && r.type.isIntegral) {
                    val t = promoted(l.type, r.type)
                    Out(t, "(${convert(l, t).src} $op ${convert(r, t).src})")
                } else
                    err("operator ${node.op} requires two boolean or two integral operands")
            }
            else -> err("operator ${node.op} not allowed")
        }

        private fun equality(node: ExprNode.Binary): Out {
            val negate = node.op == "!="
            val op = if (negate) "!=" else "=="
            // enum-constant form "toll == NO": mirrors the Janino pipeline's replacement with
            // the qualified constant of the LEFT-hand side's enum type
            val lName = (node.lhs as? ExprNode.Name)?.single
            val rName = (node.rhs as? ExprNode.Name)?.single
            if (lName != null && rName != null && rName.uppercase() == rName) {
                val v = variables[lName]
                if (v != null) {
                    if (v.type !is SemType.ENUM) err("'$lName' cannot be compared with '$rName'")
                    if (rName !in v.enumConstants) err("'$rName' is not a valid value of '$lName'")
                    return Out(SemType.BOOL, "(${v.ref} $op ${enumTypeRef(v.enumType!!)}.$rName)")
                }
            }

            val l = emit(node.lhs)
            val r = emit(node.rhs)
            val lt = l.type
            val rt = r.type
            return Out(SemType.BOOL, when {
                lt.isNumeric && rt.isNumeric -> comparison(node.op, l, r).src
                lt === SemType.BOOL && rt === SemType.BOOL -> "(${l.src} $op ${r.src})"
                lt is SemType.ENUM && rt is SemType.ENUM ->
                    if (lt.id == rt.id) "(${l.src} $op ${r.src})" // Kotlin enum == is identity, like Java
                    else err("incomparable enum types ${lt.typeName} and ${rt.typeName}")
                // Java's String == is reference identity (literals are interned in both languages)
                lt === SemType.STRING && rt === SemType.STRING -> "(${l.src} ${if (negate) "!==" else "==="} ${r.src})"
                lt === SemType.STRING && rt === SemType.NULL -> "(${l.src} $op null)"
                lt === SemType.NULL && rt === SemType.STRING -> "(${r.src} $op null)"
                lt is SemType.ENUM && rt === SemType.NULL -> (negate).toString() // encoded enum values are never null
                lt === SemType.NULL && rt is SemType.ENUM -> (negate).toString()
                lt === SemType.NULL && rt === SemType.NULL -> (!negate).toString()
                else -> err("incomparable operand types for ${node.op}")
            })
        }

        private fun call(node: ExprNode.Call): Out {
            if (node.target.size != 1) err("${node.method} is an illegal method")
            val target = node.target[0]

            if (target == "Math") {
                if (node.args.size != 1) err("cannot resolve Math.${node.method} with ${node.args.size} arguments")
                val arg = emit(node.args[0])
                if (!arg.type.isNumeric) err("Math.${node.method} requires a numeric argument")
                return when (node.method) {
                    // kotlin.math has the same IEEE-754 semantics and the same overloads as java.lang.Math
                    "sqrt" -> Out(SemType.DOUBLE, "kotlin.math.sqrt(${toDouble(arg).src})")
                    "abs" -> Out(arg.type, "kotlin.math.abs(${arg.src})")
                    else -> err("cannot resolve method Math.${node.method}")
                }
            }

            // the receiver must be a DECLARED variable, exactly like in TypedCompiler
            val recv = variables[target] ?: err("cannot resolve '$target'")
            return when (node.method) {
                "ordinal" -> {
                    if (node.args.isNotEmpty()) err("cannot resolve ordinal(...) with arguments")
                    if (recv.type !is SemType.ENUM) err("cannot call ordinal() on '$target'")
                    Out(SemType.INT, "${recv.ref}.ordinal")
                }
                "isRightHandTraffic" -> {
                    if (node.args.isNotEmpty()) err("cannot resolve isRightHandTraffic(...) with arguments")
                    if (recv.enumType != Country::class.java) err("cannot call isRightHandTraffic() on '$target'")
                    Out(SemType.BOOL, "${recv.ref}.isRightHandTraffic")
                }
                "contains" -> {
                    if (node.args.size != 1) err("cannot resolve contains(...) with ${node.args.size} arguments")
                    if (recv.type !== SemType.STRING) err("cannot call contains() on '$target'")
                    val arg = emit(node.args[0])
                    val argSrc = when (arg.type) {
                        SemType.STRING -> arg.src
                        SemType.NULL -> "null!!" // compiles in Java, NPEs at runtime - same here
                        else -> err("contains() requires a String argument")
                    }
                    Out(SemType.BOOL, "${recv.ref}.contains($argSrc)")
                }
                "equals" -> {
                    if (node.args.size != 1) err("cannot resolve equals(...) with ${node.args.size} arguments")
                    val arg = emit(node.args[0])
                    when (recv.type) {
                        // equals(Any?) accepts every operand in Kotlin just like equals(Object) in
                        // Java; mismatched types yield false at runtime in both languages
                        SemType.STRING, is SemType.ENUM -> Out(SemType.BOOL, "${recv.ref}.equals(${arg.src})")
                        else -> err("cannot call equals() on the primitive '$target'")
                    }
                }
                else -> err("${node.method} is an illegal method")
            }
        }

        // ---- numeric promotion, made explicit for Kotlin

        private fun promoted(a: SemType, b: SemType): SemType = when {
            a === SemType.DOUBLE || b === SemType.DOUBLE -> SemType.DOUBLE
            a === SemType.FLOAT || b === SemType.FLOAT -> SemType.FLOAT
            a === SemType.LONG || b === SemType.LONG -> SemType.LONG
            else -> SemType.INT
        }

        private fun comparison(op: String, l: Out, r: Out): Out {
            val t = promoted(l.type, r.type)
            return Out(SemType.BOOL, "(${convert(l, t).src} $op ${convert(r, t).src})")
        }

        private fun arithmetic(op: String, l: Out, r: Out): Out {
            val t = promoted(l.type, r.type) // int/int stays Int: Kotlin's / truncates like Java's
            return Out(t, "(${convert(l, t).src} $op ${convert(r, t).src})")
        }

        private fun convert(o: Out, target: SemType): Out = when (target) {
            SemType.DOUBLE -> toDouble(o)
            SemType.FLOAT -> toFloat(o)
            SemType.LONG -> toLong(o)
            else -> o // INT operands are never narrowed
        }

        private fun toDouble(o: Out): Out = when {
            o.type === SemType.DOUBLE -> o
            o.cst is Int -> numericConst(o.cst.toDouble())
            o.cst is Long -> numericConst(o.cst.toDouble())
            o.cst is Float -> numericConst(o.cst.toDouble())
            else -> Out(SemType.DOUBLE, "${o.src}.toDouble()")
        }

        private fun toFloat(o: Out): Out = when {
            o.type === SemType.FLOAT -> o
            o.cst is Int -> numericConst(o.cst.toFloat())
            o.cst is Long -> numericConst(o.cst.toFloat())
            else -> Out(SemType.FLOAT, "${o.src}.toFloat()")
        }

        private fun toLong(o: Out): Out = when {
            o.type === SemType.LONG -> o
            o.cst is Int -> numericConst(o.cst.toLong())
            else -> Out(SemType.LONG, "${o.src}.toLong()")
        }

        private fun toInt(o: Out): Out = when {
            o.type === SemType.INT -> o
            o.cst is Long -> numericConst(o.cst.toInt())
            else -> Out(SemType.INT, "${o.src}.toInt()")
        }
    }

    // ------------------------------------------------------------------
    // Kotlin literal rendering (canonical; Kotlin types -2147483648 as Long and has no octal)
    // ------------------------------------------------------------------

    private fun renderInt(v: Int): String = when {
        v == Int.MIN_VALUE -> "Int.MIN_VALUE"
        v < 0 -> "($v)"
        else -> v.toString()
    }

    private fun renderLong(v: Long): String = when {
        v == Long.MIN_VALUE -> "Long.MIN_VALUE"
        v < 0 -> "(${v}L)"
        else -> "${v}L"
    }

    private fun renderFloat(v: Float): String = when {
        v.isNaN() -> "Float.NaN"
        v == Float.POSITIVE_INFINITY -> "Float.POSITIVE_INFINITY"
        v == Float.NEGATIVE_INFINITY -> "Float.NEGATIVE_INFINITY"
        v.toString().startsWith("-") -> "(${v}f)"
        else -> "${v}f"
    }

    private fun renderDouble(v: Double): String = when {
        v.isNaN() -> "Double.NaN"
        v == Double.POSITIVE_INFINITY -> "Double.POSITIVE_INFINITY"
        v == Double.NEGATIVE_INFINITY -> "Double.NEGATIVE_INFINITY"
        v.toString().startsWith("-") -> "($v)"
        else -> v.toString()
    }

    internal fun renderString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '$' -> sb.append("\\$")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c.isISOControl()) sb.append("\\u").append(String.format("%04X", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** Backticks identifiers that are Kotlin hard keywords (EV names are lowercase snake case). */
    private fun kotlinId(name: String): String = if (name in KOTLIN_HARD_KEYWORDS) "`$name`" else name

    private val KOTLIN_HARD_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while")
}
