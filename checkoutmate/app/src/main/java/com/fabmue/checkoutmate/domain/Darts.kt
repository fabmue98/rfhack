package com.fabmue.checkoutmate.domain

/** The ring a dart landed in. */
enum class Ring { MISS, SINGLE, DOUBLE, TRIPLE, OUTER_BULL, BULL }

/**
 * A single thrown dart. [segment] is 1..20 for the numbered beds, ignored for
 * miss/bull. Value and double-ness are derived.
 */
data class Dart(val segment: Int, val ring: Ring) {

    val value: Int
        get() = when (ring) {
            Ring.MISS -> 0
            Ring.SINGLE -> segment
            Ring.DOUBLE -> segment * 2
            Ring.TRIPLE -> segment * 3
            Ring.OUTER_BULL -> 25
            Ring.BULL -> 50
        }

    /** The bull (50) counts as a double for double-in / double-out. */
    val isDouble: Boolean
        get() = ring == Ring.DOUBLE || ring == Ring.BULL

    /** Short label such as T20, D16, 25, Bull, 5. */
    val label: String
        get() = when (ring) {
            Ring.MISS -> "–"
            Ring.SINGLE -> segment.toString()
            Ring.DOUBLE -> "D$segment"
            Ring.TRIPLE -> "T$segment"
            Ring.OUTER_BULL -> "25"
            Ring.BULL -> "Bull"
        }

    companion object {
        val MISS = Dart(0, Ring.MISS)
        val OUTER_BULL = Dart(25, Ring.OUTER_BULL)
        val BULL = Dart(50, Ring.BULL)
        fun single(n: Int) = Dart(n, Ring.SINGLE)
        fun double(n: Int) = Dart(n, Ring.DOUBLE)
        fun triple(n: Int) = Dart(n, Ring.TRIPLE)
    }
}
