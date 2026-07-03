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
 *  The AST node shapes in this file are modeled after the subset of Janino's
 *  Java AST (org.codehaus.janino.Java: AmbiguousName, Literal, UnaryOperation,
 *  BinaryOperation, MethodInvocation, ParenthesizedExpression, ConditionalExpression)
 *  that GraphHopper's custom-model expressions use. Janino is distributed under
 *  the BSD-3-Clause license, Copyright (c) 2001-2010 Arno Unkrig,
 *  Copyright (c) 2015-2016 TIBCO Software Inc. (https://janino-compiler.github.io/janino/).
 *  No Janino source code was copied; see NOTICE.md.
 */
package com.graphhopper.routing.weighting.custom.expression

/**
 * AST for the custom-model expression language produced by [ExpressionParser].
 *
 * The node set deliberately mirrors the Janino AST shapes that the (JVM-only) Janino
 * back-end accepts via `ConditionalExpressionVisitor`/`ValueExpressionVisitor`, so that
 * [ExpressionValidator] can enforce identical accept/reject decisions. This file is pure
 * Kotlin (KMP-clean) and must stay free of `java.*` and Janino imports.
 */
sealed interface ExprNode {

    /** A literal token exactly as written, e.g. `0.9`, `0x10`, `"main"`, `'c'`, `true`, `null`. */
    data class Literal(val kind: LiteralKind, val text: String) : ExprNode

    /**
     * A possibly dotted name, e.g. `road_class` or `java.lang.Object`. Only single-part
     * names are ever accepted by the validator (mirrors Janino's `AmbiguousName`).
     */
    data class Name(val parts: List<String>) : ExprNode {
        val single: String?
            get() = if (parts.size == 1) parts[0] else null

        override fun toString(): String = parts.joinToString(".")
    }

    /** A unary operation, e.g. `!x` or `-x`. Only `!` and `-` are ever accepted. */
    data class Unary(val op: String, val operand: ExprNode) : ExprNode

    /** A binary operation of the Java operator ladder, e.g. `a && b`, `a == B`, `a * 2`. */
    data class Binary(val op: String, val lhs: ExprNode, val rhs: ExprNode) : ExprNode

    /** The ternary `?:` operator. Parsed for parity with Janino but always rejected. */
    data class Ternary(val condition: ExprNode, val ifTrue: ExprNode, val ifFalse: ExprNode) : ExprNode

    /**
     * A method invocation `target.method(args)`; [target] holds the dotted target parts and
     * is empty for a bare call like `sqrt(2)` (mirrors Janino's `MethodInvocation` whose
     * `target` is `null` in that case). Only calls with exactly one target part and at most
     * one argument are ever accepted.
     */
    data class Call(val target: List<String>, val method: String, val args: List<ExprNode>) : ExprNode

    /** A parenthesized expression `( inner )`. */
    data class Paren(val inner: ExprNode) : ExprNode
}

/** Kind of an [ExprNode.Literal]. */
enum class LiteralKind {
    /** Integral (decimal/hex/octal/binary) or floating point literal, incl. suffixes. */
    NUMBER,
    STRING,
    CHAR,
    BOOLEAN,
    NULL
}
