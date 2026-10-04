package com.outsmartis.yoke.palette

import java.math.BigDecimal
import java.math.MathContext

/**
 * Small hand-written expression evaluator for the palette's `=` mode. No scripting engine, no eval.
 *
 * Grammar (lowest to highest precedence):
 * ```
 * expr    = term (('+' | '-') term)*
 * term    = unary (('*' | '/' | '%') unary)*
 * unary   = ('-' | '+') unary | power
 * power   = primary ('^' unary)?          right associative, so -2^2 = -4 and 2^3^2 = 512
 * primary = number | '(' expr ')'
 * ```
 */
object Calculator {

    sealed class Result {
        data class Value(val value: Double) : Result() {
            val text: String get() = format(value)
        }

        data class Error(val reason: Reason) : Result()
    }

    enum class Reason { EMPTY, SYNTAX, DIVISION_BY_ZERO, OVERFLOW, TOO_DEEP }

    private const val MAX_DEPTH = 100
    private const val MAX_LENGTH = 500

    fun evaluate(input: String): Result {
        if (input.isBlank()) return Result.Error(Reason.EMPTY)
        if (input.length > MAX_LENGTH) return Result.Error(Reason.SYNTAX)
        return try {
            val parser = Parser(input)
            val value = parser.parse()
            if (value.isNaN() || value.isInfinite()) Result.Error(Reason.OVERFLOW) else Result.Value(value)
        } catch (e: CalcException) {
            Result.Error(e.reason)
        }
    }

    /** Whole numbers without a trailing ".0", otherwise up to 10 significant digits. */
    fun format(value: Double): String {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return value.toLong().toString()
        val text = BigDecimal(value).round(MathContext(10)).stripTrailingZeros().toPlainString()
        return if (text == "-0") "0" else text
    }

    private class CalcException(val reason: Reason) : Exception()

    private class Parser(private val src: String) {
        private var pos = 0
        private var depth = 0

        fun parse(): Double {
            val value = expr()
            skipSpaces()
            if (pos != src.length) fail(Reason.SYNTAX)
            return value
        }

        private fun fail(reason: Reason): Nothing = throw CalcException(reason)

        private fun skipSpaces() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return src.getOrNull(pos)
        }

        private fun expr(): Double {
            var left = term()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; left += term() }
                    '-' -> { pos++; left -= term() }
                    else -> return left
                }
            }
        }

        private fun term(): Double {
            var left = unary()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; left *= unary() }
                    '/' -> {
                        pos++
                        val right = unary()
                        if (right == 0.0) fail(Reason.DIVISION_BY_ZERO)
                        left /= right
                    }
                    '%' -> {
                        pos++
                        val right = unary()
                        if (right == 0.0) fail(Reason.DIVISION_BY_ZERO)
                        left %= right
                    }
                    else -> return left
                }
            }
        }

        private fun unary(): Double {
            if (++depth > MAX_DEPTH) fail(Reason.TOO_DEEP)
            try {
                return when (peek()) {
                    '-' -> { pos++; -unary() }
                    '+' -> { pos++; unary() }
                    else -> power()
                }
            } finally {
                depth--
            }
        }

        private fun power(): Double {
            val base = primary()
            if (peek() == '^') {
                pos++
                val exponent = unary()
                return Math.pow(base, exponent)
            }
            return base
        }

        private fun primary(): Double {
            val c = peek() ?: fail(Reason.SYNTAX)
            if (c == '(') {
                pos++
                if (++depth > MAX_DEPTH) fail(Reason.TOO_DEEP)
                val value = expr()
                depth--
                if (peek() != ')') fail(Reason.SYNTAX)
                pos++
                return value
            }
            return number()
        }

        private fun number(): Double {
            val start = pos
            var digits = 0
            var dots = 0
            while (pos < src.length) {
                val c = src[pos]
                if (c.isDigit() && c.code < 128) digits++
                else if (c == '.') dots++
                else break
                pos++
            }
            if (digits == 0 || dots > 1) fail(Reason.SYNTAX)
            return src.substring(start, pos).toDouble()
        }
    }
}
