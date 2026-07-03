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
package com.graphhopper.routing.weighting.custom

import com.graphhopper.json.MinMax
import com.graphhopper.json.Statement
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.Orientation
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.weighting.custom.expression.BoolCell
import com.graphhopper.routing.weighting.custom.expression.BoolCellExpr
import com.graphhopper.routing.weighting.custom.expression.BoolExpr
import com.graphhopper.routing.weighting.custom.expression.CompiledGroup
import com.graphhopper.routing.weighting.custom.expression.CompiledStatement
import com.graphhopper.routing.weighting.custom.expression.DoubleCell
import com.graphhopper.routing.weighting.custom.expression.DoubleCellExpr
import com.graphhopper.routing.weighting.custom.expression.DoubleConst
import com.graphhopper.routing.weighting.custom.expression.ExpressionContext
import com.graphhopper.routing.weighting.custom.expression.ExpressionScope
import com.graphhopper.routing.weighting.custom.expression.ExpressionScopes
import com.graphhopper.routing.weighting.custom.expression.ExpressionValidator
import com.graphhopper.routing.weighting.custom.expression.IntCell
import com.graphhopper.routing.weighting.custom.expression.IntCellExpr
import com.graphhopper.routing.weighting.custom.expression.IntExpr
import com.graphhopper.routing.weighting.custom.expression.SemType
import com.graphhopper.routing.weighting.custom.expression.StatementProgram
import com.graphhopper.routing.weighting.custom.expression.StringCell
import com.graphhopper.routing.weighting.custom.expression.StringCellExpr
import com.graphhopper.routing.weighting.custom.expression.TypedCompiler
import com.graphhopper.routing.weighting.custom.expression.TypedEnv
import com.graphhopper.routing.weighting.custom.expression.TypedVariable
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.CustomModel
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.Helper
import com.graphhopper.util.JsonFeature
import com.graphhopper.util.Parameters as GHParameters
import com.graphhopper.util.shapes.Polygon
import org.locationtech.jts.geom.Polygonal
import org.locationtech.jts.geom.prep.PreparedPolygon

/**
 * The closure-composer [CustomWeightingBackend] (stage 4 of the custom-model platform work):
 * compiles the shared expression front-end's AST (package `expression`) once per request
 * into a DAG of typed function objects — NO runtime classloading, NO Janino — for platforms
 * where runtime codegen is impossible (Android, iOS AOT).
 *
 * Behavior contract: bit-identical results with [JaninoBackend] (locked by
 * ClosureBackendDifferentialTest) and identical accept/reject decisions, including the
 * checks the Janino pipeline only performs while generating/compiling the helper class.
 * The composition steps below therefore mirror `CustomModelParser.createClazz` and the
 * generated `getSpeed`/`getPriority`/`getTurnPenalty` bodies 1:1:
 *
 *  - every guessed variable is "declared" as a loader that reads its encoded value once
 *    per evaluated edge/turn into a primitive cell (like the generated local variables)
 *  - conditions/values become typed evaluator nodes with exact Java semantics (see
 *    [TypedCompiler]); `in_area` references evaluate `CustomWeightingHelper.in` inline
 *  - the statement lists become [StatementProgram]s: sequential if/else_if/else groups
 *    folding multiply_by/limit_to/add into a double accumulator
 *  - min/max calculators mirror `CustomWeightingHelper.calcMaxSpeed`/`calcMaxPriority` +
 *    `FindMinMax`, but evaluate value expressions with the Janino-free stage-3 evaluator
 *
 * Like Janino's per-request helper instance, the returned [CustomWeighting.Parameters] own
 * their (thread-confined) mutable cells: each call composes a fresh program. The evaluation
 * hot path is allocation-free; only `in_area` (JTS) and `street_name` (KV storage) perform
 * the same allocations the generated Janino code performs.
 *
 * This backend is NOT the production default (Janino stays); select it explicitly via
 * [CustomWeightingBackends.default].
 */
object ClosureBackend : CustomWeightingBackend {

    override fun createParameters(customModel: CustomModel, lookup: EncodedValueLookup): CustomWeighting.Parameters {
        val areas = CustomModel.getAreasAsMap(customModel.getAreas())
        val speedMapping: CustomWeighting.EdgeToDoubleMapping
        val priorityMapping: CustomWeighting.EdgeToDoubleMapping
        val turnPenaltyMapping: CustomWeighting.TurnPenaltyMapping
        try {
            val conditionScope = ExpressionScopes.conditionScope(lookup, areas.keys)
            val valueScope = ExpressionScopes.valueScope(lookup)

            // ---- priority (mirrors createClazz: value expressions first, then 'add' check, then conditions)
            validateValues(customModel.getPriority(), valueScope)
            for (s in customModel.getPriority())
                if (s.operation() == Statement.Op.ADD)
                    throw IllegalArgumentException("'priority' statement must not have the operation 'add'")
            priorityMapping = compileEdgeMapping(customModel.getPriority(), CustomWeightingHelper.GLOBAL_PRIORITY,
                    "priority entry", lookup, areas, conditionScope, valueScope)

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
            validateValues(customModel.getSpeed(), valueScope)
            speedMapping = compileEdgeMapping(customModel.getSpeed(), CustomWeightingHelper.GLOBAL_MAX_SPEED,
                    "speed entry", lookup, areas, conditionScope, valueScope)

            // ---- turn penalty (mirrors createGetTurnPenaltyStatements' checks)
            for (s in customModel.getTurnPenalty()) {
                if (s.operation() == Statement.Op.ADD && s.value().trim().startsWith("-"))
                    throw IllegalArgumentException("The value for the 'add' operation must be positive, but was: " + s.value())
                if (s.isBlock())
                    throw IllegalArgumentException("'turn_penalty' statement cannot be a block (not yet implemented)")
                if (s.operation() != Statement.Op.ADD)
                    throw IllegalArgumentException("'turn_penalty' statement must have the operation 'add' but was: " + s.operation() + " (not yet implemented)")
            }
            validateValues(customModel.getTurnPenalty(), valueScope)
            turnPenaltyMapping = compileTurnMapping(customModel.getTurnPenalty(), "turn_penalty entry",
                    lookup, conditionScope, valueScope)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Cannot compile expression: " + ex.message, ex)
        }

        return CustomWeighting.Parameters(
                speedMapping, { calcMaxSpeed(customModel, lookup) },
                priorityMapping, { calcMaxPriority(customModel, lookup) },
                turnPenaltyMapping,
                customModel.getDistanceInfluence() ?: 0.0,
                customModel.getHeadingPenalty() ?: GHParameters.Routing.DEFAULT_HEADING_PENALTY)
    }

    // ------------------------------------------------------------------
    // value-expression validation (mirrors ValueExpressionVisitor.findVariables incl.
    // its group-structure rules)
    // ------------------------------------------------------------------

    private fun validateValues(statements: List<Statement>, valueScope: ExpressionScope) {
        for (group in CustomModelParser.splitIntoGroup(statements)) validateValuesForGroup(group, valueScope)
    }

    private fun validateValuesForGroup(group: List<Statement>, valueScope: ExpressionScope) {
        if (group.isEmpty() || Statement.Keyword.IF != group[0].keyword())
            throw IllegalArgumentException("Every group of statements must start with an if-statement")
        val first = group[0]
        if (first.condition().trim() == "true") {
            if (first.isBlock()) {
                for (sub in CustomModelParser.splitIntoGroup(first.doBlock())) validateValuesForGroup(sub, valueScope)
            } else {
                checkValue(first, valueScope)
            }
            if (group.size > 1)
                throw IllegalArgumentException("Only one statement allowed for an unconditional statement")
        } else {
            for (st in group) {
                if (st.isBlock()) {
                    for (sub in CustomModelParser.splitIntoGroup(st.doBlock())) validateValuesForGroup(sub, valueScope)
                } else {
                    checkValue(st, valueScope)
                }
            }
        }
    }

    private fun checkValue(st: Statement, valueScope: ExpressionScope) {
        val v = ExpressionValidator.valueStrict(st.value(), valueScope, st.operation() == Statement.Op.ADD)
        if (!v.ok) throw IllegalArgumentException(v.invalidMessage)
    }

    // ------------------------------------------------------------------
    // program composition
    // ------------------------------------------------------------------

    private fun compileEdgeMapping(statements: List<Statement>, initial: Double, info: String,
                                   lookup: EncodedValueLookup, areas: Map<String, JsonFeature>,
                                   conditionScope: ExpressionScope, valueScope: ExpressionScope): CustomWeighting.EdgeToDoubleMapping {
        val guessed = collectVariables(statements, conditionScope, valueScope, ExpressionContext.EDGE, info)
        val env = EdgeEnv(lookup, areas)
        for (name in guessed) env.declare(name)
        val groups = compileGroups(statements, env, ExpressionContext.EDGE, conditionScope, valueScope, info)
        return ClosureEdgeMapping(env.current, env.loaders.toTypedArray(), StatementProgram(groups, initial))
    }

    private fun compileTurnMapping(statements: List<Statement>, info: String, lookup: EncodedValueLookup,
                                   conditionScope: ExpressionScope, valueScope: ExpressionScope): CustomWeighting.TurnPenaltyMapping {
        val guessed = collectVariables(statements, conditionScope, valueScope, ExpressionContext.TURN_PENALTY, info)
        // mirrors createGetTurnPenaltyStatements: any two-direction encoded value (or change_angle)
        // switches ALL turn variables to the direction-aware accessors
        var needTwoDirections = false
        for (name in guessed) {
            val enc = simplifiedEncodedValue(lookup, name)
            if (enc != null && enc.isStoreTwoDirections || name == CustomModelParser.CHANGE_ANGLE) {
                needTwoDirections = true
                break
            }
        }
        val env = TurnEnv(lookup, needTwoDirections)
        for (name in guessed) env.declare(name)
        val groups = compileGroups(statements, env, ExpressionContext.TURN_PENALTY, conditionScope, valueScope, info)
        return ClosureTurnMapping(env.loaders.toTypedArray(), StatementProgram(groups, 0.0), needTwoDirections)
    }

    /** Mirrors CustomModelParser.createSimplifiedLookup. */
    private fun simplifiedEncodedValue(lookup: EncodedValueLookup, key: String): EncodedValue? = when {
        key == CustomModelParser.STREET_NAME || key == CustomModelParser.PREV_PREFIX + CustomModelParser.STREET_NAME -> null
        key.startsWith(CustomModelParser.BACKWARD_PREFIX) ->
            lookup.getEncodedValue(key.substring(CustomModelParser.BACKWARD_PREFIX.length), EncodedValue::class.java)
        key.startsWith(CustomModelParser.PREV_PREFIX) ->
            lookup.getEncodedValue(key.substring(CustomModelParser.PREV_PREFIX.length), EncodedValue::class.java)
        lookup.hasEncodedValue(key) -> lookup.getEncodedValue(key, EncodedValue::class.java)
        else -> null
    }

    /**
     * Validates all conditions/values of the section and returns the union of their guessed
     * variables — the set the Janino back-end declares at the start of the generated method.
     */
    private fun collectVariables(statements: List<Statement>, conditionScope: ExpressionScope,
                                 valueScope: ExpressionScope, context: ExpressionContext, info: String): LinkedHashSet<String> {
        val variables = LinkedHashSet<String>()
        fun walk(list: List<Statement>) {
            for (st in list) {
                if (st.keyword() != Statement.Keyword.ELSE) {
                    val v = ExpressionValidator.conditionStrict(st.condition(), conditionScope, context)
                    if (!v.ok)
                        throw IllegalArgumentException(info + " invalid condition \"" + st.condition() + "\"" +
                                (if (v.invalidMessage == null) "" else ": " + v.invalidMessage))
                    variables.addAll(v.guessedVariables)
                }
                if (st.isBlock()) {
                    walk(st.doBlock())
                } else {
                    val vv = ExpressionValidator.valueStrict(st.value(), valueScope, st.operation() == Statement.Op.ADD)
                    if (!vv.ok) throw IllegalArgumentException(vv.invalidMessage)
                    variables.addAll(vv.guessedVariables)
                }
            }
        }
        walk(statements)
        return variables
    }

    private fun compileGroups(list: List<Statement>, env: ProgramEnv, context: ExpressionContext,
                              conditionScope: ExpressionScope, valueScope: ExpressionScope, info: String): Array<CompiledGroup> {
        val groups = CustomModelParser.splitIntoGroup(list)
        return Array(groups.size) { gi ->
            val group = groups[gi]
            CompiledGroup(Array(group.size) { si ->
                compileStatement(group[si], env, context, conditionScope, valueScope, info)
            })
        }
    }

    private fun compileStatement(st: Statement, env: ProgramEnv, context: ExpressionContext,
                                 conditionScope: ExpressionScope, valueScope: ExpressionScope, info: String): CompiledStatement {
        val condition: BoolExpr?
        if (st.keyword() == Statement.Keyword.ELSE) {
            if (!Helper.isEmpty(st.condition()))
                throw IllegalArgumentException("condition must be empty but was " + st.condition())
            condition = null
        } else {
            val v = ExpressionValidator.conditionStrict(st.condition(), conditionScope, context)
            if (!v.ok)
                throw IllegalArgumentException(info + " invalid condition \"" + st.condition() + "\"" +
                        (if (v.invalidMessage == null) "" else ": " + v.invalidMessage))
            condition = TypedCompiler.compileCondition(v.node!!, env)
        }
        if (st.isBlock())
            return CompiledStatement.Block(condition, compileGroups(st.doBlock(), env, context, conditionScope, valueScope, info))

        val vv = ExpressionValidator.valueStrict(st.value(), valueScope, st.operation() == Statement.Op.ADD)
        if (!vv.ok) throw IllegalArgumentException(vv.invalidMessage)
        // Statement.Op.build maps the exact string "Infinity" to Double.POSITIVE_INFINITY for 'add'
        val value = if (st.operation() == Statement.Op.ADD && st.value() == "Infinity") DoubleConst(Double.POSITIVE_INFINITY)
        else TypedCompiler.compileValue(vv.node!!, env)
        val op = when (st.operation()) {
            Statement.Op.MULTIPLY -> CompiledStatement.OP_MULTIPLY
            Statement.Op.LIMIT -> CompiledStatement.OP_LIMIT
            Statement.Op.ADD -> CompiledStatement.OP_ADD
            else -> throw IllegalArgumentException("Unsupported operation " + st.operation())
        }
        return CompiledStatement.Leaf(condition, op, value)
    }

    // ------------------------------------------------------------------
    // variable environments = the generated code's variable declarations
    // ------------------------------------------------------------------

    private abstract class ProgramEnv : TypedEnv {
        private val variables = LinkedHashMap<String, TypedVariable>()
        private val enumTypeIds = HashMap<Class<*>, Int>()
        private val literalPool = HashMap<String, String>()

        override fun variable(name: String): TypedVariable? = variables[name]

        override fun internLiteral(value: String): String = literalPool.getOrPut(value) { value }

        fun declare(name: String) {
            if (!variables.containsKey(name)) variables[name] = create(name)
        }

        protected abstract fun create(name: String): TypedVariable

        protected fun enumVariable(enc: EnumEncodedValue<*>, node: IntExpr): TypedVariable {
            val constants = enc.getValues()
            val constantOrdinals = HashMap<String, Int>(constants.size * 2)
            for (c in constants) constantOrdinals[(c as Enum<*>).name] = c.ordinal
            val boolProperties = if (enc.enumType == Country::class.java)
                mapOf("isRightHandTraffic" to BooleanArray(constants.size) { (constants[it] as Country).isRightHandTraffic })
            else emptyMap()
            val id = enumTypeIds.getOrPut(enc.enumType) { enumTypeIds.size }
            return TypedVariable(SemType.ENUM(id, enc.enumType.simpleName), node, constantOrdinals, boolProperties)
        }
    }

    private class EdgeEnv(private val lookup: EncodedValueLookup, private val areas: Map<String, JsonFeature>) : ProgramEnv() {
        val current = CurrentEdge()
        val loaders = ArrayList<EdgeLoader>()

        /** Mirrors CustomModelParser.getVariableDeclaration + the area part of createClassTemplate. */
        override fun create(name: String): TypedVariable {
            if (lookup.hasEncodedValue(name))
                return encodedValueVariable(lookup.getEncodedValue(name, EncodedValue::class.java), false)
            if (name.startsWith(CustomModelParser.BACKWARD_PREFIX)) {
                val sub = name.substring(CustomModelParser.BACKWARD_PREFIX.length)
                if (lookup.hasEncodedValue(sub))
                    return encodedValueVariable(lookup.getEncodedValue(sub, EncodedValue::class.java), true)
                throw IllegalArgumentException("Not supported for backward: $sub")
            }
            if (name.startsWith(CustomModelParser.IN_AREA_PREFIX))
                return areaVariable(name)
            throw IllegalArgumentException("Not supported $name")
        }

        private fun encodedValueVariable(enc: EncodedValue, backward: Boolean): TypedVariable = when (enc) {
            // order matters, exactly like getReturnType: Enum, String, Decimal, Boolean, Int
            is EnumEncodedValue<*> -> {
                // the stored int IS the ordinal; comparing ordinals == comparing enum identity
                val cell = IntCell()
                loaders.add(EdgeIntLoader(enc, cell, backward))
                enumVariable(enc, IntCellExpr(cell))
            }
            is StringEncodedValue -> {
                // the generated code compares the int index (getInterface returns IntEncodedValue)
                val cell = IntCell()
                loaders.add(EdgeIntLoader(enc, cell, backward))
                TypedVariable(SemType.INT, IntCellExpr(cell))
            }
            is DecimalEncodedValue -> {
                val cell = DoubleCell()
                loaders.add(EdgeDoubleLoader(enc, cell, backward))
                TypedVariable(SemType.DOUBLE, DoubleCellExpr(cell))
            }
            is BooleanEncodedValue -> {
                val cell = BoolCell()
                loaders.add(EdgeBoolLoader(enc, cell, backward))
                TypedVariable(SemType.BOOL, BoolCellExpr(cell))
            }
            is IntEncodedValue -> {
                val cell = IntCell()
                loaders.add(EdgeIntLoader(enc, cell, backward))
                TypedVariable(SemType.INT, IntCellExpr(cell))
            }
            else -> throw IllegalArgumentException("Unsupported EncodedValue: " + enc.javaClass)
        }

        private fun areaVariable(name: String): TypedVariable {
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
            // evaluated inline per occurrence, like the generated CustomWeightingHelper.in(...) call
            return TypedVariable(SemType.BOOL, AreaContains(current, Polygon(PreparedPolygon(geometry))))
        }
    }

    private class TurnEnv(private val lookup: EncodedValueLookup, private val needTwoDirections: Boolean) : ProgramEnv() {
        val loaders = ArrayList<TurnLoader>()

        /** Mirrors CustomModelParser.getTurnPenaltyVariableDeclaration (incl. its check order). */
        override fun create(name: String): TypedVariable {
            if (name == CustomModelParser.CHANGE_ANGLE) {
                // the generated class declares orientation_enc via the lookup — missing EV = rejection
                if (!lookup.hasEncodedValue(Orientation.KEY))
                    throw IllegalArgumentException("Variable not supported: " + Orientation.KEY)
                val cell = DoubleCell()
                loaders.add(ChangeAngleLoader(lookup.getDecimalEncodedValue(Orientation.KEY), cell))
                return TypedVariable(SemType.DOUBLE, DoubleCellExpr(cell))
            }
            if (name == CustomModelParser.STREET_NAME)
                return streetNameVariable(false)
            if (name == CustomModelParser.PREV_PREFIX + CustomModelParser.STREET_NAME)
                return streetNameVariable(true)
            if (lookup.hasEncodedValue(name))
                return encodedValueVariable(lookup.getEncodedValue(name, EncodedValue::class.java), false)
            if (name.startsWith(CustomModelParser.PREV_PREFIX)) {
                val sub = name.substring(CustomModelParser.PREV_PREFIX.length)
                if (lookup.hasEncodedValue(sub))
                    return encodedValueVariable(lookup.getEncodedValue(sub, EncodedValue::class.java), true)
                throw IllegalArgumentException("Not supported for prev: $sub")
            }
            throw IllegalArgumentException("Not supported for turn_penalty: $name")
        }

        private fun streetNameVariable(prevSide: Boolean): TypedVariable {
            val cell = StringCell()
            loaders.add(StreetNameLoader(cell, prevSide))
            return TypedVariable(SemType.STRING, StringCellExpr(cell))
        }

        private fun encodedValueVariable(enc: EncodedValue, prevSide: Boolean): TypedVariable = when (enc) {
            // order matters, exactly like getTurnPenaltyAccessor: Enum, Boolean, Decimal, Int
            is EnumEncodedValue<*> -> {
                val cell = IntCell()
                loaders.add(TurnIntLoader(enc, cell, prevSide, needTwoDirections))
                enumVariable(enc, IntCellExpr(cell))
            }
            is BooleanEncodedValue -> {
                val cell = BoolCell()
                loaders.add(TurnBoolLoader(enc, cell, prevSide, needTwoDirections))
                TypedVariable(SemType.BOOL, BoolCellExpr(cell))
            }
            is DecimalEncodedValue -> {
                val cell = DoubleCell()
                loaders.add(TurnDoubleLoader(enc, cell, prevSide, needTwoDirections))
                TypedVariable(SemType.DOUBLE, DoubleCellExpr(cell))
            }
            is IntEncodedValue -> {
                // includes StringEncodedValue: the generated code uses the int index
                val cell = IntCell()
                loaders.add(TurnIntLoader(enc, cell, prevSide, needTwoDirections))
                TypedVariable(SemType.INT, IntCellExpr(cell))
            }
            else -> throw IllegalArgumentException("Unsupported EncodedValue for turn penalty: " + enc.javaClass)
        }
    }

    // ------------------------------------------------------------------
    // loaders: one primitive read per declared variable per evaluation, no allocation
    // ------------------------------------------------------------------

    internal class CurrentEdge {
        @JvmField
        var edge: EdgeIteratorState? = null
    }

    private class AreaContains(private val current: CurrentEdge, private val polygon: Polygon) : BoolExpr() {
        override fun eval(): Boolean = CustomWeightingHelper.`in`(polygon, current.edge!!)
    }

    private fun interface EdgeLoader {
        fun load(edge: EdgeIteratorState, reverse: Boolean)
    }

    private class EdgeBoolLoader(private val enc: BooleanEncodedValue, private val cell: BoolCell,
                                 private val backward: Boolean) : EdgeLoader {
        override fun load(edge: EdgeIteratorState, reverse: Boolean) {
            cell.value = if (reverse != backward) edge.getReverse(enc) else edge.get(enc)
        }
    }

    private class EdgeIntLoader(private val enc: IntEncodedValue, private val cell: IntCell,
                                private val backward: Boolean) : EdgeLoader {
        override fun load(edge: EdgeIteratorState, reverse: Boolean) {
            cell.value = if (reverse != backward) edge.getReverse(enc) else edge.get(enc)
        }
    }

    private class EdgeDoubleLoader(private val enc: DecimalEncodedValue, private val cell: DoubleCell,
                                   private val backward: Boolean) : EdgeLoader {
        override fun load(edge: EdgeIteratorState, reverse: Boolean) {
            cell.value = if (reverse != backward) edge.getReverse(enc) else edge.get(enc)
        }
    }

    private fun interface TurnLoader {
        fun load(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, outEdge: Int,
                 inEdgeReverse: Boolean, outEdgeReverse: Boolean)
    }

    private class TurnBoolLoader(private val enc: BooleanEncodedValue, private val cell: BoolCell,
                                 private val prevSide: Boolean, private val useReverse: Boolean) : TurnLoader {
        override fun load(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, outEdge: Int,
                          inEdgeReverse: Boolean, outEdgeReverse: Boolean) {
            val edge = if (prevSide) inEdge else outEdge
            val reverse = useReverse && if (prevSide) inEdgeReverse else outEdgeReverse
            cell.value = enc.getBool(reverse, edge, edgeIntAccess)
        }
    }

    private class TurnIntLoader(private val enc: IntEncodedValue, private val cell: IntCell,
                                private val prevSide: Boolean, private val useReverse: Boolean) : TurnLoader {
        override fun load(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, outEdge: Int,
                          inEdgeReverse: Boolean, outEdgeReverse: Boolean) {
            val edge = if (prevSide) inEdge else outEdge
            val reverse = useReverse && if (prevSide) inEdgeReverse else outEdgeReverse
            cell.value = enc.getInt(reverse, edge, edgeIntAccess)
        }
    }

    private class TurnDoubleLoader(private val enc: DecimalEncodedValue, private val cell: DoubleCell,
                                   private val prevSide: Boolean, private val useReverse: Boolean) : TurnLoader {
        override fun load(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, outEdge: Int,
                          inEdgeReverse: Boolean, outEdgeReverse: Boolean) {
            val edge = if (prevSide) inEdge else outEdge
            val reverse = useReverse && if (prevSide) inEdgeReverse else outEdgeReverse
            cell.value = enc.getDecimal(reverse, edge, edgeIntAccess)
        }
    }

    private class ChangeAngleLoader(private val enc: DecimalEncodedValue, private val cell: DoubleCell) : TurnLoader {
        override fun load(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, outEdge: Int,
                          inEdgeReverse: Boolean, outEdgeReverse: Boolean) {
            // outEdgeReverse means direction of travel; calcChangeAngle expects the storage side
            cell.value = CustomWeightingHelper.calcChangeAngle(edgeIntAccess, enc, inEdge, inEdgeReverse, outEdge, !outEdgeReverse)
        }
    }

    private class StreetNameLoader(private val cell: StringCell, private val prevSide: Boolean) : TurnLoader {
        override fun load(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, outEdge: Int,
                          inEdgeReverse: Boolean, outEdgeReverse: Boolean) {
            val edge = if (prevSide) inEdge else outEdge
            cell.value = graph.getEdgeIteratorState(edge, Int.MIN_VALUE)!!.name
        }
    }

    // ------------------------------------------------------------------
    // the composed mappings (fresh, thread-confined per createParameters call)
    // ------------------------------------------------------------------

    private class ClosureEdgeMapping(private val current: CurrentEdge, private val loaders: Array<EdgeLoader>,
                                     private val program: StatementProgram) : CustomWeighting.EdgeToDoubleMapping {
        override fun get(edge: EdgeIteratorState, reverse: Boolean): Double {
            current.edge = edge
            for (i in loaders.indices) loaders[i].load(edge, reverse)
            return program.run()
        }
    }

    private class ClosureTurnMapping(private val loaders: Array<TurnLoader>, private val program: StatementProgram,
                                     private val needTwoDirections: Boolean) : CustomWeighting.TurnPenaltyMapping {
        override fun get(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, viaNode: Int, outEdge: Int): Double {
            var inEdgeReverse = false
            var outEdgeReverse = false
            if (needTwoDirections) {
                inEdgeReverse = !graph.isAdjNode(inEdge, viaNode)
                outEdgeReverse = graph.isAdjNode(outEdge, viaNode)
            }
            for (i in loaders.indices) loaders[i].load(graph, edgeIntAccess, inEdge, outEdge, inEdgeReverse, outEdgeReverse)
            return program.run()
        }
    }

    // ------------------------------------------------------------------
    // min/max calculators: mirror CustomWeightingHelper.calcMaxSpeed/calcMaxPriority and
    // FindMinMax, with the Janino-free stage-3 evaluator instead of ExpressionEvaluator
    // ------------------------------------------------------------------

    private fun calcMaxSpeed(customModel: CustomModel, lookup: EncodedValueLookup): Double {
        val minMaxSpeed = MinMax(0.0, CustomWeightingHelper.GLOBAL_MAX_SPEED)
        findMinMax(minMaxSpeed, customModel.getSpeed(), lookup)
        if (minMaxSpeed.min < 0)
            throw IllegalArgumentException("speed has to be >=0 but can be negative (" + minMaxSpeed.min + ")")
        if (minMaxSpeed.max <= 0)
            throw IllegalArgumentException("maximum speed has to be >0 but was " + minMaxSpeed.max)
        if (minMaxSpeed.max == CustomWeightingHelper.GLOBAL_MAX_SPEED)
            throw IllegalArgumentException("The first statement for 'speed' must be unconditionally to set the speed. But it was " + customModel.getSpeed()[0])
        return minMaxSpeed.max
    }

    private fun calcMaxPriority(customModel: CustomModel, lookup: EncodedValueLookup): Double {
        val minMaxPriority = MinMax(0.0, CustomWeightingHelper.GLOBAL_PRIORITY)
        val statements = customModel.getPriority()
        if (statements.isNotEmpty() && "true" == statements[0].condition()) {
            val value = statements[0].value()
            if (lookup.hasEncodedValue(value))
                minMaxPriority.max = lookup.getDecimalEncodedValue(value).maxOrMaxStorableDecimal
        }
        findMinMax(minMaxPriority, statements, lookup)
        if (minMaxPriority.min < 0)
            throw IllegalArgumentException("priority has to be >=0 but can be negative (" + minMaxPriority.min + ")")
        if (minMaxPriority.max < 0)
            throw IllegalArgumentException("maximum priority has to be >=0 but was " + minMaxPriority.max)
        return minMaxPriority.max
    }

    private fun findMinMax(minMax: MinMax, statements: List<Statement>, lookup: EncodedValueLookup): MinMax {
        val scope = ExpressionScopes.minMaxScope(lookup)
        for (group in CustomModelParser.splitIntoGroup(statements)) findMinMaxForGroup(minMax, group, scope)
        return minMax
    }

    /** 1:1 mirror of FindMinMax.findMinMaxForGroup. */
    private fun findMinMaxForGroup(minMax: MinMax, group: List<Statement>, scope: ExpressionScope) {
        if (group.isEmpty() || Statement.Keyword.IF != group[0].keyword())
            throw IllegalArgumentException("Every group must start with an if-statement")

        val minMaxGroup: MinMax
        val first = group[0]
        if (first.condition().trim() == "true") {
            if (first.isBlock()) {
                for (subGroup in CustomModelParser.splitIntoGroup(first.doBlock())) findMinMaxForGroup(minMax, subGroup, scope)
                return
            } else {
                minMaxGroup = first.operation().apply(minMax, valueMinMax(first.value(), scope))
                if (minMaxGroup.max < 0)
                    throw IllegalArgumentException("statement resulted in negative value: $first")
            }
        } else {
            minMaxGroup = MinMax(Double.MAX_VALUE, 0.0)
            var foundElse = false
            for (s in group) {
                if (s.keyword() == Statement.Keyword.ELSE) foundElse = true
                val tmp: MinMax
                if (s.isBlock()) {
                    tmp = MinMax(minMax.min, minMax.max)
                    for (subGroup in CustomModelParser.splitIntoGroup(s.doBlock())) findMinMaxForGroup(tmp, subGroup, scope)
                } else {
                    tmp = s.operation().apply(minMax, valueMinMax(s.value(), scope))
                    if (tmp.max < 0)
                        throw IllegalArgumentException("statement resulted in negative value: $s")
                }
                minMaxGroup.min = Math.min(minMaxGroup.min, tmp.min)
                minMaxGroup.max = Math.max(minMaxGroup.max, tmp.max)
            }

            if (!foundElse) {
                minMaxGroup.min = Math.min(minMaxGroup.min, minMax.min)
                minMaxGroup.max = Math.max(minMaxGroup.max, minMax.max)
            }
        }

        minMax.min = minMaxGroup.min
        minMax.max = minMaxGroup.max
    }

    /** Mirrors ValueExpressionVisitor.findMinMax (parse-check first, then evaluation). */
    private fun valueMinMax(valueExpression: String, scope: ExpressionScope): MinMax {
        val v = ExpressionValidator.value(valueExpression, scope)
        if (!v.ok) throw IllegalArgumentException(v.invalidMessage)
        if (v.guessedVariables.size > 1)
            throw IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was "
                    + v.guessedVariables.size + ". Value expression: " + valueExpression)
        val pair = ExpressionValidator.valueMinMax(valueExpression, scope)
                ?: throw IllegalArgumentException("Cannot evaluate min/max of value expression: $valueExpression")
        return MinMax(pair.first, pair.second)
    }
}
