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
    fun wasMinimized(identity: String) = dismissed == identity
    fun minimize() { dismissed = current; expanded = false }
    fun absent() { current = null; expanded = false }
    fun toggle(now: Long) {
        if (expanded) minimize()
        else if (current != null && now - lastSeen < 8000) expanded = true
    }
    fun missing(now: Long, hideAfter: Long = 6000) {
        if (now - lastSeen >= hideAfter) expanded = false
        // A gap in OCR must not forget the user's explicit minimize action.
        if (now - lastSeen > 12000) current = null
    }
}
