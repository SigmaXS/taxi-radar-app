import 'dart:math' as math;

import 'fare_calculator.dart';

/// Заказ, разобранный из текста скриншота карточки Яндекс Про.
class ParsedOrder {
  /// Цена поездки по нашему расчёту; 0 — ещё считаем или посчитать не вышло.
  final double price;
  final String pointA;
  final String pointB;
  final List<String> stops;
  final String tariff;

  /// «9.4 км · 15 мин» — для виджета.
  final String distanceTime;
  final String rawText;

  /// Надбавка с кнопки «Принять» («+15 L») и платная подача — уже в цене.
  final int surgeBonus;

  /// Подача: «800 m · 4 min» → 0.8 км.
  final double pickupKm;

  /// Км и минуты, которые Яндекс сам подписал на мини-карте (длинные поездки).
  final double? cardKm;
  final int? cardMin;

  /// true — цену ещё считаем; false и price == 0 — посчитать не вышло.
  final bool calculating;

  const ParsedOrder({
    required this.price,
    required this.pointA,
    required this.pointB,
    required this.tariff,
    this.stops = const [],
    this.distanceTime = '',
    required this.rawText,
    this.surgeBonus = 0,
    this.pickupKm = 0,
    this.cardKm,
    this.cardMin,
    this.calculating = false,
  });

  bool get hasRoute => pointA.isNotEmpty && pointB.isNotEmpty;

  /// «~109 L», «считаю…» или «нет цены» — как на виджете Android.
  String get priceText {
    if (calculating) return 'считаю…';
    if (price > 0) return '~${price.round()} L';
    return hasRoute ? 'нет цены' : 'адрес не распознан';
  }

  ParsedOrder copyWith({double? price, String? distanceTime, bool? calculating, int? stopsCount}) => ParsedOrder(
        price: price ?? this.price,
        pointA: pointA,
        pointB: pointB,
        tariff: tariff,
        stops: stopsCount == null ? stops : stops.take(stopsCount).toList(),
        distanceTime: distanceTime ?? this.distanceTime,
        rawText: rawText,
        surgeBonus: surgeBonus,
        pickupKm: pickupKm,
        cardKm: cardKm,
        cardMin: cardMin,
        calculating: calculating ?? this.calculating,
      );
}

/// Разбор текста карточки — те же правила, что у Android (OrderAccessibilityService.kt),
/// но для текста, распознанного со скриншота: в нём есть подписи улиц с карты
/// и комментарий пассажира, которые нельзя принять за адреса.
class OrderParserService {
  // «+55 L», «+15 MDL», «+ 35 лей». Без единицы — только 5…150.
  static final _plusAmount =
      RegExp(r'\+\s*(\d{1,3})(?:[.,]\d+)?\s*(L|Л|лей|lei|MDL)?(?![\p{L}\d])', caseSensitive: false, unicode: true);
  static final _paidPickup = RegExp(
      r'(платн\S*\s+подач|pl[aă]t\S*\s+(?:a\s+)?(?:prelu|deplas)|preluare\s+pl[aă]t|paid\s+pick)',
      caseSensitive: false);
  static final _pickup = RegExp(r'^(\d+(?:[.,]\d+)?)\s*(км|м|km|m)\s*[·•]\s*\d+\s*(мин|min)', caseSensitive: false);
  static final _cardKm = RegExp(r'^(\d+(?:[.,]\d+)?)\s*(км|km)$', caseSensitive: false);
  static final _cardMin = RegExp(r'^(?:(\d+)\s*(ч|h)\s*)?(\d+)\s*(мин|min)\.?$', caseSensitive: false);
  static final _unit = RegExp(r'(\d\s*(км|м|мин|km|m|min|l|lei|лей)(?![\p{L}]))|((?<![\p{L}])(l|lei)\s*\d)',
      caseSensitive: false, unicode: true);
  static final _streetWord = RegExp(
      r'(^|[\s,.])(str|strada|stradela|bd|bul|bulevardul|șos|şos|sos|soseaua|șoseaua|aleea|piața|piata|calea|ул|улица|пр|просп|проспект|бул|бульвар|шоссе|пер|переулок|село|satul|sat|com)[\s.,]',
      caseSensitive: false);
  static final _entrance =
      RegExp(r'^(entrance|подъезд|scara|scară|poarta|ворота|этаж|etaj|кв|ap)(?![\p{L}])', caseSensitive: false, unicode: true);
  // Метка точки, которую распознавание склеило с адресом: «A strada …», «(B) …».
  static final _markerPrefix = RegExp(r'^(?:\(?[AАBБ]\)?|Ⓐ|Ⓑ)\s+(?=\S)');
  static final _markerOnly = RegExp(r'^(?:\(?[AАBБ]\)?|Ⓐ|Ⓑ)$');
  static final _letter = RegExp(r'\p{L}', unicode: true);
  static final _nonLetter = RegExp(r'[^\p{L}]', unicode: true);
  static final _digit = RegExp(r'\d');

  // С этих строк — комментарий пассажира и кнопки, адресов там нет.
  static const _tailWords = ['pasager', 'пассажир', 'comentariu', 'комментар', 'acceptă', 'accepta', 'принять'];

  static bool _isService(String t) {
    final lower = t.toLowerCase();
    if (_unit.hasMatch(t) || lower.contains('·') || lower.contains('•') || lower.startsWith('+')) return true;
    const words = [
      'принять', 'пропустить', 'подача', 'вы находитесь', 'я здесь', 'уточнить', 'приоритет',
      'accept', 'omite', 'preluare', 'accesul la comenzi', 'prioritate', 'are you here', 'pasager', 'пассажир',
    ];
    if (words.any(lower.contains)) return true;
    const tariffs = ['эконом', 'комфорт', 'комфорт+', 'econom', 'comfort', 'comfort+', 'confort', 'confort+'];
    return tariffs.contains(lower);
  }

  /// Похоже на адрес: улица со словом-типом или с номером дома.
  static bool _looksLikeAddress(String t) {
    if (t.length < 5 || _isService(t) || !_letter.hasMatch(t)) return false;
    final l = t.toLowerCase();
    if (_entrance.hasMatch(l)) return false;
    // Подписи районов и улиц на карте — заглавными («BOTANICA», «str. ISMAIL»).
    final letters = t.replaceAll(_nonLetter, '');
    final caps = letters.replaceAll(RegExp(r'^(str|bd|ул)', caseSensitive: false), '');
    if (caps.length >= 4 && caps == caps.toUpperCase() && !_digit.hasMatch(t)) return false;
    return _digit.hasMatch(t) || _streetWord.hasMatch(' $l ');
  }

  static bool isDelivery(List<String> lines) {
    final l = lines.map((s) => s.toLowerCase()).toList();
    return l.any((s) => s == 'доставка' || s == 'livrare') &&
        l.any((s) => s.contains('получени') || s.contains('вручени') || s == 'откуда' || s == 'de unde');
  }

  static ParsedOrder parse(String rawText) {
    final lines = rawText.split('\n').map((l) => l.trim()).where((l) => l.isNotEmpty).toList();
    final lower = lines.map((l) => l.toLowerCase()).toList();

    var tariff = 'Эконом';
    if (lower.any((l) => l.contains('комфорт+') || l.contains('comfort+') || l.contains('confort+'))) {
      tariff = 'Комфорт+';
    } else if (lower.any((l) => l.contains('комфорт') || l.contains('comfort') || l.contains('confort'))) {
      tariff = 'Комфорт';
    }

    // Надбавка: платная подача отдельно, остальное «+N» — с кнопки «Принять».
    var paid = 0, surge = 0;
    final used = <int>{};
    for (var i = 0; i < lines.length; i++) {
      if (!_paidPickup.hasMatch(lines[i])) continue;
      used.add(i);
      for (var j = i; j <= math.min(i + 2, lines.length - 1); j++) {
        final m = _plusAmount.firstMatch(lines[j]);
        if (m == null) continue;
        paid = math.max(paid, int.parse(m.group(1)!));
        used.add(j);
        break;
      }
    }
    for (var i = 0; i < lines.length; i++) {
      if (used.contains(i)) continue;
      for (final m in _plusAmount.allMatches(lines[i])) {
        final v = int.parse(m.group(1)!);
        final hasUnit = m.group(2) != null;
        if ((hasUnit && v >= 1 && v <= 500) || (!hasUnit && v >= 5 && v <= 150)) surge = math.max(surge, v);
      }
    }

    // Подача — с неё начинается нижняя карточка; всё выше — карта с подписями улиц.
    var start = 0;
    var pickupKm = 0.0;
    for (var i = 0; i < lines.length; i++) {
      final m = _pickup.firstMatch(lines[i]);
      if (m == null) continue;
      final v = double.tryParse(m.group(1)!.replaceAll(',', '.')) ?? 0;
      final unit = m.group(2)!.toLowerCase();
      pickupKm = (unit == 'км' || unit == 'km') ? v : v / 1000;
      start = i + 1;
      break;
    }
    var end = lines.length;
    for (var i = start; i < lines.length; i++) {
      if (_tailWords.any((w) => lower[i].startsWith(w))) {
        end = i;
        break;
      }
    }
    final card = lines.sublist(start, end);

    // Подписи маршрута Яндекса на мини-карте: «17 km», «50 min» (только длинные поездки).
    double? cardKm;
    int? cardMin;
    for (final l in lines.take(start == 0 ? lines.length : start - 1)) {
      final k = _cardKm.firstMatch(l);
      if (k != null) cardKm ??= double.tryParse(k.group(1)!.replaceAll(',', '.'));
      final m = _cardMin.firstMatch(l);
      if (m != null) cardMin ??= (int.tryParse(m.group(1) ?? '') ?? 0) * 60 + int.parse(m.group(3)!);
    }
    if (cardKm == null || cardMin == null || cardKm <= 0 || cardMin <= 0) {
      cardKm = null;
      cardMin = null;
    }

    // Адреса по порядку: первый — А, последний — Б, между ними — заезды.
    String clean(String s) => s.replaceFirst(_markerPrefix, '').replaceAll(RegExp(r'[,\s]*\+\d+\s*$'), '').trim();
    final addresses = <String>[];
    for (final l in card) {
      if (_markerOnly.hasMatch(l)) continue;
      var c = clean(l);
      if (c.length > 60) c = c.substring(0, 60);
      if (_looksLikeAddress(c) && !addresses.contains(c)) addresses.add(c);
    }

    final pointA = addresses.isNotEmpty ? addresses.first : '';
    final pointB = addresses.length > 1 ? addresses.last : '';
    final a = pointA.toLowerCase(), b = pointB.toLowerCase();
    final stops = addresses.length > 2
        ? addresses.sublist(1, addresses.length - 1).where((s) {
            final l = s.toLowerCase();
            return !(l.contains(a) || a.contains(l) || l.contains(b) || b.contains(l));
          }).toList()
        : <String>[];

    return ParsedOrder(
      price: 0,
      pointA: pointA,
      pointB: pointB,
      stops: stops,
      tariff: tariff,
      rawText: rawText,
      surgeBonus: surge + paid,
      pickupKm: pickupKm,
      cardKm: cardKm,
      cardMin: cardMin,
      calculating: pointA.isNotEmpty && pointB.isNotEmpty,
    );
  }

  static String _fmtKm(double km) => km < 10 ? km.toStringAsFixed(1) : km.round().toString();

  /// Цена по маршруту (как в Android). Не нашли адреса или маршрут — цена 0,
  /// на острове «нет цены», а не выдуманная сумма.
  static Future<ParsedOrder> enrich(ParsedOrder order) async {
    if (!order.hasRoute) return order.copyWith(calculating: false);
    var r = await FareCalculator.calculate(
      [order.pointA, ...order.stops, order.pointB],
      tariff: order.tariff,
      surgeBonus: order.surgeBonus,
    );
    if (r == null) return order.copyWith(price: 0, calculating: false);
    if (order.cardKm != null && order.cardMin != null) {
      r = FareCalculator.withYandexRoute(r, order.cardKm!, order.cardMin!, order.tariff, order.surgeBonus) ?? r;
    } else if (r.durationMin > FareCalculator.maxUnlabeledMin) {
      // Подписей нет — по Яндексу поездка короче 40 минут; наша оценка больше — обрезаем.
      r = FareResult(
        price: FareCalculator.price(
            order.tariff, r.cityKm, r.outOfCityKm, FareCalculator.maxUnlabeledMin.toDouble(), order.surgeBonus),
        distanceKm: r.distanceKm,
        durationMin: FareCalculator.maxUnlabeledMin,
        osrmMin: r.osrmMin,
        cityKm: r.cityKm,
        outOfCityKm: r.outOfCityKm,
        stops: r.stops,
      );
    }
    final parts = ['${_fmtKm(r.distanceKm)} км', '${r.durationMin} мин', if (r.stops > 0) '${r.stops} заезд'];
    return order.copyWith(
        price: r.price.toDouble(), distanceTime: parts.join(' · '), calculating: false, stopsCount: r.stops);
  }
}
