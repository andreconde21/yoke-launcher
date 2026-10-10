package com.outsmartis.yoke.palette

import com.outsmartis.yoke.palette.Calculator.Reason
import com.outsmartis.yoke.palette.Calculator.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorTest {

    private fun value(expr: String): Double = (Calculator.evaluate(expr) as Result.Value).value

    private fun error(expr: String): Reason = (Calculator.evaluate(expr) as Result.Error).reason

    @Test
    fun precedence() {
        assertEquals(14.0, value("2+3*4"), 0.0)
        assertEquals(2.0, value("10-4*2"), 0.0)
        assertEquals(7.0, value("1+6%4*3"), 0.0) // 6%4=2, *3=6, +1
        assertEquals(2.5, value("10/4"), 0.0)
    }

    @Test
    fun parentheses() {
        assertEquals(20.0, value("(2+3)*4"), 0.0)
        assertEquals(21.0, value("((1+2))*(3+4)"), 0.0)
    }

    @Test
    fun powerIsRightAssociativeAndBindsTighterThanUnaryMinus() {
        assertEquals(512.0, value("2^3^2"), 0.0)
        assertEquals(-4.0, value("-2^2"), 0.0)
        assertEquals(4.0, value("(-2)^2"), 0.0)
        assertEquals(8.0, value("2^3"), 0.0)
    }

    @Test
    fun unaryMinusAndPlus() {
        assertEquals(-5.0, value("-5"), 0.0)
        assertEquals(5.0, value("--5"), 0.0)
        assertEquals(3.0, value("5+-2"), 0.0)
        assertEquals(5.0, value("+5"), 0.0)
        assertEquals(-6.0, value("2*-3"), 0.0)
    }

    @Test
    fun decimalsAndWhitespace() {
        assertEquals(3.75, value(" 1.5 * 2.5 "), 1e-12)
        assertEquals(0.5, value(".5"), 0.0)
    }

    @Test
    fun formatting() {
        assertEquals("14", (Calculator.evaluate("2+3*4") as Result.Value).text)
        assertEquals("0.3", (Calculator.evaluate("0.1+0.2") as Result.Value).text)
        assertEquals("3.333333333", (Calculator.evaluate("10/3") as Result.Value).text)
        assertEquals("-2.5", (Calculator.evaluate("-5/2") as Result.Value).text)
    }

    @Test
    fun divisionByZero() {
        assertEquals(Reason.DIVISION_BY_ZERO, error("1/0"))
        assertEquals(Reason.DIVISION_BY_ZERO, error("5%0"))
        assertEquals(Reason.DIVISION_BY_ZERO, error("1/(2-2)"))
    }

    @Test
    fun garbageInput() {
        assertEquals(Reason.EMPTY, error(""))
        assertEquals(Reason.EMPTY, error("   "))
        for (bad in listOf("abc", "2+", "*3", "(1+2", "1+2)", "1..2", "1 2", "2**3", "System.exit(0)", "1+a", "()", "١٢")) {
            assertEquals("expected syntax error for '$bad'", Reason.SYNTAX, error(bad))
        }
    }

    @Test
    fun overflowAndDepthAreErrorsNotCrashes() {
        assertEquals(Reason.OVERFLOW, error("9^9^9^9"))
        assertEquals(Reason.TOO_DEEP, error("(".repeat(150) + "1" + ")".repeat(150)))
        assertEquals(Reason.SYNTAX, error("1+".repeat(200)))
    }
}
