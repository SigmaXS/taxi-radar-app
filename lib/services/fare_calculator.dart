import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'api_service.dart';
import 'traffic_model.dart';

/// Результат расчёта цены поездки.
class FareResult {
  final int price;
  final double distanceKm;

  /// Минуты с поправкой на пробки — по ним считалась цена.
  final int durationMin;

  /// Минуты по пустым дорогам (OSRM).
  final double osrmMin;
  final double cityKm;
  final double outOfCityKm;

  /// Сколько заездов реально вошло в маршрут (сомнительные отброшены).
  final int stops;

  const FareResult({
    required this.price,
    required this.distanceKm,
    required this.durationMin,
    required this.osrmMin,
    required this.cityKm,
    required this.outOfCityKm,
    required this.stops,
  });
}

class _Tariff {
  final double baseFare, perKmCity, perKmOutOfCity, perMinute;
  const _Tariff(this.baseFare, this.perKmCity, this.perKmOutOfCity, this.perMinute);
}

class _Point {
  final double lat, lon;
  const _Point(this.lat, this.lon);
}

/// Цена поездки по официальной сетке Яндекса в Кишинёве — тот же расчёт,
/// что в Android-версии (RouteFareCalculator.kt): маршрут OSRM, делённый на
/// городскую и загородную части по полигону зоны, + минуты с пробками.
class FareCalculator {
  static const _osrm = 'https://router.project-osrm.org';

  static const _tariffs = {
    'эконом': _Tariff(30, 3.5, 5.3, 1.0),
    'комфорт': _Tariff(45, 3.5, 7.3, 1.0),
    'комфорт+': _Tariff(65, 3.5, 7.3, 1.0),
  };

  /// Первые 2 км входят в минимальную стоимость.
  static const _freeKm = 2.0;

  /// Граница городской тарифной зоны (lon, lat) — как в Android.
  static const _cityZone = [
    [28.83774, 47.08549],
    [28.80156, 47.07999],
    [28.76378, 47.07457],
    [28.76370, 47.05835],
    [28.73516, 47.03933],
    [28.74046, 47.01677],
    [28.76997, 46.99111],
    [28.80372, 46.97135],
    [28.89244, 46.96319],
    [28.93342, 46.93772],
    [28.93020, 47.04983],
    [28.92294, 47.04881],
    [28.84655, 47.06034],
  ];

  /// Подписей маршрута на карточке нет — по расчёту Яндекса поездка короче 40 мин.
  static const maxUnlabeledMin = 40;

  static bool isInsideCity(double lat, double lon) {
    var inside = false;
    var j = _cityZone.length - 1;
    for (var i = 0; i < _cityZone.length; i++) {
      final xi = _cityZone[i][0], yi = _cityZone[i][1];
      final xj = _cityZone[j][0], yj = _cityZone[j][1];
      if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
        inside = !inside;
      }
      j = i;
    }
    return inside;
  }

  static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    const r = 6371.0;
    double rad(double d) => d * math.pi / 180;
    final dLat = rad(lat2 - lat1), dLon = rad(lon2 - lon1);
    final a = math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.cos(rad(lat1)) * math.cos(rad(lat2)) * math.sin(dLon / 2) * math.sin(dLon / 2);
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a));
  }

  /// Цена по тарифу: минималка (с первыми 2 км) + км город/загород + минуты + надбавка.
  static int price(String tariffName, double cityKm, double outOfCityKm, double minutes, int surgeBonus) {
    final t = _tariffs[tariffName.toLowerCase()] ?? _tariffs['эконом']!;
    final freeInCity = math.min(_freeKm, cityKm);
    final billableCity = cityKm - freeInCity;
    final billableOut = math.max(0.0, outOfCityKm - (_freeKm - freeInCity));
    final raw = t.baseFare + t.perKmCity * billableCity + t.perKmOutOfCity * billableOut + t.perMinute * minutes;
    return raw.round() + surgeBonus;
  }

  /// Час пик (будни 7–9 и 13–18) — +10 минут, если пробки ещё не выучены по водителям.
  static int rushHourExtraMin(DateTime now) {
    if (now.weekday == DateTime.saturday || now.weekday == DateTime.sunday) return 0;
    final h = now.hour;
    return ((h >= 7 && h < 10) || (h >= 13 && h < 19)) ? 10 : 0;
  }

  /// Считает цену по адресам [А, заезды…, Б]. null — адреса не нашлись или нет маршрута:
  /// лучше без цены, чем с выдуманной.
  static Future<FareResult?> calculate(
    List<String> addresses, {
    required String tariff,
    int surgeBonus = 0,
  }) async {
    if (addresses.length < 2) return null;
    try {
      final found = await Future.wait(addresses.map((a) => ApiService.geocodeAddress(a)
          .timeout(const Duration(seconds: 8), onTimeout: () => null)));
      final from = found.first, to = found.last;
      if (from == null || to == null) return null;
      final a = _Point(from['lat']!, from['lon']!);
      final b = _Point(to['lat']!, to['lon']!);
      // Заезд, который не нашёлся или стоит почти у А/Б (дубль адреса, подъезд), — не заезд.
      final stops = found.sublist(1, found.length - 1).whereType<Map<String, double>>().map((p) => _Point(p['lat']!, p['lon']!)).where((s) =>
          haversineKm(s.lat, s.lon, a.lat, a.lon) >= 0.3 && haversineKm(s.lat, s.lon, b.lat, b.lon) >= 0.3).toList();

      final route = await _route([a, ...stops, b]);
      if (route == null) return null;

      final traffic = TrafficModel.factor();
      var minutes = route.durationMin * traffic.value;
      if (!traffic.fromDrivers) {
        minutes += math.min(rushHourExtraMin(DateTime.now()).toDouble(), minutes);
      }
      final km = route.cityKm + route.outOfCityKm;
      return FareResult(
        price: price(tariff, route.cityKm, route.outOfCityKm, minutes, surgeBonus),
        distanceKm: (km * 10).round() / 10,
        durationMin: minutes.round(),
        osrmMin: route.durationMin,
        cityKm: route.cityKm,
        outOfCityKm: route.outOfCityKm,
        stops: stops.length,
      );
    } catch (e) {
      if (kDebugMode) print('FareCalculator: $e');
      return null;
    }
  }

  /// Цена по км и минутам самого Яндекса (подписи маршрута на карточке).
  /// Наш маршрут нужен только для доли загородного пути.
  static FareResult? withYandexRoute(FareResult ours, double yandexKm, int yandexMin, String tariff, int surgeBonus) {
    final ourKm = ours.cityKm + ours.outOfCityKm;
    if (ourKm <= 0 || yandexKm < ourKm * 0.6 || yandexKm > ourKm * 1.8) return null;
    final scale = yandexKm / ourKm;
    return FareResult(
      price: price(tariff, ours.cityKm * scale, ours.outOfCityKm * scale, yandexMin.toDouble(), surgeBonus),
      distanceKm: yandexKm,
      durationMin: yandexMin,
      osrmMin: ours.osrmMin,
      cityKm: ours.cityKm * scale,
      outOfCityKm: ours.outOfCityKm * scale,
      stops: ours.stops,
    );
  }

  static Future<({double cityKm, double outOfCityKm, double durationMin})?> _route(List<_Point> points) async {
    final url = '$_osrm/route/v1/driving/${points.map((p) => '${p.lon},${p.lat}').join(';')}'
        '?overview=full&geometries=geojson';
    final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 8));
    if (res.statusCode != 200) return null;
    final json = jsonDecode(res.body) as Map<String, dynamic>;
    if (json['code'] != 'Ok') return null;
    final route = (json['routes'] as List).first as Map<String, dynamic>;
    final durationMin = (route['duration'] as num).toDouble() / 60;
    final coords = (route['geometry']['coordinates'] as List).cast<List>();
    var city = 0.0, out = 0.0;
    for (var i = 1; i < coords.length; i++) {
      final lon1 = (coords[i - 1][0] as num).toDouble(), lat1 = (coords[i - 1][1] as num).toDouble();
      final lon2 = (coords[i][0] as num).toDouble(), lat2 = (coords[i][1] as num).toDouble();
      final seg = haversineKm(lat1, lon1, lat2, lon2);
      if (isInsideCity((lat1 + lat2) / 2, (lon1 + lon2) / 2)) {
        city += seg;
      } else {
        out += seg;
      }
    }
    if (coords.length < 2) {
      final km = (route['distance'] as num).toDouble() / 1000;
      final p = points.first, q = points.last;
      if (isInsideCity((p.lat + q.lat) / 2, (p.lon + q.lon) / 2)) {
        city = km;
      } else {
        out = km;
      }
    }
    return (cityKm: city, outOfCityKm: out, durationMin: durationMin);
  }
}
