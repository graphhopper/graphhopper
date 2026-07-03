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

import kotlin.jvm.JvmField
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Typed evaluator nodes for the closure-composer custom-model back-end (pure Kotlin,
 * KMP-clean — kotlin stdlib only).
 *
 * [TypedCompiler] lowers a validated [ExprNode] AST once into a DAG of these nodes; edge
 * evaluation then only performs virtual `eval()` calls returning primitives — no boxing,
 * no lambda allocation, no reflection. Mutable state is confined to the small *cell*
 * objects that the (JVM-side) loaders fill per evaluated edge/turn, which makes a compiled
 * program exactly as thread-confined as Janino's per-request `CustomWeightingHelper`
 * instance.
 *
 * The nodes implement JAVA evaluation semantics on purpose (`int/int` division stays int,
 * numeric promotion int→long→float→double, wrap-around overflow, `&& || !` on booleans,
 * `& | ^` bitwise or non-short-circuit boolean, `==` on Strings is reference identity)
 * so that results are bit-identical with the code the Janino back-end generates.
 */

// ---------------------------------------------------------------------------
// mutable per-evaluation cells (filled by the loaders, read by the nodes)
// ---------------------------------------------------------------------------

class BoolCell { @JvmField var value = false }
class IntCell { @JvmField var value = 0 }
class DoubleCell { @JvmField var value = 0.0 }
class StringCell { @JvmField var value: String? = null }

// ---------------------------------------------------------------------------
// node base classes, one per Java evaluation type
// ---------------------------------------------------------------------------

abstract class BoolExpr { abstract fun eval(): Boolean }
abstract class IntExpr { abstract fun eval(): Int }
abstract class LongExpr { abstract fun eval(): Long }
abstract class FloatExpr { abstract fun eval(): Float }
abstract class DoubleExpr { abstract fun eval(): Double }
abstract class StringExpr { abstract fun eval(): String? }

// ---------------------------------------------------------------------------
// operator codes (per-instance constant -> perfectly predicted branch)
// ---------------------------------------------------------------------------

internal object EvalOp {
    const val ADD = 0
    const val SUB = 1
    const val MUL = 2
    const val DIV = 3
    const val REM = 4
    const val AND = 5
    const val OR = 6
    const val XOR = 7
    const val SHL = 8
    const val SHR = 9
    const val USHR = 10

    const val EQ = 0
    const val NE = 1
    const val LT = 2
    const val LE = 3
    const val GT = 4
    const val GE = 5
}

// ---------------------------------------------------------------------------
// boolean nodes
// ---------------------------------------------------------------------------

class BoolConst(private val v: Boolean) : BoolExpr() {
    override fun eval(): Boolean = v
}

class BoolCellExpr(private val cell: BoolCell) : BoolExpr() {
    override fun eval(): Boolean = cell.value
}

class NotExpr(private val e: BoolExpr) : BoolExpr() {
    override fun eval(): Boolean = !e.eval()
}

/** short-circuit `&&` */
class AndExpr(private val l: BoolExpr, private val r: BoolExpr) : BoolExpr() {
    override fun eval(): Boolean = l.eval() && r.eval()
}

/** short-circuit `||` */
class OrExpr(private val l: BoolExpr, private val r: BoolExpr) : BoolExpr() {
    override fun eval(): Boolean = l.eval() || r.eval()
}

/** non-short-circuit boolean `& | ^` and `== !=` */
internal class BoolBitExpr(private val op: Int, private val l: BoolExpr, private val r: BoolExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.AND -> a and b
            EvalOp.OR -> a or b
            EvalOp.XOR -> a xor b
            EvalOp.EQ -> a == b
            else -> a != b // NE
        }
    }
}

/** enum property lookup by ordinal, e.g. `country.isRightHandTraffic()` via a precomputed table */
class BoolTableExpr(private val ordinal: IntExpr, private val table: BooleanArray) : BoolExpr() {
    override fun eval(): Boolean = table[ordinal.eval()]
}

// ---------------------------------------------------------------------------
// comparisons (one class per promoted type, operator as per-instance constant)
// ---------------------------------------------------------------------------

internal class IntCompare(private val op: Int, private val l: IntExpr, private val r: IntExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.EQ -> a == b
            EvalOp.NE -> a != b
            EvalOp.LT -> a < b
            EvalOp.LE -> a <= b
            EvalOp.GT -> a > b
            else -> a >= b // GE
        }
    }
}

internal class LongCompare(private val op: Int, private val l: LongExpr, private val r: LongExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.EQ -> a == b
            EvalOp.NE -> a != b
            EvalOp.LT -> a < b
            EvalOp.LE -> a <= b
            EvalOp.GT -> a > b
            else -> a >= b
        }
    }
}

internal class FloatCompare(private val op: Int, private val l: FloatExpr, private val r: FloatExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.EQ -> a == b
            EvalOp.NE -> a != b
            EvalOp.LT -> a < b
            EvalOp.LE -> a <= b
            EvalOp.GT -> a > b
            else -> a >= b
        }
    }
}

internal class DoubleCompare(private val op: Int, private val l: DoubleExpr, private val r: DoubleExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.EQ -> a == b
            EvalOp.NE -> a != b
            EvalOp.LT -> a < b
            EvalOp.LE -> a <= b
            EvalOp.GT -> a > b
            else -> a >= b
        }
    }
}

// ---------------------------------------------------------------------------
// int nodes (Java int semantics: truncating division, wrap-around overflow,
// ArithmeticException on /0, shift distance masked to 5 bits)
// ---------------------------------------------------------------------------

class IntConst(private val v: Int) : IntExpr() {
    override fun eval(): Int = v
}

class IntCellExpr(private val cell: IntCell) : IntExpr() {
    override fun eval(): Int = cell.value
}

internal class IntNeg(private val e: IntExpr) : IntExpr() {
    override fun eval(): Int = -e.eval()
}

internal class IntAbs(private val e: IntExpr) : IntExpr() {
    override fun eval(): Int = abs(e.eval())
}

internal class IntArith(private val op: Int, private val l: IntExpr, private val r: IntExpr) : IntExpr() {
    override fun eval(): Int {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.ADD -> a + b
            EvalOp.SUB -> a - b
            EvalOp.MUL -> a * b
            EvalOp.DIV -> a / b
            EvalOp.REM -> a % b
            EvalOp.AND -> a and b
            EvalOp.OR -> a or b
            EvalOp.XOR -> a xor b
            EvalOp.SHL -> a shl b
            EvalOp.SHR -> a shr b
            else -> a ushr b // USHR
        }
    }
}

// ---------------------------------------------------------------------------
// long nodes
// ---------------------------------------------------------------------------

class LongConst(private val v: Long) : LongExpr() {
    override fun eval(): Long = v
}

internal class LongNeg(private val e: LongExpr) : LongExpr() {
    override fun eval(): Long = -e.eval()
}

internal class LongAbs(private val e: LongExpr) : LongExpr() {
    override fun eval(): Long = abs(e.eval())
}

internal class LongArith(private val op: Int, private val l: LongExpr, private val r: LongExpr) : LongExpr() {
    override fun eval(): Long {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.ADD -> a + b
            EvalOp.SUB -> a - b
            EvalOp.MUL -> a * b
            EvalOp.DIV -> a / b
            EvalOp.REM -> a % b
            EvalOp.AND -> a and b
            EvalOp.OR -> a or b
            EvalOp.XOR -> a xor b
            EvalOp.SHL -> a shl b.toInt()
            EvalOp.SHR -> a shr b.toInt()
            else -> a ushr b.toInt() // USHR
        }
    }
}

// ---------------------------------------------------------------------------
// float nodes (only reachable via `f` literals, but semantics kept exact)
// ---------------------------------------------------------------------------

class FloatConst(private val v: Float) : FloatExpr() {
    override fun eval(): Float = v
}

internal class FloatNeg(private val e: FloatExpr) : FloatExpr() {
    override fun eval(): Float = -e.eval()
}

internal class FloatAbs(private val e: FloatExpr) : FloatExpr() {
    override fun eval(): Float = abs(e.eval())
}

internal class FloatArith(private val op: Int, private val l: FloatExpr, private val r: FloatExpr) : FloatExpr() {
    override fun eval(): Float {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.ADD -> a + b
            EvalOp.SUB -> a - b
            EvalOp.MUL -> a * b
            EvalOp.DIV -> a / b
            else -> a % b // REM
        }
    }
}

// ---------------------------------------------------------------------------
// double nodes
// ---------------------------------------------------------------------------

class DoubleConst(private val v: Double) : DoubleExpr() {
    override fun eval(): Double = v
}

class DoubleCellExpr(private val cell: DoubleCell) : DoubleExpr() {
    override fun eval(): Double = cell.value
}

internal class DoubleNeg(private val e: DoubleExpr) : DoubleExpr() {
    override fun eval(): Double = -e.eval()
}

internal class DoubleAbs(private val e: DoubleExpr) : DoubleExpr() {
    override fun eval(): Double = abs(e.eval())
}

internal class DoubleSqrt(private val e: DoubleExpr) : DoubleExpr() {
    override fun eval(): Double = sqrt(e.eval())
}

internal class DoubleArith(private val op: Int, private val l: DoubleExpr, private val r: DoubleExpr) : DoubleExpr() {
    override fun eval(): Double {
        val a = l.eval()
        val b = r.eval()
        return when (op) {
            EvalOp.ADD -> a + b
            EvalOp.SUB -> a - b
            EvalOp.MUL -> a * b
            EvalOp.DIV -> a / b
            else -> a % b // REM
        }
    }
}

// ---------------------------------------------------------------------------
// widening conversions (Java numeric promotion)
// ---------------------------------------------------------------------------

internal class IntToLong(private val e: IntExpr) : LongExpr() {
    override fun eval(): Long = e.eval().toLong()
}

internal class IntToFloat(private val e: IntExpr) : FloatExpr() {
    override fun eval(): Float = e.eval().toFloat()
}

internal class IntToDouble(private val e: IntExpr) : DoubleExpr() {
    override fun eval(): Double = e.eval().toDouble()
}

internal class LongToFloat(private val e: LongExpr) : FloatExpr() {
    override fun eval(): Float = e.eval().toFloat()
}

internal class LongToDouble(private val e: LongExpr) : DoubleExpr() {
    override fun eval(): Double = e.eval().toDouble()
}

internal class LongToInt(private val e: LongExpr) : IntExpr() {
    override fun eval(): Int = e.eval().toInt() // shift distances only: low bits are what counts
}

internal class FloatToDouble(private val e: FloatExpr) : DoubleExpr() {
    override fun eval(): Double = e.eval().toDouble()
}

// ---------------------------------------------------------------------------
// String nodes. `==`/`!=` on Strings is Java REFERENCE identity; the compiler pools
// string literals per program which reproduces the JVM's literal interning.
// ---------------------------------------------------------------------------

class StringConst(private val v: String?) : StringExpr() {
    override fun eval(): String? = v
}

class StringCellExpr(private val cell: StringCell) : StringExpr() {
    override fun eval(): String? = cell.value
}

internal class StringIdentity(private val l: StringExpr, private val r: StringExpr, private val negate: Boolean) : BoolExpr() {
    override fun eval(): Boolean = (l.eval() === r.eval()) != negate
}

internal class StringIsNull(private val e: StringExpr, private val negate: Boolean) : BoolExpr() {
    override fun eval(): Boolean = (e.eval() == null) != negate
}

/** `receiver.equals(arg)` — structural; NPE for a null receiver exactly like Java */
internal class StringEqualsExpr(private val recv: StringExpr, private val arg: StringExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val r = recv.eval() ?: throw NullPointerException("equals() called on null String")
        return r == arg.eval()
    }
}

/** `receiver.contains(arg)` — NPE for null receiver or argument exactly like Java */
internal class StringContainsExpr(private val recv: StringExpr, private val arg: StringExpr) : BoolExpr() {
    override fun eval(): Boolean {
        val r = recv.eval() ?: throw NullPointerException("contains() called on null String")
        val a = arg.eval() ?: throw NullPointerException("contains(null)")
        return r.contains(a)
    }
}

// ---------------------------------------------------------------------------
// compiled statement runtime: exactly the structure of the Janino-generated
// getSpeed/getPriority/getTurnPenalty bodies — sequential if/else-if/else groups
// folding into a double accumulator via multiply_by / limit_to / add.
// ---------------------------------------------------------------------------

class CompiledGroup(@JvmField internal val statements: Array<CompiledStatement>)

sealed class CompiledStatement(@JvmField internal val condition: BoolExpr?) {

    /** leaf statement: `value *= x`, `value = Math.min(value, x)` or `value += x` */
    class Leaf(condition: BoolExpr?, @JvmField internal val op: Int, @JvmField internal val value: DoubleExpr) :
            CompiledStatement(condition)

    /** `do` block: nested if/else-if/else groups */
    class Block(condition: BoolExpr?, @JvmField internal val groups: Array<CompiledGroup>) :
            CompiledStatement(condition)

    companion object {
        const val OP_MULTIPLY = 0
        const val OP_LIMIT = 1
        const val OP_ADD = 2
    }
}

/**
 * The compiled body of one generated method (getSpeed/getPriority/getTurnPenalty).
 * Running it is allocation-free.
 */
class StatementProgram(private val groups: Array<CompiledGroup>, private val initial: Double) {

    fun run(): Double = runGroups(initial, groups)

    private fun runGroups(start: Double, groups: Array<CompiledGroup>): Double {
        var value = start
        for (g in groups) {
            val statements = g.statements
            for (i in statements.indices) {
                val st = statements[i]
                val condition = st.condition
                if (condition == null || condition.eval()) {
                    value = when (st) {
                        is CompiledStatement.Leaf -> when (st.op) {
                            CompiledStatement.OP_MULTIPLY -> value * st.value.eval()
                            CompiledStatement.OP_LIMIT -> min(value, st.value.eval())
                            else -> value + st.value.eval() // OP_ADD
                        }
                        is CompiledStatement.Block -> runGroups(value, st.groups)
                    }
                    break
                }
            }
        }
        return value
    }
}
