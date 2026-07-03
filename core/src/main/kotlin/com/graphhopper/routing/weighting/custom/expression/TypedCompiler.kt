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
package com.graphhopper.routing.weighting.custom.expression

/**
 * Thrown when a (whitelist-valid) expression cannot be typed under Java semantics — the
 * closure-composer equivalent of a Janino compile error, e.g. `!max_speed`, `road_class == 2`
 * or a shift on a double. Extends IllegalArgumentException so callers reject the custom
 * model exactly like the Janino back-end does.
 */
class TypedCompilationException(message: String) : IllegalArgumentException(message)

/** Static Java type of a typed evaluator node. */
sealed class SemType {
    object BOOL : SemType()
    object INT : SemType()
    object LONG : SemType()
    object FLOAT : SemType()
    object DOUBLE : SemType()
    object STRING : SemType()
    object NULL : SemType()

    /** an enum type; [id] identifies the enum class within one compilation */
    class ENUM(val id: Int, val typeName: String) : SemType()

    val isNumeric: Boolean
        get() = this === INT || this === LONG || this === FLOAT || this === DOUBLE

    val isIntegral: Boolean
        get() = this === INT || this === LONG
}

/**
 * A declared variable: the Janino back-end declares one local per guessed variable at the
 * start of the generated method; here each variable is a cell-reading node plus its type.
 * Enum variables are represented by their ORDINAL (an [IntExpr]) — `==`/`!=` against an
 * enum constant, `.ordinal()` and `.equals()` are all ordinal (identity) comparisons.
 */
class TypedVariable(
        val type: SemType,
        val node: Any,
        /** enum constants by name (validation guaranteed membership before compilation) */
        val enumConstants: Map<String, Int> = emptyMap(),
        /** ordinal-indexed tables for whitelisted boolean enum properties, e.g. isRightHandTraffic */
        val enumBoolProperties: Map<String, BooleanArray> = emptyMap()
)

/**
 * Name-resolution environment for [TypedCompiler]. Implementations must resolve exactly the
 * variables that were DECLARED for the compiled section (the union of the guessed variables
 * of all its expressions) — not more: `country.equals(x)` only compiles in the Janino
 * back-end when `country` got declared through some other occurrence, because the visitor
 * does not add the receiver of a whitelisted-parent 1-arg call to the guessed variables.
 */
interface TypedEnv {
    fun variable(name: String): TypedVariable?

    /** pooled string literal instances — mirrors the JVM's literal interning per program */
    fun internLiteral(value: String): String
}

/**
 * Lowers a validated [ExprNode] AST into a typed evaluator-node DAG (see Evaluators.kt)
 * with exact JAVA typing/evaluation semantics, mirroring what the Janino back-end's
 * generated code computes:
 *
 *  - literals are typed like Java literals (int/long/float/double/char/String/boolean/null,
 *    hex/octal/binary, `_` separators, suffixes; out-of-range literals are compile errors)
 *  - binary numeric promotion: int → long → float → double; `int/int` stays int
 *  - `== != < <= > >=` produce booleans; `&& || !` require booleans; `& | ^` are boolean or
 *    integral-bitwise; shifts are integral-only
 *  - enum values are compared via `==`/`!=` against the constant of the left-hand side's
 *    enum type (as the generated `toll == Toll.NO`), realized as ordinal comparison
 *  - `String == String` is reference identity (literals are pooled/interned), while
 *    `equals`/`contains` are the usual structural methods
 *  - `Math.sqrt(x)` is double; `Math.abs(x)` resolves the Java overload of the argument type
 *
 * Anything not typable this way throws [TypedCompilationException] — the same custom models
 * the Janino back-end rejects with a compile error.
 *
 * Deliberately UNSUPPORTED (rejected here although Janino would compile it — documented
 * divergence, kept out of the differential corpus): String concatenation with `+`.
 */
object TypedCompiler {

    /** Compiles an if/else_if condition; the root must be boolean like in `if (...)`. */
    fun compileCondition(root: ExprNode, env: TypedEnv): BoolExpr {
        val t = compile(root, env)
        if (t.type !== SemType.BOOL) err("condition must be a boolean expression")
        return t.node as BoolExpr
    }

    /** Compiles a limit_to/multiply_by/add value; the numeric result is widened to double. */
    fun compileValue(root: ExprNode, env: TypedEnv): DoubleExpr {
        val t = compile(root, env)
        if (!t.type.isNumeric) err("value must be a numeric expression")
        return toDouble(t)
    }

    // ------------------------------------------------------------------

    private class Typed(val type: SemType, val node: Any?)

    private fun err(message: String): Nothing = throw TypedCompilationException(message)

    private fun compile(node: ExprNode, env: TypedEnv): Typed = when (node) {
        is ExprNode.Paren -> compile(node.inner, env)
        is ExprNode.Literal -> literal(node, env)
        is ExprNode.Name -> name(node, env)
        is ExprNode.Unary -> unary(node, env)
        is ExprNode.Binary -> binary(node, env)
        is ExprNode.Call -> call(node, env)
        is ExprNode.Ternary -> err("ternary operator not allowed")
    }

    private fun name(node: ExprNode.Name, env: TypedEnv): Typed {
        val n = node.single ?: err("identifier $node invalid")
        val v = env.variable(n) ?: err("'$n' not available")
        return Typed(v.type, v.node)
    }

    private fun literal(node: ExprNode.Literal, env: TypedEnv): Typed = when (node.kind) {
        LiteralKind.NUMBER -> numericLiteral(node.text, false)
        LiteralKind.STRING -> Typed(SemType.STRING,
                StringConst(env.internLiteral(unescape(node.text.substring(1, node.text.length - 1)))))
        LiteralKind.CHAR -> Typed(SemType.INT, IntConst(unescape(node.text.substring(1, node.text.length - 1))[0].code))
        LiteralKind.BOOLEAN -> Typed(SemType.BOOL, BoolConst(node.text == "true"))
        LiteralKind.NULL -> Typed(SemType.NULL, null)
    }

    private fun unary(node: ExprNode.Unary, env: TypedEnv): Typed = when (node.op) {
        "!" -> {
            val t = compile(node.operand, env)
            if (t.type !== SemType.BOOL) err("operator ! cannot be applied to a non-boolean operand")
            Typed(SemType.BOOL, NotExpr(t.node as BoolExpr))
        }
        "-" -> {
            // Java treats "-<decimal literal>" as one literal: -2147483648 is a valid int
            val operand = node.operand
            if (operand is ExprNode.Literal && operand.kind == LiteralKind.NUMBER) {
                numericLiteral(operand.text, true)
            } else {
                val t = compile(node.operand, env)
                when (t.type) {
                    SemType.INT -> Typed(SemType.INT, IntNeg(t.node as IntExpr))
                    SemType.LONG -> Typed(SemType.LONG, LongNeg(t.node as LongExpr))
                    SemType.FLOAT -> Typed(SemType.FLOAT, FloatNeg(t.node as FloatExpr))
                    SemType.DOUBLE -> Typed(SemType.DOUBLE, DoubleNeg(t.node as DoubleExpr))
                    else -> err("operator - cannot be applied to a non-numeric operand")
                }
            }
        }
        else -> err("operator ${node.op} not allowed")
    }

    private fun binary(node: ExprNode.Binary, env: TypedEnv): Typed = when (node.op) {
        "&&", "||" -> {
            val l = compile(node.lhs, env)
            val r = compile(node.rhs, env)
            if (l.type !== SemType.BOOL || r.type !== SemType.BOOL)
                err("operator ${node.op} requires boolean operands")
            val ln = l.node as BoolExpr
            val rn = r.node as BoolExpr
            Typed(SemType.BOOL, if (node.op == "&&") AndExpr(ln, rn) else OrExpr(ln, rn))
        }
        "==", "!=" -> equality(node, env)
        "<", "<=", ">", ">=" -> {
            val l = compile(node.lhs, env)
            val r = compile(node.rhs, env)
            if (!l.type.isNumeric || !r.type.isNumeric)
                err("operator ${node.op} requires numeric operands")
            val op = when (node.op) {
                "<" -> EvalOp.LT
                "<=" -> EvalOp.LE
                ">" -> EvalOp.GT
                else -> EvalOp.GE
            }
            Typed(SemType.BOOL, comparison(op, l, r))
        }
        "+", "-", "*", "/", "%" -> {
            val l = compile(node.lhs, env)
            val r = compile(node.rhs, env)
            if (!l.type.isNumeric || !r.type.isNumeric)
                err("operator ${node.op} cannot be applied to non-numeric operands")
            val op = when (node.op) {
                "+" -> EvalOp.ADD
                "-" -> EvalOp.SUB
                "*" -> EvalOp.MUL
                "/" -> EvalOp.DIV
                else -> EvalOp.REM
            }
            arithmetic(op, l, r)
        }
        "<<", ">>", ">>>" -> {
            val l = compile(node.lhs, env)
            val r = compile(node.rhs, env)
            if (!l.type.isIntegral || !r.type.isIntegral)
                err("shift operator ${node.op} requires integral operands")
            val op = when (node.op) {
                "<<" -> EvalOp.SHL
                ">>" -> EvalOp.SHR
                else -> EvalOp.USHR
            }
            // result type is the (promoted) left operand; the shift distance keeps only its low bits
            when (l.type) {
                SemType.INT -> Typed(SemType.INT, IntArith(op, l.node as IntExpr, toShiftDistance(r)))
                else -> Typed(SemType.LONG, LongArith(op, l.node as LongExpr,
                        if (r.type === SemType.LONG) r.node as LongExpr else IntToLong(r.node as IntExpr)))
            }
        }
        "&", "|", "^" -> {
            val l = compile(node.lhs, env)
            val r = compile(node.rhs, env)
            val op = when (node.op) {
                "&" -> EvalOp.AND
                "|" -> EvalOp.OR
                else -> EvalOp.XOR
            }
            if (l.type === SemType.BOOL && r.type === SemType.BOOL)
                Typed(SemType.BOOL, BoolBitExpr(op, l.node as BoolExpr, r.node as BoolExpr))
            else if (l.type.isIntegral && r.type.isIntegral)
                arithmetic(op, l, r)
            else
                err("operator ${node.op} requires two boolean or two integral operands")
        }
        else -> err("operator ${node.op} not allowed")
    }

    private fun equality(node: ExprNode.Binary, env: TypedEnv): Typed {
        val negate = node.op == "!="
        // enum-constant form "toll == NO": mirrors the Janino pipeline's replacement with
        // the qualified constant of the LEFT-hand side's enum type (only for a direct name
        // on the left and an ALL-UPPERCASE direct name on the right)
        val lName = (node.lhs as? ExprNode.Name)?.single
        val rName = (node.rhs as? ExprNode.Name)?.single
        if (lName != null && rName != null && rName.uppercase() == rName) {
            val v = env.variable(lName)
            if (v != null) {
                if (v.type !is SemType.ENUM) err("'$lName' cannot be compared with '$rName'")
                val ordinal = v.enumConstants[rName] ?: err("'$rName' is not a valid value of '$lName'")
                return Typed(SemType.BOOL,
                        IntCompare(if (negate) EvalOp.NE else EvalOp.EQ, v.node as IntExpr, IntConst(ordinal)))
            }
        }

        val l = compile(node.lhs, env)
        val r = compile(node.rhs, env)
        val lt = l.type
        val rt = r.type
        val op = if (negate) EvalOp.NE else EvalOp.EQ
        return Typed(SemType.BOOL, when {
            lt.isNumeric && rt.isNumeric -> comparison(op, l, r)
            lt === SemType.BOOL && rt === SemType.BOOL -> BoolBitExpr(op, l.node as BoolExpr, r.node as BoolExpr)
            lt is SemType.ENUM && rt is SemType.ENUM ->
                if (lt.id == rt.id) IntCompare(op, l.node as IntExpr, r.node as IntExpr)
                else err("incomparable enum types ${lt.typeName} and ${rt.typeName}")
            lt === SemType.STRING && rt === SemType.STRING ->
                StringIdentity(l.node as StringExpr, r.node as StringExpr, negate)
            lt === SemType.STRING && rt === SemType.NULL -> StringIsNull(l.node as StringExpr, negate)
            lt === SemType.NULL && rt === SemType.STRING -> StringIsNull(r.node as StringExpr, negate)
            lt is SemType.ENUM && rt === SemType.NULL -> BoolConst(negate) // encoded enum values are never null
            lt === SemType.NULL && rt is SemType.ENUM -> BoolConst(negate)
            lt === SemType.NULL && rt === SemType.NULL -> BoolConst(!negate)
            else -> err("incomparable operand types for ${node.op}")
        })
    }

    private fun call(node: ExprNode.Call, env: TypedEnv): Typed {
        if (node.target.size != 1) err("${node.method} is an illegal method")
        val target = node.target[0]

        if (target == "Math") {
            if (node.args.size != 1) err("cannot resolve Math.${node.method} with ${node.args.size} arguments")
            val arg = compile(node.args[0], env)
            if (!arg.type.isNumeric) err("Math.${node.method} requires a numeric argument")
            return when (node.method) {
                "sqrt" -> Typed(SemType.DOUBLE, DoubleSqrt(toDouble(arg)))
                "abs" -> when (arg.type) {
                    SemType.INT -> Typed(SemType.INT, IntAbs(arg.node as IntExpr))
                    SemType.LONG -> Typed(SemType.LONG, LongAbs(arg.node as LongExpr))
                    SemType.FLOAT -> Typed(SemType.FLOAT, FloatAbs(arg.node as FloatExpr))
                    else -> Typed(SemType.DOUBLE, DoubleAbs(arg.node as DoubleExpr))
                }
                else -> err("cannot resolve method Math.${node.method}")
            }
        }

        // The receiver must be a DECLARED variable. Note: for the whitelisted "parent"
        // targets (edge/country) a 1-arg call does not add the target to the guessed
        // variables, so unless it got declared through another occurrence the generated
        // Java could not resolve it either.
        val recv = env.variable(target) ?: err("cannot resolve '$target'")
        return when (node.method) {
            "ordinal" -> {
                if (node.args.isNotEmpty()) err("cannot resolve ordinal(...) with arguments")
                if (recv.type !is SemType.ENUM) err("cannot call ordinal() on '$target'")
                Typed(SemType.INT, recv.node as IntExpr)
            }
            "isRightHandTraffic" -> {
                if (node.args.isNotEmpty()) err("cannot resolve isRightHandTraffic(...) with arguments")
                val table = recv.enumBoolProperties["isRightHandTraffic"]
                        ?: err("cannot call isRightHandTraffic() on '$target'")
                Typed(SemType.BOOL, BoolTableExpr(recv.node as IntExpr, table))
            }
            "contains" -> {
                if (node.args.size != 1) err("cannot resolve contains(...) with ${node.args.size} arguments")
                if (recv.type !== SemType.STRING) err("cannot call contains() on '$target'")
                val arg = compile(node.args[0], env)
                val argNode = when (arg.type) {
                    SemType.STRING -> arg.node as StringExpr
                    SemType.NULL -> StringConst(null) // compiles in Java, NPEs at runtime
                    else -> err("contains() requires a String argument")
                }
                Typed(SemType.BOOL, StringContainsExpr(recv.node as StringExpr, argNode))
            }
            "equals" -> {
                if (node.args.size != 1) err("cannot resolve equals(...) with ${node.args.size} arguments")
                val arg = compile(node.args[0], env)
                when (recv.type) {
                    SemType.STRING -> Typed(SemType.BOOL, when (arg.type) {
                        SemType.STRING -> StringEqualsExpr(recv.node as StringExpr, arg.node as StringExpr)
                        // enums / null / (auto-boxed) primitives are never equal to a String
                        else -> BoolConst(false)
                    })
                    is SemType.ENUM -> Typed(SemType.BOOL, when {
                        arg.type is SemType.ENUM && (arg.type as SemType.ENUM).id == (recv.type as SemType.ENUM).id ->
                            IntCompare(EvalOp.EQ, recv.node as IntExpr, arg.node as IntExpr)
                        else -> BoolConst(false)
                    })
                    else -> err("cannot call equals() on the primitive '$target'")
                }
            }
            else -> err("${node.method} is an illegal method")
        }
    }

    // ------------------------------------------------------------------
    // numeric promotion helpers
    // ------------------------------------------------------------------

    private fun promoted(a: SemType, b: SemType): SemType = when {
        a === SemType.DOUBLE || b === SemType.DOUBLE -> SemType.DOUBLE
        a === SemType.FLOAT || b === SemType.FLOAT -> SemType.FLOAT
        a === SemType.LONG || b === SemType.LONG -> SemType.LONG
        else -> SemType.INT
    }

    private fun comparison(op: Int, l: Typed, r: Typed): BoolExpr = when (promoted(l.type, r.type)) {
        SemType.INT -> IntCompare(op, l.node as IntExpr, r.node as IntExpr)
        SemType.LONG -> LongCompare(op, toLong(l), toLong(r))
        SemType.FLOAT -> FloatCompare(op, toFloat(l), toFloat(r))
        else -> DoubleCompare(op, toDouble(l), toDouble(r))
    }

    private fun arithmetic(op: Int, l: Typed, r: Typed): Typed = when (promoted(l.type, r.type)) {
        SemType.INT -> Typed(SemType.INT, IntArith(op, l.node as IntExpr, r.node as IntExpr))
        SemType.LONG -> Typed(SemType.LONG, LongArith(op, toLong(l), toLong(r)))
        SemType.FLOAT -> Typed(SemType.FLOAT, FloatArith(op, toFloat(l), toFloat(r)))
        else -> Typed(SemType.DOUBLE, DoubleArith(op, toDouble(l), toDouble(r)))
    }

    private fun toShiftDistance(t: Typed): IntExpr =
            if (t.type === SemType.INT) t.node as IntExpr else LongToInt(t.node as LongExpr)

    private fun toLong(t: Typed): LongExpr = when (t.type) {
        SemType.INT -> IntToLong(t.node as IntExpr)
        else -> t.node as LongExpr
    }

    private fun toFloat(t: Typed): FloatExpr = when (t.type) {
        SemType.INT -> IntToFloat(t.node as IntExpr)
        SemType.LONG -> LongToFloat(t.node as LongExpr)
        else -> t.node as FloatExpr
    }

    private fun toDouble(t: Typed): DoubleExpr = when (t.type) {
        SemType.INT -> IntToDouble(t.node as IntExpr)
        SemType.LONG -> LongToDouble(t.node as LongExpr)
        SemType.FLOAT -> FloatToDouble(t.node as FloatExpr)
        else -> t.node as DoubleExpr
    }

    // ------------------------------------------------------------------
    // Java literal typing
    // ------------------------------------------------------------------

    private fun numericLiteral(rawText: String, negated: Boolean): Typed =
            when (val v = parseJavaNumericLiteral(rawText, negated)) {
                is Int -> Typed(SemType.INT, IntConst(v))
                is Long -> Typed(SemType.LONG, LongConst(v))
                is Float -> Typed(SemType.FLOAT, FloatConst(v))
                else -> Typed(SemType.DOUBLE, DoubleConst(v as Double))
            }

    /**
     * Parses a Java numeric literal with exact Java typing; returns a boxed Int, Long, Float
     * or Double. [negated] implements the JLS special case that `-2147483648` /
     * `-9223372036854775808L` are valid literals; for all other values the negation is simply
     * folded into the constant. Internal so the stage-5 Kotlin source generator applies the
     * identical literal typing.
     */
    internal fun parseJavaNumericLiteral(rawText: String, negated: Boolean): Any {
        val text = rawText.replace("_", "")
        fun fail(): Nothing = err("invalid numeric literal: $rawText")

        // hex / binary
        if (text.length > 2 && text[0] == '0' && (text[1].lowercaseChar() == 'x' || text[1].lowercaseChar() == 'b')) {
            val radix = if (text[1].lowercaseChar() == 'x') 16 else 2
            var body = text.substring(2)
            var isLong = false
            if (body.endsWith("l") || body.endsWith("L")) {
                isLong = true
                body = body.dropLast(1)
            }
            val v = body.toULongOrNull(radix) ?: fail()
            if (isLong) {
                val value = v.toLong() // full unsigned 64-bit range wraps, like Java
                return if (negated) -value else value
            }
            if (v > 0xFFFF_FFFFuL) fail() // int hex/binary literals allow the unsigned 32-bit range
            val value = v.toUInt().toInt()
            return if (negated) -value else value
        }

        // float / double
        val last = text.last()
        if (last == 'f' || last == 'F') {
            val v = text.dropLast(1).toFloatOrNull() ?: fail()
            return if (negated) -v else v
        }
        if (last == 'd' || last == 'D') {
            val v = text.dropLast(1).toDoubleOrNull() ?: fail()
            return if (negated) -v else v
        }
        if (text.contains('.') || text.contains('e') || text.contains('E')) {
            val v = text.toDoubleOrNull() ?: fail()
            return if (negated) -v else v
        }

        // decimal / octal integrals
        var body = text
        var isLong = false
        if (last == 'l' || last == 'L') {
            isLong = true
            body = body.dropLast(1)
        }
        if (body.length > 1 && body[0] == '0') {
            // octal (digit validity enforced by the lexer); the full unsigned range wraps
            val v = body.toULongOrNull(8) ?: fail()
            if (isLong) {
                val value = v.toLong()
                return if (negated) -value else value
            }
            if (v > 0xFFFF_FFFFuL) fail()
            val value = v.toUInt().toInt()
            return if (negated) -value else value
        }
        val v = body.toULongOrNull() ?: fail()
        if (isLong) {
            if (negated) {
                if (v > Long.MIN_VALUE.toULong()) fail()
                return if (v == Long.MIN_VALUE.toULong()) Long.MIN_VALUE else -v.toLong()
            }
            if (v > Long.MAX_VALUE.toULong()) fail()
            return v.toLong()
        }
        if (negated) {
            if (v > 2147483648uL) fail()
            return if (v == 2147483648uL) Int.MIN_VALUE else -v.toInt()
        }
        if (v > Int.MAX_VALUE.toULong()) fail()
        return v.toInt()
    }

    /** Unescapes a Java string/char literal body (the lexer already validated the escapes). */
    internal fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') {
                sb.append(c)
                i++
                continue
            }
            i++
            val e = s[i]
            when (e) {
                'b' -> { sb.append('\b'); i++ }
                't' -> { sb.append('\t'); i++ }
                'n' -> { sb.append('\n'); i++ }
                'f' -> { sb.append('\u000C'); i++ }
                'r' -> { sb.append('\r'); i++ }
                '"' -> { sb.append('"'); i++ }
                '\'' -> { sb.append('\''); i++ }
                '\\' -> { sb.append('\\'); i++ }
                in '0'..'7' -> {
                    val maxLen = if (e <= '3') 3 else 2
                    var value = 0
                    var n = 0
                    while (n < maxLen && i < s.length && s[i] in '0'..'7') {
                        value = value * 8 + (s[i] - '0')
                        i++
                        n++
                    }
                    sb.append(value.toChar())
                }
                else -> err("invalid escape sequence '\\$e'")
            }
        }
        return sb.toString()
    }
}
