package com.example.taxiradar

import android.content.Context

/**
 * Сервер лицензий отвечает по-русски. Для румынского интерфейса переводим
 * известные ответы по тексту; незнакомый ответ показываем как есть.
 */
object ServerText {

    private val exact = mapOf(
        "Активирован бесплатный доступ на 3 дня!" to R.string.srv_trial_activated,
        "Активирован бесплатный доступ на 7 дней!" to R.string.srv_trial_activated,
        "Введите ключ и ID" to R.string.srv_enter_key_and_id,
        "Введите код" to R.string.srv_enter_code,
        "Код друга уже введён" to R.string.srv_code_already,
        "Код не найден" to R.string.srv_code_not_found,
        "Неверный ключ или уже активирован" to R.string.srv_bad_key,
        "Нельзя ввести свой собственный код" to R.string.srv_own_code,
        "Ошибка базы данных" to R.string.srv_db_error,
        "Ошибка сервера БД" to R.string.srv_db_error,
        "Подписка отключена. Введите ключ." to R.string.srv_disabled,
        "Пробный период активен" to R.string.srv_trial_active,
        "Пробный период завершен. Введите ключ." to R.string.srv_trial_finished,
        "Пробный период на этом устройстве уже был использован. Введите ключ." to R.string.srv_trial_used,
        "Сначала активируйте доступ" to R.string.srv_activate_first,
        "Срок действия подписки истек" to R.string.srv_sub_expired,
        "Устройство в бане!" to R.string.srv_banned,
        "Устройство заблокировано (БАН)!" to R.string.srv_banned,
        "Устройство заблокировано" to R.string.srv_banned,
        "Устройство не найдено" to R.string.srv_not_found,
    )

    private val prefixes = listOf(
        "Код принят!" to R.string.srv_code_accepted,
        "Успешно! Доступ открыт" to R.string.srv_key_ok,
    )

    fun localize(context: Context, message: String): String {
        // Интерфейс по-русски — оригинал сервера точнее (в нём есть число дней, тип ключа).
        if (context.getString(R.string.lang_button) == "RU") return message
        val text = message.trim()
        exact[text]?.let { return context.getString(it) }
        prefixes.firstOrNull { text.startsWith(it.first) }?.let { return context.getString(it.second) }
        return message
    }
}
