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
 *
 *  The whitelist logic in this file is a transcription of GraphHopper's
 *  ConditionalExpressionVisitor/ValueExpressionVisitor, whose traversal mirrors Janino
 *  AST concepts (e.g. the method-invocation target being an AmbiguousName holding the
 *  method name as its last identifier). Janino is distributed under the BSD-3-Clause
 *  license, Copyright (c) 2001-2010 Arno Unkrig, Copyright (c) 2015-2016 TIBCO Software
 *  Inc. (https://janino-compiler.github.io/janino/). No Janino source code was copied;
 *  see NOTICE.md.
 */
package com.graphhopper.routing.weighting.custom.expression

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What an encoded value stores, as far as expression validation is concerned. */
enum class EvKind { BOOLEAN, NUMBER, ENUMERATION, STRING }

/** Validation metadata of one encoded value. [minValue]/[maxValue] mirror `getMinStorable*`/`getMaxOrMaxStorable*`. */
class EvMeta(
        @JvmField val kind: EvKind,
        @JvmField val enumConstants: Set<String> = emptySet(),
        @JvmField val minValue: Double = 0.0,
        @JvmField val maxValue: Double = 0.0
)

/**
 * Name-resolution environment for expression validation (pure Kotlin, KMP-clean — build one
 * from an `EncodedValueLookup` via [ExpressionScopes]).
 *
 * @param isValidName the identifier whitelist; for conditions this mirrors CustomModelParser's
 * `nameInConditionValidator` (encoded values, ALL-UPPERCASE constants, `in_` areas, `change_angle`,
 * `street_name`, `prev_street_name`, `backward_`/`prev_` + encoded value), for values the
 * `findVariables` validator (encoded values and names containing "Infinity")
 * @param encodedValue raw lookup by exact encoded-value name (no prefix stripping)
 * @param areaIds ids of the available areas (without the `in_` prefix)
 */
class ExpressionScope(
        val isValidName: (String) -> Boolean,
        val encodedValue: (String) -> EvMeta?,
        val areaIds: Set<String> = emptySet()
) {
    /** Mirrors CustomModelParser.createSimplifiedLookup: resolves `backward_`/`prev_` prefixes. */
    internal fun simplifiedEv(name: String): EvMeta? = when {
        name == STREET_NAME || name == PREV_PREFIX + STREET_NAME -> null
        name.startsWith(BACKWARD_PREFIX) -> encodedValue(name.substring(BACKWARD_PREFIX.length))
        name.startsWith(PREV_PREFIX) -> encodedValue(name.substring(PREV_PREFIX.length))
        else -> encodedValue(name)
    }

    companion object {
        // deliberately duplicated from CustomModelParser to keep this package KMP-clean
        internal const val IN_AREA_PREFIX = "in_"
        internal const val BACKWARD_PREFIX = "backward_"
        internal const val PREV_PREFIX = "prev_"
        internal const val CHANGE_ANGLE = "change_angle"
        internal const val STREET_NAME = "street_name"
    }
}

/** Where a condition is evaluated; determines which special variables can be declared. */
enum class ExpressionContext {
    /** priority/speed statements: per-edge, allows `backward_` and `in_` area variables. */
    EDGE,

    /** turn_penalty statements: allows `prev_`, `street_name`, `prev_street_name`, `change_angle`. */
    TURN_PENALTY
}

/** Result of a validation run; mirrors the Janino pipeline's ParseResult. */
class ExpressionValidation internal constructor(
        @JvmField val ok: Boolean,
        @JvmField val invalidMessage: String?,
        @JvmField val guessedVariables: Set<String>,
        @JvmField val operators: Set<String>,
        internal val node: ExprNode?
) {
    internal companion object {
        internal fun rejected(message: String?): ExpressionValidation =
                ExpressionValidation(false, message, emptySet(), emptySet(), null)
    }
}

/**
 * Enforces the same whitelisting rules over the [ExpressionParser] AST as the Janino-based
 * visitors (`ConditionalExpressionVisitor`/`ValueExpressionVisitor`):
 *
 *  - [condition]/[value] mirror the visitors' parse-level acceptance exactly (identifier
 *    whitelist, literal/operator/method rules, the enum comparison rule) including the
 *    guessed-variables bookkeeping
 *  - [conditionStrict]/[valueStrict] additionally enforce what the Janino pipeline only
 *    catches when compiling the generated class: enum-constant validity per encoded value,
 *    area references, variable declarability per [ExpressionContext], the single
 *    encoded-value rule and non-negativity of value expressions
 */
object ExpressionValidator {

    private val CONDITION_METHODS = setOf(
            "ordinal", "getDistance", "getName", "contains", "sqrt", "abs", "isRightHandTraffic", "equals")
    private val CONDITION_METHOD_PARENTS = setOf("edge", "Math", "country")
    private val VALUE_METHODS = setOf("sqrt")
    private val VALUE_METHOD_PARENTS = setOf("Math")

    /** Parse-level validation of an if/else_if condition; mirrors ConditionalExpressionVisitor.parse. */
    @JvmStatic
    fun condition(expression: String, scope: ExpressionScope): ExpressionValidation {
        val node = try {
            ExpressionParser.parse(expression)
        } catch (e: ExpressionSyntaxException) {
            return ExpressionValidation.rejected(e.message)
        }
        val w = ConditionWalker(scope)
        val ok = w.check(node)
        return ExpressionValidation(ok, if (ok) null else w.message, w.guessed, emptySet(), node)
    }

    /** Parse-level validation of a limit_to/multiply_by/add value; mirrors ValueExpressionVisitor.parse. */
    @JvmStatic
    fun value(expression: String, scope: ExpressionScope): ExpressionValidation {
        val node = try {
            ExpressionParser.parse(expression)
        } catch (e: ExpressionSyntaxException) {
            return ExpressionValidation.rejected(e.message)
        }
        val w = ValueWalker(scope)
        val ok = w.check(node)
        return ExpressionValidation(ok, if (ok) null else w.message, w.guessed, w.operators, node)
    }

    /**
     * Full validation of a condition: [condition] plus the checks the Janino pipeline performs
     * while generating and compiling the weighting class — enum-constant membership per encoded
     * value, rejection of stray UPPERCASE identifiers outside the `ev == CONSTANT` position, and
     * declarability of every guessed variable in the given [context] (encoded values,
     * `backward_`/`prev_` prefixes, `in_` area references incl. area-id validity and existence).
     */
    @JvmStatic
    fun conditionStrict(expression: String, scope: ExpressionScope, context: ExpressionContext): ExpressionValidation {
        val v = condition(expression, scope)
        if (!v.ok) return v
        val enumRhs = ArrayList<ExprNode>()
        val err = strictConditionWalk(v.node!!, scope, enumRhs)
        if (err != null) return ExpressionValidation.rejected(err)
        for (variable in v.guessedVariables) {
            val declErr = checkDeclarable(variable, scope, context)
            if (declErr != null) return ExpressionValidation.rejected(declErr)
        }
        return v
    }

    /**
     * Full validation of a value expression: [value] plus the checks of
     * `ValueExpressionVisitor.findVariables`: at most one encoded value, numeric-only data,
     * and non-negativity of the expression over the encoded value's storable range (evaluated
     * with double arithmetic at the range endpoints, exactly like the Janino pipeline).
     *
     * @param allowBareInfinity true when the statement operation maps the literal string
     * "Infinity" to `Double.POSITIVE_INFINITY` (only `add` does, see Statement.Op.build)
     */
    @JvmStatic
    @JvmOverloads
    fun valueStrict(expression: String, scope: ExpressionScope, allowBareInfinity: Boolean = false): ExpressionValidation {
        val v = value(expression, scope)
        if (!v.ok) return v
        if (v.guessedVariables.size > 1)
            return ExpressionValidation.rejected("Currently only a single EncodedValue is allowed on the right-hand side, but was "
                    + v.guessedVariables.size + ". Value expression: " + expression)

        // mirror the Double.parseDouble speed path of findVariables/findMinMax
        val direct = expression.toDoubleOrNull()
        if (direct != null) {
            // "Infinity" only compiles when the operation maps the exact string (add); any other
            // non-literal route to a non-finite value ends in generated code that does not compile
            if (expression == "Infinity")
                return if (allowBareInfinity) v
                else ExpressionValidation.rejected("Infinity is not allowed for this operation: $expression")
            if (direct.isNaN() || direct.isInfinite())
                return ExpressionValidation.rejected("invalid value: $expression")
            return if (direct < 0)
                ExpressionValidation.rejected("illegal expression as it can result in a negative weight: $expression")
            else v
        }

        val variable = v.guessedVariables.firstOrNull()
        var meta: EvMeta? = null
        if (variable != null) {
            meta = scope.encodedValue(variable)
                    ?: return ExpressionValidation.rejected("'$variable' not available")
            if (meta.kind != EvKind.NUMBER)
                return ExpressionValidation.rejected("Cannot use non-number data '$variable' in value expression")
        }
        val evaluated = try {
            if (variable == null) {
                evalValue(v.node!!, null, 0.0)
            } else {
                val m = meta!!
                val atMax = evalValue(v.node!!, variable, m.maxValue)
                val atMin = evalValue(v.node!!, variable, m.minValue)
                min(atMax, atMin)
            }
        } catch (e: ValueEvalException) {
            return ExpressionValidation.rejected(e.message)
        }
        if (evaluated < 0)
            return ExpressionValidation.rejected("illegal expression as it can result in a negative weight: $expression")
        return v
    }

    /** Like [valueStrict] but also returns min/max over the encoded value's range, or rejection. */
    @JvmStatic
    fun valueMinMax(expression: String, scope: ExpressionScope): Pair<Double, Double>? {
        val v = value(expression, scope)
        if (!v.ok || v.guessedVariables.size > 1) return null
        val direct = expression.toDoubleOrNull()
        if (direct != null && !direct.isNaN()) return Pair(direct, direct)
        val variable = v.guessedVariables.firstOrNull()
        return try {
            if (variable == null) {
                val value = evalValue(v.node!!, null, 0.0)
                Pair(value, value)
            } else {
                val meta = scope.encodedValue(variable) ?: return null
                if (meta.kind != EvKind.NUMBER) return null
                val atMax = evalValue(v.node!!, variable, meta.maxValue)
                val atMin = evalValue(v.node!!, variable, meta.minValue)
                Pair(min(atMax, atMin), max(atMax, atMin))
            }
        } catch (e: ValueEvalException) {
            null
        }
    }

    // ---------------------------------------------------------------------
    // parse-level walkers, transcribed 1:1 from the Janino visitors
    // ---------------------------------------------------------------------

    private class ConditionWalker(val scope: ExpressionScope) {
        val guessed = LinkedHashSet<String>()
        var message: String? = null

        fun check(node: ExprNode): Boolean = when (node) {
            is ExprNode.Name -> {
                val arg = node.single
                if (arg != null) {
                    when {
                        arg.startsWith(ExpressionScope.IN_AREA_PREFIX) -> {
                            guessed.add(arg)
                            true
                        }
                        scope.isValidName(arg) -> {
                            if (!arg[0].isUpperCase()) guessed.add(arg)
                            true
                        }
                        else -> fail("'$arg' not available")
                    }
                } else {
                    fail("identifier $node invalid")
                }
            }
            is ExprNode.Literal -> true
            is ExprNode.Unary ->
                if (node.op == "!" || node.op == "-") check(node.operand) else false
            is ExprNode.Call -> {
                var ok = false
                if (node.method in CONDITION_METHODS && node.target.isNotEmpty() && node.target.size == 1) {
                    val parent = node.target[0]
                    if (parent in CONDITION_METHOD_PARENTS) {
                        if (node.args.isEmpty()) {
                            guessed.add(parent) // e.g. "edge" for edge.getDistance()
                            ok = true
                        } else if (node.args.size == 1) {
                            ok = check(node.args[0])
                            if (!ok) return false
                        }
                    } else if (scope.isValidName(parent)) {
                        if (node.args.isEmpty()) {
                            guessed.add(parent) // e.g. road_class.ordinal()
                            ok = true
                        } else if (node.args.size == 1) {
                            guessed.add(parent) // e.g. prev_street_name.equals(street_name)
                            ok = check(node.args[0])
                            if (!ok) return false
                        }
                    }
                }
                if (!ok) fail("${node.method} is an illegal method in a conditional expression") else true
            }
            is ExprNode.Paren -> check(node.inner)
            is ExprNode.Binary -> {
                // make enums explicit: "toll == NO" is only allowed for == and != and only when
                // the left-hand side resolves to a class (mirrors the replacement logic incl.
                // the exception thrown by the ClassHelper of CustomModelParser.verifyExpressions)
                val l = (node.lhs as? ExprNode.Name)?.single
                val r = (node.rhs as? ExprNode.Name)?.single
                if (l != null && r != null && scope.isValidName(l) && r.uppercase() == r) {
                    val eqOps = node.op == "==" || node.op == "!="
                    if (!eqOps) return fail("Operator ${node.op} not allowed for enum")
                    if (scope.simplifiedEv(l) == null) return fail("Couldn't find class for $l")
                }
                check(node.lhs) && check(node.rhs)
            }
            is ExprNode.Ternary -> false
        }

        private fun fail(msg: String): Boolean {
            message = msg
            return false
        }
    }

    private class ValueWalker(val scope: ExpressionScope) {
        val guessed = LinkedHashSet<String>()
        val operators = LinkedHashSet<String>()
        var message: String? = null

        fun check(node: ExprNode): Boolean = when (node) {
            is ExprNode.Name -> {
                val arg = node.single
                if (arg != null) {
                    if (scope.isValidName(arg)) {
                        if (!arg[0].isUpperCase()) guessed.add(arg)
                        true
                    } else {
                        fail("'$arg' not available")
                    }
                } else {
                    fail("identifier $node invalid")
                }
            }
            is ExprNode.Literal -> true
            is ExprNode.Unary -> {
                operators.add(node.op)
                if (node.op == "-") check(node.operand) else false
            }
            is ExprNode.Call -> {
                var ok = false
                var checkedArg = true
                if (node.method in VALUE_METHODS && node.target.size == 1 && node.target[0] in VALUE_METHOD_PARENTS) {
                    if (node.args.isEmpty()) {
                        guessed.add(node.target[0]) // quirk parity: Math.sqrt() adds "Math"
                        ok = true
                    } else if (node.args.size == 1) {
                        checkedArg = check(node.args[0])
                        ok = checkedArg
                    }
                }
                if (!ok && checkedArg) fail("${node.method} is an illegal method in a value expression") else ok
            }
            is ExprNode.Paren -> check(node.inner)
            is ExprNode.Binary -> {
                operators.add(node.op)
                if (node.op == "*" || node.op == "+" || node.op == "-")
                    check(node.lhs) && check(node.rhs)
                else
                    fail("invalid operation '${node.op}'")
            }
            is ExprNode.Ternary -> false
        }

        private fun fail(msg: String): Boolean {
            message = msg
            return false
        }
    }

    // ---------------------------------------------------------------------
    // strict layer: what the Janino pipeline only rejects at class-generation/compile time
    // ---------------------------------------------------------------------

    private fun strictConditionWalk(node: ExprNode, scope: ExpressionScope, consumedEnumRhs: MutableList<ExprNode>): String? {
        when (node) {
            is ExprNode.Binary -> {
                val l = (node.lhs as? ExprNode.Name)?.single
                val r = (node.rhs as? ExprNode.Name)?.single
                if (l != null && r != null && scope.isValidName(l) && r.uppercase() == r) {
                    // parse level guaranteed ==/!= and a resolvable left-hand side
                    val meta = scope.simplifiedEv(l)
                            ?: return "Couldn't find class for $l"
                    if (meta.kind != EvKind.ENUMERATION)
                        return "'$l' cannot be compared with '$r'"
                    if (r !in meta.enumConstants)
                        return "'$r' is not a valid value of '$l'"
                    consumedEnumRhs.add(node.rhs)
                }
                return strictConditionWalk(node.lhs, scope, consumedEnumRhs)
                        ?: strictConditionWalk(node.rhs, scope, consumedEnumRhs)
            }
            is ExprNode.Name -> {
                val arg = node.single
                // an UPPERCASE identifier compiles only in the "ev == CONSTANT" position where it
                // gets qualified with the enum class; anywhere else the generated code cannot resolve it
                if (arg != null && arg[0].isUpperCase() && consumedEnumRhs.none { it === node })
                    return "'$arg' not available"
                return null
            }
            is ExprNode.Unary -> return strictConditionWalk(node.operand, scope, consumedEnumRhs)
            is ExprNode.Paren -> return strictConditionWalk(node.inner, scope, consumedEnumRhs)
            is ExprNode.Call -> {
                for (arg in node.args) {
                    val err = strictConditionWalk(arg, scope, consumedEnumRhs)
                    if (err != null) return err
                }
                return null
            }
            is ExprNode.Literal -> return null
            is ExprNode.Ternary -> return "ternary operator not allowed" // unreachable after parse-level check
        }
    }

    /** Mirrors getVariableDeclaration/getTurnPenaltyVariableDeclaration/createClassTemplate rejection. */
    private fun checkDeclarable(variable: String, scope: ExpressionScope, context: ExpressionContext): String? {
        when (context) {
            ExpressionContext.EDGE -> {
                if (scope.encodedValue(variable) != null) return null
                if (variable.startsWith(ExpressionScope.BACKWARD_PREFIX)
                        && scope.encodedValue(variable.substring(ExpressionScope.BACKWARD_PREFIX.length)) != null) return null
                if (variable.startsWith(ExpressionScope.IN_AREA_PREFIX)) {
                    if (!isValidAreaId(variable)) return "Area has invalid name: $variable"
                    val id = variable.substring(ExpressionScope.IN_AREA_PREFIX.length)
                    if (id !in scope.areaIds) return "Area '$id' wasn't found"
                    return null
                }
                return "Not supported $variable"
            }
            ExpressionContext.TURN_PENALTY -> {
                if (variable == ExpressionScope.CHANGE_ANGLE || variable == ExpressionScope.STREET_NAME
                        || variable == ExpressionScope.PREV_PREFIX + ExpressionScope.STREET_NAME) return null
                if (scope.encodedValue(variable) != null) return null
                if (variable.startsWith(ExpressionScope.PREV_PREFIX)
                        && scope.encodedValue(variable.substring(ExpressionScope.PREV_PREFIX.length)) != null) return null
                return "Not supported for turn_penalty: $variable"
            }
        }
    }

    /** Pure-Kotlin mirror of JsonFeature.isValidId (area ids: `in_` + letters/digits, no `__`). */
    internal fun isValidAreaId(name: String): Boolean {
        if (name.length <= 3 || !name.startsWith(ExpressionScope.IN_AREA_PREFIX) || name in ExpressionLexer.JAVA_KEYWORDS)
            return false
        var underscoreCount = 0
        for (i in 1 until name.length) {
            val c = name[i]
            if (c == '_') {
                if (underscoreCount > 0) return false
                underscoreCount++
            } else if (!c.isLetter() && !c.isDigit()) {
                return false
            } else {
                underscoreCount = 0
            }
        }
        return true
    }

    private class ValueEvalException(message: String) : RuntimeException(message)

    /**
     * Evaluates a (parse-level valid) value expression with double arithmetic, substituting
     * [variableValue] for the encoded value [variable] — the same evaluation the Janino
     * pipeline performs via ExpressionEvaluator with a double parameter.
     */
    private fun evalValue(node: ExprNode, variable: String?, variableValue: Double): Double = when (node) {
        is ExprNode.Literal ->
            if (node.kind == LiteralKind.NUMBER)
                ExpressionLexer.numericLiteralToDouble(node.text)
                        ?: throw ValueEvalException("invalid numeric literal: ${node.text}")
            else throw ValueEvalException("non-numeric literal not allowed in a value expression: ${node.text}")
        is ExprNode.Name ->
            if (node.single == variable && variable != null) variableValue
            else throw ValueEvalException("'$node' not available")
        is ExprNode.Unary -> -evalValue(node.operand, variable, variableValue) // only "-" survives parse-level checks
        is ExprNode.Paren -> evalValue(node.inner, variable, variableValue)
        is ExprNode.Binary -> {
            val l = evalValue(node.lhs, variable, variableValue)
            val r = evalValue(node.rhs, variable, variableValue)
            when (node.op) {
                "*" -> l * r
                "+" -> l + r
                "-" -> l - r
                else -> throw ValueEvalException("invalid operation '${node.op}'")
            }
        }
        is ExprNode.Call ->
            if (node.method == "sqrt" && node.args.size == 1) sqrt(evalValue(node.args[0], variable, variableValue))
            else throw ValueEvalException("${node.method} is an illegal method in a value expression")
        is ExprNode.Ternary -> throw ValueEvalException("ternary operator not allowed")
    }
}
