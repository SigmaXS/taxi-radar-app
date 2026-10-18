package com.example.taxiradar

/** Compare only visible screen pixels, excluding our overlay both before and after it changes size. */
class ScreenFrameStability {
    private var previous: IntArray? = null
    private var previousMask: BooleanArray? = null
    fun changed(pixels: IntArray, ignored: BooleanArray): Boolean {
        require(pixels.size == ignored.size)
        val old = previous; val mask = previousMask
        previous = pixels.copyOf(); previousMask = ignored.copyOf()
        if (old == null || old.size != pixels.size) return true
        var compared = 0; var different = 0
        for (i in pixels.indices) {
            if (ignored[i] || mask!![i]) continue
            compared++
            val a = old[i]; val b = pixels[i]
            val delta = kotlin.math.abs((a shr 16 and 255) - (b shr 16 and 255)) +
                kotlin.math.abs((a shr 8 and 255) - (b shr 8 and 255)) + kotlin.math.abs((a and 255) - (b and 255))
            if (delta > 72) different++
        }
        // If our panel covers everything, there is no evidence of a different offer.
        return compared > 0 && different >= maxOf(3, compared / 100)
    }
}
