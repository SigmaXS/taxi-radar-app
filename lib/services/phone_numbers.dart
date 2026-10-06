/// Номера клиентов — в одном международном виде, как в Android (PhoneNumbers.kt):
/// «078 12 34 56» и «+373 78 123 456» — один и тот же человек. Сервер принимает
/// только «+373…»: местный формат он отклонял, и проверка «ничего не делала».
class PhoneNumbers {
  static final _bidi = RegExp(r'[\u200E\u200F\u202A-\u202E\u2066-\u2069]', unicode: true);

  /// «+37378123456» или null, если это не похоже на номер.
  static String? normalize(String raw) {
    final cleaned = raw.replaceAll(_bidi, '').trim();
    final hasPlus = cleaned.startsWith('+');
    final digits = cleaned.replaceAll(RegExp(r'\D'), '');
    String normalized;
    if (hasPlus) {
      normalized = '+$digits';
    } else if (digits.startsWith('00')) {
      normalized = '+${digits.substring(2)}';
    } else if (digits.length == 9 && digits.startsWith('0')) {
      normalized = '+373${digits.substring(1)}';
    } else if (digits.length == 8) {
      normalized = '+373$digits';
    } else if (digits.startsWith('373') && digits.length == 11) {
      normalized = '+$digits';
    } else {
      return null;
    }
    final n = normalized.length - 1;
    return n >= 8 && n <= 15 ? normalized : null;
  }

  /// Для показа: «+373 78 123 456».
  static String pretty(String number) {
    if (number.startsWith('+373') && number.length == 12) {
      final d = number.substring(4);
      return '+373 ${d.substring(0, 2)} ${d.substring(2, 5)} ${d.substring(5)}';
    }
    return number;
  }
}
