package com.example.taxiradar

object BoltAddressNormalizer {
    fun normalize(raw: String): String {
        var q = raw.trim().replace(Regex("\\s+"), " ")
        val places = mapOf("Пересечина" to "Peresecina", "Пересечино" to "Peresecina", "Тирасполь" to "Tiraspol", "Кишинёв" to "Chișinău", "Кишинев" to "Chișinău")
        places.forEach { (from, to) -> q = q.replace(from, to, ignoreCase = true) }
        q = q.replace(Regex("(?iu)(?:\\bBlocul|Блокул)\\s+"), "")
        val parts = q.split(',', limit = 2)
        if (parts.size == 2 && parts[1].trim().startsWith(parts[0].trim() + " ", ignoreCase = true)) q = parts[1].trim()
        return if (Regex("(?iu)(moldova|молдов)").containsMatchIn(q)) q else "$q, Moldova"
    }
}
