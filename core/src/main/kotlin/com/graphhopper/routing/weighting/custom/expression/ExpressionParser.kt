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
 *  The grammar implemented here is the Java conditional-expression grammar as
 *  implemented by Janino's Parser (org.codehaus.janino.Parser#parseConditionalExpression),
 *  restricted to the constructs that GraphHopper's expression whitelist can ever accept.
 *  It was derived by studying Janino's observable parsing behavior. Janino is distributed
 *  under the BSD-3-Clause license, Copyright (c) 2001-2010 Arno Unkrig, Copyright
 *  (c) 2015-2016 TIBCO Software Inc. (https://janino-compiler.github.io/janino/).
 *  No Janino source code was copied; see NOTICE.md.
 */
package com.graphhopper.routing.weighting.custom.expression

/**
 * Self-contained recursive-descent parser for the custom-model expression grammar
 * (kotlin stdlib only, KMP-clean — no `java.*`, no Janino).
 *
 * This is the shared front-end for all custom-model back-ends: it produces the [ExprNode]
 * AST that [ExpressionValidator] validates with exactly the same accept/reject decisions
 * as the Janino-based `ConditionalExpressionVisitor`/`ValueExpressionVisitor` pipeline
 * (proven by ExpressionParserDifferentialTest).
 *
 * Grammar (derived empirically from what Janino's `parseConditionalExpression` accepts and
 * the GraphHopper visitors then whitelist; `{x}` = zero or more):
 *
 * ```
 * expression     := ternary                                 (all input must be consumed)
 * ternary        := or [ '?' ternary ':' ternary ]          (parsed; always rejected by the whitelist)
 * or             := and { '||' and }
 * and            := bitOr { '&&' bitOr }
 * bitOr          := bitXor { '|' bitXor }
 * bitXor         := bitAnd { '^' bitAnd }
 * bitAnd         := equality { '&' equality }
 * equality       := relational { ('==' | '!=') relational }
 * relational     := shift { ('<' | '>' | '<=' | '>=') shift }
 * shift          := additive { ('<<' | '>>' | '>>>') additive }
 * additive       := multiplicative { ('+' | '-') multiplicative }
 * multiplicative := unary { ('*' | '/' | '%') unary }
 * unary          := ('!' | '-' | '+' | '~') unary | primary  ('+'/'~' parsed; rejected by the whitelist)
 * primary        := literal
 *                 | '(' ternary ')'
 *                 | name [ '(' [ ternary { ',' ternary } ] ')' ]
 * name           := IDENTIFIER { '.' IDENTIFIER }
 * literal        := NUMBER | STRING | CHAR | 'true' | 'false' | 'null'
 * ```
 *
 * Constructs Janino parses but the whitelist always rejects (casts, `new`, array access,
 * assignments, `++`/`--`, `instanceof`, chained calls like `a.b().c()`, class literals)
 * are syntax errors here — the accept/reject outcome is identical either way.
 *
 * The whitelisted expression language that actually SURVIVES validation is:
 *  - conditions: comparisons `==` `!=` `<` `<=` `>` `>=` between encoded values, numeric
 *    literals and (via `==`/`!=` only) enum constants written in UPPERCASE (`toll == NO`);
 *    boolean operators `&&` `||` `!`; parentheses; bare boolean encoded values; area
 *    references `in_<area>`; `backward_`/`prev_` encoded-value prefixes; `street_name`,
 *    `prev_street_name`, `change_angle` (turn-penalty context); string/char/numeric
 *    literals; whitelisted calls `X.ordinal|getDistance|getName|contains|sqrt|abs|
 *    isRightHandTraffic|equals(...)` with at most one argument where X is `edge`, `Math`,
 *    `country` or a valid identifier (arithmetic `* / % + - << >> >>> & | ^` between
 *    numeric operands is also accepted, as in the Janino pipeline)
 *  - values: numeric literals, encoded values, `*` `+` `-` (incl. unary minus),
 *    parentheses and `Math.sqrt(x)`
 */
object ExpressionParser {

    /**
     * Parses the expression and returns the AST root.
     *
     * @throws ExpressionSyntaxException if the input is not valid or not fully consumed
     * (mirrors the Janino pipeline's "input must end after the expression" rule).
     */
    @JvmStatic
    fun parse(expression: String): ExprNode {
        val p = P(ExpressionLexer(expression).tokenize())
        val node = p.ternary()
        p.expectEnd()
        return node
    }

    /** Like [parse] but returns null instead of throwing. */
    @JvmStatic
    fun parseOrNull(expression: String): ExprNode? = try {
        parse(expression)
    } catch (e: ExpressionSyntaxException) {
        null
    }

    private class P(private val tokens: List<Token>) {
        private var i = 0

        private fun peek(): Token = tokens[i]

        private fun advance(): Token = tokens[i++]

        private fun isOp(op: String): Boolean {
            val t = peek()
            return t.type == TokenType.OPERATOR && t.text == op
        }

        private fun isOpIn(ops: Array<String>): Boolean {
            val t = peek()
            return t.type == TokenType.OPERATOR && t.text in ops
        }

        private fun expectOp(op: String) {
            if (!isOp(op)) throw ExpressionSyntaxException("expected '$op' but found ${peek()}")
            advance()
        }

        fun expectEnd() {
            if (peek().type != TokenType.END)
                throw ExpressionSyntaxException("unexpected trailing input ${peek()}")
        }

        fun ternary(): ExprNode {
            val c = or()
            if (isOp("?")) {
                advance()
                val t = ternary()
                expectOp(":")
                val f = ternary()
                return ExprNode.Ternary(c, t, f)
            }
            return c
        }

        private fun chain(ops: Array<String>, next: () -> ExprNode): ExprNode {
            var n = next()
            while (isOpIn(ops)) {
                val op = advance().text
                n = ExprNode.Binary(op, n, next())
            }
            return n
        }

        private fun or() = chain(OR_OPS) { and() }
        private fun and() = chain(AND_OPS) { bitOr() }
        private fun bitOr() = chain(BIT_OR_OPS) { bitXor() }
        private fun bitXor() = chain(BIT_XOR_OPS) { bitAnd() }
        private fun bitAnd() = chain(BIT_AND_OPS) { equality() }
        private fun equality() = chain(EQUALITY_OPS) { relational() }
        private fun relational() = chain(RELATIONAL_OPS) { shift() }
        private fun shift() = chain(SHIFT_OPS) { additive() }
        private fun additive() = chain(ADDITIVE_OPS) { multiplicative() }
        private fun multiplicative() = chain(MULTIPLICATIVE_OPS) { unary() }

        private fun unary(): ExprNode {
            if (isOpIn(UNARY_OPS)) {
                val op = advance().text
                return ExprNode.Unary(op, unary())
            }
            return primary()
        }

        private fun primary(): ExprNode {
            val t = peek()
            return when (t.type) {
                TokenType.NUMBER -> { advance(); ExprNode.Literal(LiteralKind.NUMBER, t.text) }
                TokenType.STRING -> { advance(); ExprNode.Literal(LiteralKind.STRING, t.text) }
                TokenType.CHAR -> { advance(); ExprNode.Literal(LiteralKind.CHAR, t.text) }
                TokenType.KEYWORD -> when (t.text) {
                    "true", "false" -> { advance(); ExprNode.Literal(LiteralKind.BOOLEAN, t.text) }
                    "null" -> { advance(); ExprNode.Literal(LiteralKind.NULL, t.text) }
                    else -> throw ExpressionSyntaxException("unexpected keyword ${peek()}")
                }
                TokenType.IDENTIFIER -> nameOrCall()
                TokenType.OPERATOR -> {
                    if (t.text == "(") {
                        advance()
                        val inner = ternary()
                        expectOp(")")
                        ExprNode.Paren(inner)
                    } else {
                        throw ExpressionSyntaxException("unexpected token ${peek()}")
                    }
                }
                TokenType.END -> throw ExpressionSyntaxException("unexpected end of expression")
            }
        }

        private fun nameOrCall(): ExprNode {
            val parts = ArrayList<String>(2)
            parts.add(advance().text)
            while (isOp(".") && tokens[i + 1].type == TokenType.IDENTIFIER) {
                advance() // '.'
                parts.add(advance().text)
            }
            if (isOp("(")) {
                advance()
                val args = ArrayList<ExprNode>(1)
                if (!isOp(")")) {
                    args.add(ternary())
                    while (isOp(",")) {
                        advance()
                        args.add(ternary())
                    }
                }
                expectOp(")")
                return ExprNode.Call(parts.subList(0, parts.size - 1).toList(), parts[parts.size - 1], args)
            }
            return ExprNode.Name(parts)
        }
    }

    private val OR_OPS = arrayOf("||")
    private val AND_OPS = arrayOf("&&")
    private val BIT_OR_OPS = arrayOf("|")
    private val BIT_XOR_OPS = arrayOf("^")
    private val BIT_AND_OPS = arrayOf("&")
    private val EQUALITY_OPS = arrayOf("==", "!=")
    private val RELATIONAL_OPS = arrayOf("<", ">", "<=", ">=")
    private val SHIFT_OPS = arrayOf("<<", ">>", ">>>")
    private val ADDITIVE_OPS = arrayOf("+", "-")
    private val MULTIPLICATIVE_OPS = arrayOf("*", "/", "%")
    private val UNARY_OPS = arrayOf("!", "-", "+", "~")
}
