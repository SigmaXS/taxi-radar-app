package com.example.taxiradar

/**
 * Номера клиентов приводим к одному международному виду, чтобы один и тот же
 * человек всегда совпадал: «078 12 34 56» и «+373 78 123 456» — один номер.
 * Местный формат (с нулём, 9 цифр) считаем молдавским.
 */
object PhoneNumbers {

    // Кандидат в номер внутри любой строки: цифры с пробелами, скобками, дефисами.
    private val candidateRegex = Regex("""\+?\d[\d\s\-()]{6,}\d""")

    /** «+37378123456» или null, если это не похоже на номер телефона. */
    fun normalize(raw: String): String? {
        // Звонилка оборачивает номер в невидимые символы направления текста.
        val cleaned = raw.replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), "")
        val hasPlus = cleaned.trim().startsWith("+")
        var digits = cleaned.filter { it.isDigit() }
        val normalized = when {
            hasPlus -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.length == 9 && digits.startsWith("0") -> "+373" + digits.drop(1)
            digits.length == 8 -> "+373$digits"
            digits.startsWith("373") && digits.length == 11 -> "+$digits"
            else -> return null
        }
        digits = normalized.drop(1)
        return if (digits.length in 8..15) normalized else null
    }

    /** Все номера, найденные в тексте, уже в международном виде. */
    fun findAll(text: String): List<String> =
        candidateRegex.findAll(text.replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), ""))
            .mapNotNull { normalize(it.value) }
            .toList()

    /** Для показа: «+373 78 123 456». */
    fun pretty(number: String): String {
        if (number.startsWith("+373") && number.length == 12) {
            val d = number.drop(4)
            return "+373 ${d.take(2)} ${d.substring(2, 5)} ${d.substring(5)}"
        }
        return number
    }

    /** Для уведомлений на заблокированном экране: «…3456». */
    fun tail(number: String): String = "…" + number.takeLast(4)
}
