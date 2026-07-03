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

import com.graphhopper.routing.weighting.custom.CustomModelParser.IN_AREA_PREFIX
import com.graphhopper.util.Helper
import org.codehaus.janino.Java
import org.codehaus.janino.Parser
import org.codehaus.janino.Scanner
import org.codehaus.janino.TokenType
import org.codehaus.janino.Visitor
import java.io.StringReader
import java.util.TreeMap

/**
 * Expression visitor for the if or else_if condition.
 */
internal class ConditionalExpressionVisitor(
    private val result: ParseResult,
    private val variableValidator: NameValidator,
    private val classHelper: ClassHelper
) : Visitor.AtomVisitor<Boolean, Exception> {

    private val replacements = TreeMap<Int, Replacement>()
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
                if (arg.startsWith(IN_AREA_PREFIX)) {
                    val start = rv.getLocation().getColumnNumber() - 1
                    replacements[start] = Replacement(start, arg.length,
                            CustomWeightingHelper::class.java.simpleName + ".in(this." + arg + ", edge)")
                    result.guessedVariables!!.add(arg)
                    return true
                } else {
                    // e.g. like road_class
                    if (isValidIdentifier(arg)) return true
                    invalidMessage = "'" + arg + "' not available"
                    return false
                }
            }
            invalidMessage = "identifier " + rv + " invalid"
            return false
        }
        if (rv is Java.Literal) {
            return true
        } else if (rv is Java.UnaryOperation) {
            if (rv.operator == "!") return rv.operand.accept(this)
            if (rv.operator == "-") return rv.operand.accept(this)
            return false
        } else if (rv is Java.MethodInvocation) {
            if (allowedMethods.contains(rv.methodName) && rv.target != null) {
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
                    } else if (variableValidator.isValid(n.identifiers[0])) {
                        // road_class.ordinal()
                        if (rv.arguments.size == 0) {
                            result.guessedVariables!!.add(n.identifiers[0]) // return road_class
                            return true
                        } else if (rv.arguments.size == 1) {
                            // prev_street_name.equals(street_name)
                            result.guessedVariables!!.add(n.identifiers[0])
                            return rv.arguments[0].accept(this)
                        }
                    }
                }
            }
            invalidMessage = rv.methodName + " is an illegal method in a conditional expression"
            return false
        } else if (rv is Java.ParenthesizedExpression) {
            return rv.value.accept(this)
        } else if (rv is Java.BinaryOperation) {
            val startRH = rv.rhs.getLocation().getColumnNumber() - 1
            val lhs = rv.lhs
            if (lhs is Java.AmbiguousName && lhs.identifiers.size == 1) {
                val lhVarAsString = lhs.identifiers[0]
                val eqOps = rv.operator == "==" || rv.operator == "!="
                val rhs = rv.rhs
                if (rhs is Java.AmbiguousName && rhs.identifiers.size == 1) {
                    // Make enum explicit as NO or OTHER can occur in other enums so convert "toll == NO" to "toll == Toll.NO"
                    val rhValueAsString = rhs.identifiers[0]
                    if (variableValidator.isValid(lhVarAsString) && Helper.toUpperCase(rhValueAsString) == rhValueAsString) {
                        if (!eqOps)
                            throw IllegalArgumentException("Operator " + rv.operator + " not allowed for enum")
                        val value = classHelper.getClassName(rv.lhs.toString())
                        replacements[startRH] = Replacement(startRH, rhValueAsString.length, value + "." + rhValueAsString)
                    }
                }
            }
            return rv.lhs.accept(this) && rv.rhs.accept(this)
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

    internal class Replacement(
        @JvmField val start: Int,
        @JvmField val oldLength: Int,
        @JvmField val newString: String
    )

    companion object {
        private val allowedMethodParents = HashSet(listOf("edge", "Math", "country"))
        private val allowedMethods = HashSet(listOf("ordinal", "getDistance", "getName",
                "contains", "sqrt", "abs", "isRightHandTraffic", "equals"))

        /**
         * Enforce simple expressions of user input to increase security.
         *
         * @return ParseResult with ok if it is a valid and "simple" expression. It contains all guessed variables and a
         * converted expression that includes class names for constants to avoid conflicts e.g. when doing "toll == Toll.NO"
         * instead of "toll == NO".
         */
        @JvmStatic
        @JvmName("parse")
        internal fun parse(expression: String, validator: NameValidator, helper: ClassHelper): ParseResult {
            val result = ParseResult()
            try {
                val parser = Parser(Scanner("ignore", StringReader(expression)))
                val atom = parser.parseConditionalExpression()
                // after parsing the expression the input should end (otherwise it is not "simple")
                if (parser.peek().type == TokenType.END_OF_INPUT) {
                    result.guessedVariables = LinkedHashSet()
                    val visitor = ConditionalExpressionVisitor(result, validator, helper)
                    result.ok = atom.accept(visitor)
                    result.invalidMessage = visitor.invalidMessage
                    if (result.ok) {
                        val converted = StringBuilder(expression.length)
                        var start = 0
                        for (replace in visitor.replacements.values) {
                            converted.append(expression, start, replace.start).append(replace.newString)
                            start = replace.start + replace.oldLength
                        }
                        converted.append(expression.substring(start))
                        result.converted = converted
                    }
                }
            } catch (ex: Exception) {
            }
            return result
        }
    }
}
