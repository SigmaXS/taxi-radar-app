class ParsedOrder {
  final double grossPrice;
  final double netPrice;
  final double commissionAmount;
  final double totalCommissionPercent;
  final String pointA;
  final String pointB;
  final String tariff;
  final String rawText;

  ParsedOrder({
    required this.grossPrice,
    required this.netPrice,
    required this.commissionAmount,
    required this.totalCommissionPercent,
    required this.pointA,
    required this.pointB,
    required this.tariff,
    required this.rawText,
  });
}

class OrderParserService {
  // Стандартные комиссии в Кишинёве (Яндекс ~ 18.5%, Парк ~ 10%)
  static double yandexCommissionPercent = 18.5;
  static double parkCommissionPercent = 10.0;

  static double get totalCommissionPercent =>
      yandexCommissionPercent + parkCommissionPercent;

  /// Парсинг текста скриншота заказа Яндекс Про
  static ParsedOrder parse(String rawText) {
    final lines = rawText
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l.isNotEmpty)
        .toList();

    double grossPrice = 0.0;
    String tariff = 'Эконом';
    String pointA = '';
    String pointB = '';

    // 1. Поиск тарифа
    for (var l in lines) {
      final lower = l.toLowerCase();
      if (lower.contains('комфорт+') || lower.contains('comfort+')) {
        tariff = 'Комфорт+';
        break;
      } else if (lower.contains('комфорт') || lower.contains('comfort')) {
        tariff = 'Комфорт';
        break;
      } else if (lower.contains('эконом') || lower.contains('econom')) {
        tariff = 'Эконом';
        break;
      } else if (lower.contains('доставка') || lower.contains('delivery')) {
        tariff = 'Доставка';
        break;
      }
    }

    // 2. Поиск цены заказа
    // Ищем конструкции типа "85 лей", "85 L", "85 MDL", "~ 85", "85.00"
    final priceRegex = RegExp(
      r'(?:~|\b)?\s*(\d{2,4})\s*(?:лей|lei|mdl|l|л|\$|€)?\b',
      caseSensitive: false,
    );

    for (var l in lines) {
      final matches = priceRegex.allMatches(l);
      for (var m in matches) {
        final val = double.tryParse(m.group(1) ?? '');
        if (val != null && val >= 30 && val <= 1500) {
          // Исключаем совпадения похожие на время/год
          if (val == 1989 || val == 2024 || val == 2025 || val == 2026) continue;
          grossPrice = val;
          break;
        }
      }
      if (grossPrice > 0) break;
    }

    // 3. Поиск точек маршрута
    for (int i = 0; i < lines.length; i++) {
      final line = lines[i];
      final lower = line.toLowerCase();

      if (lower.contains('подача') ||
          lower.contains('откуда') ||
          lower.contains('preluare')) {
        if (i + 1 < lines.length && pointA.isEmpty) {
          pointA = lines[i + 1];
        }
      } else if (lower.contains('куда') ||
          lower.contains('точка б') ||
          lower.contains('destina')) {
        if (i + 1 < lines.length && pointB.isEmpty) {
          pointB = lines[i + 1];
        }
      }
    }

    // Если ключевые слова не найдены, ищем улицы по шаблонам
    if (pointA.isEmpty || pointB.isEmpty) {
      final streetIndicators = [
        'ул.',
        'str.',
        'bd.',
        'бул.',
        'ш.',
        'просп.',
        'шоссе',
        'cal.',
        'calea',
        'strada',
        'bulevardul'
      ];
      final streetLines = <String>[];
      for (var l in lines) {
        final lower = l.toLowerCase();
        if (streetIndicators.any((ind) => lower.contains(ind))) {
          streetLines.add(l);
        }
      }
      if (pointA.isEmpty && streetLines.isNotEmpty) {
        pointA = streetLines.first;
      }
      if (pointB.isEmpty && streetLines.length > 1) {
        pointB = streetLines[1];
      }
    }

    if (pointA.isEmpty) pointA = 'Точка подачи';
    if (pointB.isEmpty) pointB = 'Точка назначения';

    // Если цена не распозналась со скриншота, ставим ориентир по умолчанию
    if (grossPrice <= 0) {
      grossPrice = 65.0;
    }

    final commRate = totalCommissionPercent / 100.0;
    final commissionAmount = grossPrice * commRate;
    final netPrice = grossPrice - commissionAmount;

    return ParsedOrder(
      grossPrice: grossPrice,
      netPrice: netPrice,
      commissionAmount: commissionAmount,
      totalCommissionPercent: totalCommissionPercent,
      pointA: pointA,
      pointB: pointB,
      tariff: tariff,
      rawText: rawText,
    );
  }
}

extension IterableExt<T> on Iterable<T> {
  Iterable<T> filter(bool Function(T) test) => where(test);
}
