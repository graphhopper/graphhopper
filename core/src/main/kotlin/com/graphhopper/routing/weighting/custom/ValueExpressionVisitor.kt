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
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.IntEncodedValue
import org.codehaus.commons.compiler.CompileException
import org.codehaus.janino.ExpressionEvaluator
import org.codehaus.janino.Java
import org.codehaus.janino.Parser
import org.codehaus.janino.Scanner
import org.codehaus.janino.TokenType
import org.codehaus.janino.Visitor
import java.io.StringReader

/**
 * Expression visitor for right-hand side value of limit_to or multiply_by.
 */
class ValueExpressionVisitor(
    private val result: ParseResult,
    private val variableValidator: NameValidator
) : Visitor.AtomVisitor<Boolean, Exception> {

    private var invalidMessage: String? = null

    // allow only methods and other identifiers (constants and encoded values)
    fun isValidIdentifier(identifier: String): Boolean {
        if (variableValidator.isValid(identifier)) {
            if (!Character.isUpperCase(identifier[0]))
                result.guessedVariables!!.add(identifier)
            return true
        }
        return false
    }

    @Throws(Exception::class)
    override fun visitRvalue(rv: Java.Rvalue): Boolean {
        if (rv is Java.AmbiguousName) {
            if (rv.identifiers.size == 1) {
                val arg = rv.identifiers[0]
                // e.g. like road_class
                if (isValidIdentifier(arg)) return true
                invalidMessage = "'" + arg + "' not available"
                return false
            }
            invalidMessage = "identifier " + rv + " invalid"
            return false
        }
        if (rv is Java.Literal) {
            return true
        } else if (rv is Java.UnaryOperation) {
            result.operators!!.add(rv.operator)
            if (rv.operator == "-")
                return rv.operand.accept(this)
            return false
        } else if (rv is Java.MethodInvocation) {
            if (allowedMethods.contains(rv.methodName)) {
                // skip methods like this.in()
                if (rv.target != null) {
                    // edge.getDistance(), Math.sqrt(2) => check target name (edge or Math)
                    val n = rv.target.toRvalue() as Java.AmbiguousName
                    if (n.identifiers.size == 2) {
                        if (allowedMethodParents.contains(n.identifiers[0])) {
                            // edge.getDistance(), Math.sqrt(x) => check target name i.e. edge or Math
                            if (rv.arguments.size == 0) {
                                result.guessedVariables!!.add(n.identifiers[0]) // return "edge"
                                return true
                            } else if (rv.arguments.size == 1) {
                                // return "x" but verify before
                                return rv.arguments[0].accept(this)
                            }
                        }
                        // TODO unlike in ConditionalExpressionVisitor we don't support a call like road_class.ordinal()
                        //  as this is currently unsupported in FindMinMax
                    }
                }
            }
            invalidMessage = rv.methodName + " is an illegal method in a value expression"
            return false
        } else if (rv is Java.ParenthesizedExpression) {
            return rv.value.accept(this)
        } else if (rv is Java.BinaryOperation) {
            val op = rv.operator
            result.operators!!.add(op)
            if (op == "*" || op == "+" || rv.operator == "-") {
                return rv.lhs.accept(this) && rv.rhs.accept(this)
            }
            invalidMessage = "invalid operation '" + op + "'"
            return false
        }
        return false
    }

    override fun visitPackage(p: Java.Package): Boolean {
        return false
    }

    override fun visitType(t: Java.Type): Boolean {
        return false
    }

    override fun visitConstructorInvocation(ci: Java.ConstructorInvocation): Boolean {
        return false
    }

    interface NoArgEvaluator {
        fun evaluate(): Double
    }

    interface SingleArgEvaluator {
        fun evaluate(arg: Double): Double
    }

    companion object {
        private val INFINITY = Double.POSITIVE_INFINITY.toString()
        private val allowedMethodParents = setOf("Math")
        private val allowedMethods = setOf("sqrt")

        @JvmStatic
        @JvmName("parse")
        internal fun parse(expression: String, variableValidator: NameValidator): ParseResult {
            val result = ParseResult()
            try {
                val parser = Parser(Scanner("ignore", StringReader(expression)))
                val atom = parser.parseConditionalExpression()
                if (parser.peek().type == TokenType.END_OF_INPUT) {
                    result.guessedVariables = LinkedHashSet()
                    result.operators = LinkedHashSet()
                    val visitor = ValueExpressionVisitor(result, variableValidator)
                    result.ok = atom.accept(visitor)
                    result.invalidMessage = visitor.invalidMessage
                }
            } catch (ex: Exception) {
            }
            return result
        }

        @JvmStatic
        @JvmName("findVariables")
        internal fun findVariables(statements: List<Statement>, lookup: EncodedValueLookup): MutableSet<String> {
            val groups = CustomModelParser.splitIntoGroup(statements)
            val variables = LinkedHashSet<String>()
            for (group in groups) findVariablesForGroup(variables, group, lookup)
            return variables
        }

        private fun findVariablesForGroup(createdObjects: MutableSet<String>, group: List<Statement>, lookup: EncodedValueLookup) {
            if (group.isEmpty() || Statement.Keyword.IF != group[0].keyword())
                throw IllegalArgumentException("Every group of statements must start with an if-statement")

            val first = group[0]
            if (first.condition().trim() == "true") {
                if (first.isBlock()) {
                    val groups = CustomModelParser.splitIntoGroup(first.doBlock())
                    for (subGroup in groups)
                        findVariablesForGroup(createdObjects, subGroup, lookup)
                } else {
                    createdObjects.addAll(findVariables(first.value(), lookup))
                }

                if (group.size > 1)
                    throw IllegalArgumentException("Only one statement allowed for an unconditional statement")
            } else {
                for (st in group) {
                    if (st.isBlock()) {
                        val groups = CustomModelParser.splitIntoGroup(st.doBlock())
                        for (subGroup in groups)
                            findVariablesForGroup(createdObjects, subGroup, lookup)
                    } else {
                        createdObjects.addAll(findVariables(st.value(), lookup))
                    }
                }
            }
        }

        @JvmStatic
        @JvmName("findVariables")
        internal fun findVariables(valueExpression: String, lookup: EncodedValueLookup): Set<String> {
            val result = parse(valueExpression) { key -> lookup.hasEncodedValue(key) || key.contains(INFINITY) }
            if (!result.ok)
                throw IllegalArgumentException(result.invalidMessage)
            if (result.guessedVariables!!.size > 1)
                throw IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + result.guessedVariables!!.size + ". Value expression: " + valueExpression)

        // TODO Nearly duplicate code as in findMinMax
            var value: Double
            try {
                // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
                // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
                // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
                value = java.lang.Double.parseDouble(valueExpression)
            } catch (ex: NumberFormatException) {
                try {
                    if (result.guessedVariables!!.isEmpty()) { // without encoded values
                        val ee = ExpressionEvaluator().createFastEvaluator(valueExpression, NoArgEvaluator::class.java)
                        value = ee.evaluate()
                    } else if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                        val enc = lookup.getEncodedValue(valueExpression, EncodedValue::class.java)
                        value = Math.min(getMin(enc), getMax(enc))
                    } else {
                        // single encoded value
                        val variable = result.guessedVariables!!.iterator().next()
                        val ee = ExpressionEvaluator().createFastEvaluator(valueExpression, SingleArgEvaluator::class.java, variable)
                        val enc = lookup.getEncodedValue(variable, EncodedValue::class.java)
                        val max = getMax(enc)
                        val val1 = ee.evaluate(max)
                        val min = getMin(enc)
                        val val2 = ee.evaluate(min)
                        value = Math.min(val1, val2)
                    }
                } catch (ex2: CompileException) {
                    throw IllegalArgumentException(ex2)
                }
            }
            if (value < 0)
                throw IllegalArgumentException("illegal expression as it can result in a negative weight: " + valueExpression)

            return result.guessedVariables!!
        }

        @JvmStatic
        @JvmName("findMinMax")
        internal fun findMinMax(valueExpression: String, lookup: EncodedValueLookup): MinMax {
            val result = parse(valueExpression) { name -> lookup.hasEncodedValue(name) }
            if (!result.ok)
                throw IllegalArgumentException(result.invalidMessage)
            if (result.guessedVariables!!.size > 1)
                throw IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + result.guessedVariables!!.size + ". Value expression: " + valueExpression)

            // TODO Nearly duplicate as in findVariables
            try {
                // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
                // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
                // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
                val value = java.lang.Double.parseDouble(valueExpression)
                return MinMax(value, value)
            } catch (ex: NumberFormatException) {
            }

            try {
                if (result.guessedVariables!!.isEmpty()) { // without encoded values
                    val ee = ExpressionEvaluator().createFastEvaluator(valueExpression, NoArgEvaluator::class.java)
                    val value = ee.evaluate()
                    return MinMax(value, value)
                }

                if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                    val enc = lookup.getEncodedValue(valueExpression, EncodedValue::class.java)
                    val min = getMin(enc)
                    val max = getMax(enc)
                    return MinMax(min, max)
                }

                val variable = result.guessedVariables!!.iterator().next()
                val ee = ExpressionEvaluator().createFastEvaluator(valueExpression, SingleArgEvaluator::class.java, variable)
                val enc = lookup.getEncodedValue(variable, EncodedValue::class.java)
                val max = getMax(enc)
                val val1 = ee.evaluate(max)
                val min = getMin(enc)
                val val2 = ee.evaluate(min)
                return MinMax(Math.min(val1, val2), Math.max(val1, val2))
            } catch (ex: CompileException) {
                throw IllegalArgumentException(ex)
            }
        }

        @JvmStatic
        @JvmName("getMin")
        internal fun getMin(enc: EncodedValue): Double {
            if (enc is DecimalEncodedValue)
                return enc.minStorableDecimal
            else if (enc is IntEncodedValue) return enc.minStorableInt.toDouble()
            throw IllegalArgumentException("Cannot use non-number data '" + enc.name + "' in value expression")
        }

        @JvmStatic
        @JvmName("getMax")
        internal fun getMax(enc: EncodedValue): Double {
            if (enc is DecimalEncodedValue)
                return enc.maxOrMaxStorableDecimal
            else if (enc is IntEncodedValue)
                return enc.maxOrMaxStorableInt.toDouble()
            throw IllegalArgumentException("Cannot use non-number data '" + enc.name + "' in value expression")
        }
    }
}
