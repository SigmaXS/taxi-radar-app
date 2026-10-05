import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';
import 'api_service.dart';

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
    // Сначала ищем четкие совпадения с валютой: "85 лей", "85 L", "85 MDL", "~ 85"
    final currencyPriceRegex = RegExp(
      r'(?:~|\b)?\s*(\d{2,4})\s*(?:лей|lei|mdl|l|л|леев)\b',
      caseSensitive: false,
    );
    for (var l in lines) {
      final m = currencyPriceRegex.firstMatch(l);
      if (m != null) {
        final val = double.tryParse(m.group(1) ?? '');
        if (val != null && val >= 30 && val <= 1500) {
          price = val;
          break;
        }
      }
    }

    // Если точного совпадения с валютой нет, ищем любое число в диапазоне тарифа
    if (price <= 0) {
      final priceRegex = RegExp(
        r'(?:~|\b)?\s*(\d{2,4})\b',
        caseSensitive: false,
      );
      for (var l in lines) {
        final matches = priceRegex.allMatches(l);
        for (var m in matches) {
          final val = double.tryParse(m.group(1) ?? '');
          if (val != null && val >= 30 && val <= 1500) {
            if (val == 1989 || val == 2024 || val == 2025 || val == 2026) continue;
            price = val;
            break;
          }
        }
        if (price > 0) break;
      }
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

    return ParsedOrder(
      price: price,
      pointA: pointA,
      pointB: pointB,
      tariff: tariff,
      distanceTime: distanceTime,
      rawText: rawText,
    );
  }

  /// Обогащение заказа: геокодирование адресов через Railway (Яндекс API)
  /// и расчет дистанции/времени/цены поездки, если они отсутствовали в тексте
  static Future<ParsedOrder> enrich(ParsedOrder order) async {
    String distanceTime = order.distanceTime;
    double price = order.price;

    final hasRealPointA = order.pointA.isNotEmpty && order.pointA != 'Точка подачи';
    final hasRealPointB = order.pointB.isNotEmpty && order.pointB != 'Точка назначения';

    if (hasRealPointA && hasRealPointB) {
      try {
        final futureA = ApiService.geocodeAddress(order.pointA);
        final futureB = ApiService.geocodeAddress(order.pointB);
        final coords = await Future.wait([futureA, futureB]);
        final coordA = coords[0];
        final coordB = coords[1];

        if (coordA != null && coordB != null) {
          final distMeters = Geolocator.distanceBetween(
            coordA['lat']!,
            coordA['lon']!,
            coordB['lat']!,
            coordB['lon']!,
          );
          // Коэффициент извилистости дорог в городе ~1.35
          final distKm = (distMeters / 1000.0) * 1.35;
          final durationMin = (distKm * 2.3).round().clamp(3, 120);

          if (distanceTime.isEmpty) {
            distanceTime = '${distKm.toStringAsFixed(1)} км · $durationMin мин';
          }

          if (price <= 0) {
            // Базовый тариф без вычета комиссии: подача 30 + 3.5 за км + 1.0 за минуту
            price = (30.0 + (distKm * 3.5) + durationMin).roundToDouble();
          }
        }
      } catch (e) {
        if (kDebugMode) print('Order enrichment error: $e');
      }
    }

    if (price <= 0) {
      price = 65.0;
    }

    return ParsedOrder(
      price: price,
      pointA: order.pointA,
      pointB: order.pointB,
      tariff: order.tariff,
      distanceTime: distanceTime,
      rawText: order.rawText,
    );
  }
}

extension IterableExt<T> on Iterable<T> {
  Iterable<T> filter(bool Function(T) test) => where(test);
}

