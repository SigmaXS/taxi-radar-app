package com.example.taxiradar

/** Minimize keeps capture alive; intermittent OCR misses never reopen the dismissed offer. */
class ScreenOfferVisibility {
    var expanded = false; private set
    var current: String? = null; private set
    private var dismissed: String? = null
    private var lastSeen = 0L
    fun update(identity: String, now: Long) {
        if (current != identity) expanded = identity != dismissed
        current = identity; lastSeen = now
    }
    fun minimize() { dismissed = current; expanded = false }
    fun toggle(now: Long) {
        if (expanded) minimize()
        else if (current != null && now - lastSeen < 8000) expanded = true
    }
    fun missing(now: Long) {
        if (now - lastSeen > 6000) expanded = false
        if (now - lastSeen > 12000) { current = null; dismissed = null }
    }
}
