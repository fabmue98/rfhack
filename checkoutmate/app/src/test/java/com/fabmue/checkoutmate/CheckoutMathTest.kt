package com.fabmue.checkoutmate

import com.fabmue.checkoutmate.domain.CheckoutMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutMathTest {

    @Test
    fun maxCheckoutIsReachable() {
        assertTrue(CheckoutMath.isCheckoutable(170, doubleOut = true))
    }

    @Test
    fun bogeyNumbersAreNotCheckoutable() {
        listOf(159, 162, 163, 165, 166, 168, 169).forEach {
            assertFalse("$it should be a bogey", CheckoutMath.isCheckoutable(it, doubleOut = true))
        }
    }

    @Test
    fun aboveMaxIsNotCheckoutable() {
        assertFalse(CheckoutMath.isCheckoutable(171, doubleOut = true))
        assertFalse(CheckoutMath.isCheckoutable(1, doubleOut = true))
    }

    @Test
    fun straightOutAllowsOne() {
        assertTrue(CheckoutMath.isCheckoutable(1, doubleOut = false))
        assertEquals(7, CheckoutMath.BOGEY_DOUBLE_OUT.size)
    }
}
