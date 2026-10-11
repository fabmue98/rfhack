package com.fabmue.checkoutmate.domain

/**
 * Lightweight checkout knowledge used by the scoring engine (no Android deps).
 * The full suggestion table lives in assets and is loaded by CheckoutRepository.
 */
object CheckoutMath {

    /** Scores 2..170 that cannot be finished in three darts ending on a double. */
    val BOGEY_DOUBLE_OUT = setOf(159, 162, 163, 165, 166, 168, 169)

    /** Whether [remaining] can, in principle, be finished this visit. */
    fun isCheckoutable(remaining: Int, doubleOut: Boolean): Boolean =
        if (doubleOut) {
            remaining in 2..170 && remaining !in BOGEY_DOUBLE_OUT
        } else {
            remaining in 1..170
        }
}
