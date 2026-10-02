class ParsedOrder {
  final double price;
  final String pointA;
  final String pointB;
  final String tariff;
  final String distanceTime;
  final String rawText;

  ParsedOrder({
    required this.price,
    required this.pointA,
    required this.pointB,
    required this.tariff,
    this.distanceTime = '',
    required this.rawText,
  });
}

class OrderParserService {
  /// Парсинг текста скриншота заказа Яндекс Про (без вычета комиссий - чистая цена поездки)
  static ParsedOrder parse(String rawText) {
    final lines = rawText
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l.isNotEmpty)
        .toList();

    double price = 0.0;
    String tariff = 'Эконом';
    String pointA = '';
    String pointB = '';
    String distanceTime = '';

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

    // 2. Поиск цены поездки из Яндекс
    // Ищем конструкции вида "85 лей", "85 L", "85 MDL", "~ 85", "85.00"
    final priceRegex = RegExp(
      r'(?:~|\b)?\s*(\d{2,4})\s*(?:лей|lei|mdl|l|л|\$|€)?\b',
      caseSensitive: false,
    );

    for (var l in lines) {
      final matches = priceRegex.allMatches(l);
      for (var m in matches) {
        final val = double.tryParse(m.group(1) ?? '');
        if (val != null && val >= 30 && val <= 1500) {
          // Исключаем года
          if (val == 1989 || val == 2024 || val == 2025 || val == 2026) continue;
          price = val;
          break;
        }
      }
      if (price > 0) break;
    }

    // 3. Поиск дистанции и времени
    final distRegex = RegExp(
      r'(\d+(?:[.,]\d+)?\s*(?:км|km)\s*[•·,]\s*\d+\s*(?:мин|min)|\d+\s*(?:мин|min)|\d+(?:[.,]\d+)?\s*(?:км|km))',
      caseSensitive: false,
    );
    for (var l in lines) {
      final m = distRegex.firstMatch(l);
      if (m != null) {
        distanceTime = m.group(0)!;
        break;
      }
    }

    // 4. Поиск точек маршрута
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

    if (price <= 0) {
      price = 65.0;
    }

    return ParsedOrder(
      price: price,
      pointA: pointA,
      pointB: pointB,
      tariff: tariff,
      distanceTime: distanceTime,
      rawText: rawText,
    );
  }
}

extension IterableExt<T> on Iterable<T> {
  Iterable<T> filter(bool Function(T) test) => where(test);
}
