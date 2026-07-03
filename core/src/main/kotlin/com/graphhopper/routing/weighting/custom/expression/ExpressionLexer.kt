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
 *  The token grammar in this file (Java-style literals, comments, operators) was
 *  derived by studying the observable behavior of Janino's Scanner
 *  (org.codehaus.janino.Scanner), including its quirks that this lexer reproduces
 *  deliberately for acceptance parity. Janino is distributed under the BSD-3-Clause
 *  license, Copyright (c) 2001-2010 Arno Unkrig, Copyright (c) 2015-2016 TIBCO
 *  Software Inc. (https://janino-compiler.github.io/janino/). No Janino source code
 *  was copied; see NOTICE.md.
 */
package com.graphhopper.routing.weighting.custom.expression

/** Thrown by [ExpressionLexer]/[ExpressionParser] for input that is not lexically/syntactically valid. */
class ExpressionSyntaxException(message: String) : IllegalArgumentException(message)

internal enum class TokenType { IDENTIFIER, KEYWORD, NUMBER, STRING, CHAR, OPERATOR, END }

internal class Token(val type: TokenType, val text: String) {
    override fun toString(): String = if (type == TokenType.END) "<end of input>" else "'$text'"
}

/**
 * Hand-rolled lexer for the custom-model expression language (kotlin stdlib only, KMP-clean).
 *
 * Tokens follow Java's lexical grammar as implemented by Janino's Scanner. Behavior pinned
 * against Janino 3.1.9 (verified empirically, see ExpressionParserDifferentialTest):
 *  - block comments (slash-star ... star-slash) are skipped anywhere; an unterminated one is an error
 *  - line comments `// ...` are skipped, BUT a line comment that runs into the end of input
 *    (no terminating newline) is an error — Janino rejects `a // comment` and accepts
 *    `a // comment\n`
 *  - numeric literals: decimal/hex (0x)/binary (0b)/octal (leading 0, digits 0-7 enforced:
 *    `09` is an error), `_` digit separators (only between digits: `1__0` ok, `1_` error),
 *    float forms `.5`, `5.`, `3.4e+2`, suffixes `fFdD` and (integral only) `lL`
 *  - char literals hold exactly one (possibly escaped) character; standard Java escapes
 *  - identifiers: letter/`_`/`$` start, then also digits (unicode letters allowed)
 *
 * Known deliberate divergence from Janino (documented, irrelevant for custom models):
 * no `\uXXXX` pre-scanning of the whole input (Janino unescapes unicode escapes before lexing).
 */
internal class ExpressionLexer(private val input: String) {
    private var pos = 0

    fun tokenize(): List<Token> {
        val tokens = ArrayList<Token>()
        while (true) {
            val t = next()
            tokens.add(t)
            if (t.type == TokenType.END) return tokens
        }
    }

    private fun next(): Token {
        skipWhitespaceAndComments()
        if (pos >= input.length) return Token(TokenType.END, "")
        val c = input[pos]
        return when {
            isIdentifierStart(c) -> identifier()
            c.isDigit() -> number()
            c == '.' && pos + 1 < input.length && input[pos + 1].isDigit() -> number()
            c == '"' -> stringLiteral()
            c == '\'' -> charLiteral()
            else -> operator()
        }
    }

    private fun skipWhitespaceAndComments() {
        while (pos < input.length) {
            val c = input[pos]
            if (c.isWhitespace()) {
                pos++
            } else if (c == '/' && pos + 1 < input.length && input[pos + 1] == '/') {
                // line comment; Janino parity: must be terminated by a line break before end of input
                pos += 2
                while (pos < input.length && input[pos] != '\n' && input[pos] != '\r') pos++
                if (pos >= input.length) throw ExpressionSyntaxException("line comment not terminated by a line break")
            } else if (c == '/' && pos + 1 < input.length && input[pos + 1] == '*') {
                pos += 2
                while (pos + 1 < input.length && !(input[pos] == '*' && input[pos + 1] == '/')) pos++
                if (pos + 1 >= input.length) throw ExpressionSyntaxException("block comment not terminated")
                pos += 2
            } else {
                return
            }
        }
    }

    private fun identifier(): Token {
        val start = pos
        pos++
        while (pos < input.length && isIdentifierPart(input[pos])) pos++
        val text = input.substring(start, pos)
        return Token(if (text in JAVA_KEYWORDS) TokenType.KEYWORD else TokenType.IDENTIFIER, text)
    }

    /** Scans `digit (digit|_)*`, rejecting a trailing `_` (Java separator rule as enforced by Janino). */
    private fun scanDigits(isDigitFun: (Char) -> Boolean, what: String) {
        if (pos >= input.length || !isDigitFun(input[pos]))
            throw ExpressionSyntaxException("malformed $what literal in expression")
        var lastWasUnderscore = false
        while (pos < input.length && (isDigitFun(input[pos]) || input[pos] == '_')) {
            lastWasUnderscore = input[pos] == '_'
            pos++
        }
        if (lastWasUnderscore) throw ExpressionSyntaxException("malformed $what literal: misplaced '_'")
    }

    private fun number(): Token {
        val start = pos
        if (input[pos] == '0' && pos + 1 < input.length && (input[pos + 1] == 'x' || input[pos + 1] == 'X')) {
            pos += 2
            scanDigits({ it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }, "hex")
            if (pos < input.length && (input[pos] == 'l' || input[pos] == 'L')) pos++
            return Token(TokenType.NUMBER, input.substring(start, pos))
        }
        if (input[pos] == '0' && pos + 1 < input.length && (input[pos + 1] == 'b' || input[pos + 1] == 'B')) {
            pos += 2
            scanDigits({ it == '0' || it == '1' }, "binary")
            if (pos < input.length && (input[pos] == 'l' || input[pos] == 'L')) pos++
            return Token(TokenType.NUMBER, input.substring(start, pos))
        }
        var isFloat = false
        if (input[pos] == '.') {
            // ".5" — leading dot, caller guaranteed a digit follows
            isFloat = true
            pos++
            scanDigits({ it.isDigit() }, "number")
        } else {
            scanDigits({ it.isDigit() }, "number")
            if (pos < input.length && input[pos] == '.') {
                isFloat = true
                pos++
                if (pos < input.length && input[pos].isDigit()) scanDigits({ it.isDigit() }, "number")
            }
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            isFloat = true
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            scanDigits({ it.isDigit() }, "exponent")
        }
        if (pos < input.length && (input[pos] == 'f' || input[pos] == 'F' || input[pos] == 'd' || input[pos] == 'D')) {
            isFloat = true
            pos++
        } else if (pos < input.length && (input[pos] == 'l' || input[pos] == 'L')) {
            if (isFloat) throw ExpressionSyntaxException("malformed number literal: 'L' suffix on floating point number")
            pos++
        }
        val text = input.substring(start, pos)
        if (!isFloat) {
            // octal validation, Janino parity: "09" is rejected, "010" is a valid octal literal
            val digits = text.trimEnd('l', 'L')
            if (digits.length > 1 && digits[0] == '0' && digits.any { it > '7' && it <= '9' })
                throw ExpressionSyntaxException("malformed octal literal: $text")
        }
        return Token(TokenType.NUMBER, text)
    }

    private fun scanEscape(what: String) {
        pos++ // consume backslash
        if (pos >= input.length) throw ExpressionSyntaxException("$what literal not terminated")
        val c = input[pos]
        when {
            c == 'b' || c == 't' || c == 'n' || c == 'f' || c == 'r' || c == '"' || c == '\'' || c == '\\' -> pos++
            c in '0'..'7' -> {
                // octal escape: up to 3 digits, 3 only if first <= '3'
                val maxLen = if (c <= '3') 3 else 2
                var n = 0
                while (n < maxLen && pos < input.length && input[pos] in '0'..'7') {
                    pos++
                    n++
                }
            }
            else -> throw ExpressionSyntaxException("invalid escape sequence '\\$c' in $what literal")
        }
    }

    private fun charLiteral(): Token {
        val start = pos
        pos++ // opening quote
        if (pos >= input.length) throw ExpressionSyntaxException("character literal not terminated")
        when (input[pos]) {
            '\\' -> scanEscape("character")
            '\'', '\n', '\r' -> throw ExpressionSyntaxException("invalid character literal")
            else -> pos++
        }
        if (pos >= input.length || input[pos] != '\'')
            throw ExpressionSyntaxException("character literal not terminated (must contain exactly one character)")
        pos++
        return Token(TokenType.CHAR, input.substring(start, pos))
    }

    private fun stringLiteral(): Token {
        val start = pos
        pos++ // opening quote
        while (pos < input.length) {
            when (input[pos]) {
                '"' -> {
                    pos++
                    return Token(TokenType.STRING, input.substring(start, pos))
                }
                '\\' -> scanEscape("string")
                '\n', '\r' -> throw ExpressionSyntaxException("string literal not terminated before line break")
                else -> pos++
            }
        }
        throw ExpressionSyntaxException("string literal not terminated")
    }

    private fun operator(): Token {
        for (op in OPERATORS) {
            if (input.startsWith(op, pos)) {
                pos += op.length
                return Token(TokenType.OPERATOR, op)
            }
        }
        throw ExpressionSyntaxException("unexpected character '${input[pos]}' in expression")
    }

    companion object {
        private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '$'
        private fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || c.isDigit()

        // longest first for maximal munch (so "--a" lexes as the rejected "--", not "-", "-")
        private val OPERATORS = listOf(
                ">>>=", ">>>", "<<=", ">>=", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
                "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "->", "::",
                "(", ")", "[", "]", "{", "}", ";", ",", ".", "=", "<", ">", "!", "~", "?", ":",
                "&", "|", "^", "+", "-", "*", "/", "%", "@")

        internal val JAVA_KEYWORDS = setOf(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while",
                // literals, handled specially by the parser:
                "true", "false", "null")

        /**
         * Converts the text of a [LiteralKind.NUMBER] literal to a double using Java evaluation
         * semantics (hex/binary/octal integers, `_` separators, `fFdDlL` suffixes stripped),
         * or null if the value cannot be represented (e.g. integral overflow, which Janino
         * rejects at compile time).
         */
        internal fun numericLiteralToDouble(text: String): Double? {
            var t = text.replace("_", "")
            if (t.startsWith("0x") || t.startsWith("0X"))
                return t.substring(2).trimEnd('l', 'L').toLongOrNull(16)?.toDouble()
            if (t.startsWith("0b") || t.startsWith("0B"))
                return t.substring(2).trimEnd('l', 'L').toLongOrNull(2)?.toDouble()
            val isFloat = t.contains('.') || t.contains('e') || t.contains('E') ||
                    t.endsWith("f") || t.endsWith("F") || t.endsWith("d") || t.endsWith("D")
            if (isFloat) {
                if (t.endsWith("f") || t.endsWith("F") || t.endsWith("d") || t.endsWith("D"))
                    t = t.substring(0, t.length - 1)
                return t.toDoubleOrNull()
            }
            t = t.trimEnd('l', 'L')
            if (t.length > 1 && t[0] == '0') return t.toLongOrNull(8)?.toDouble()
            return t.toLongOrNull()?.toDouble()
        }
    }
}
