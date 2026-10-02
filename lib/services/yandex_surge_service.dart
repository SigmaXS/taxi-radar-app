import 'dart:convert';
import 'package:http/http.dart' as http;

class SurgeResult {
  final int econom;
  final int comfort;
  final int comfortPlus;

  const SurgeResult({
    this.econom = 0,
    this.comfort = 0,
    this.comfortPlus = 0,
  });
}

class YandexSurgeService {
  static Map<String, int> basePrices = {
    'econom': 30,
    'comfort': 45,
    'business': 45,
    'comfortplus': 65,
  };

  static void updateBases(Map<String, int> bases) {
    if (bases.isEmpty) return;
    bases.forEach((k, v) {
      if (v > 0) basePrices[k] = v;
    });
    if (bases['comfort'] != null && bases['comfort']! > 0) {
      basePrices['business'] = bases['comfort']!;
    }
  }

  static Future<SurgeResult?> getSurgeAll(double lon, double lat) async {
    try {
      final payload = {
        'route': [
          [lon, lat]
        ],
        'selected_class': '',
        'format_currency': true,
        'summary_version': 2,
        'is_lightweight': false,
        'supports_paid_options': true,
        'use_toll_roads': false,
        'tariff_requirements': [
          {'class': 'econom'},
          {'class': 'business'},
          {'class': 'comfortplus'}
        ]
      };

      final response = await http.post(
        Uri.parse('https://ya-authproxy.taxi.yandex.md/3.0/routestats'),
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Accept': '*/*',
          'Accept-Language': 'ro,ru;q=0.9',
          'Origin': 'https://taxi.yandex.md',
          'Referer': 'https://taxi.yandex.md/',
          'User-Agent':
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'X-Requested-With': 'XMLHttpRequest',
          'X-Request-Id': DateTime.now().millisecondsSinceEpoch.toString(),
        },
        body: jsonEncode(payload),
      ).timeout(const Duration(seconds: 7));

      if (response.statusCode != 200) return null;

      final data = jsonDecode(utf8.decode(response.bodyBytes));
      final levels = data['service_levels'] as List?;
      if (levels == null) return null;

      int econSurge = 0;
      int comfSurge = 0;
      int plusSurge = 0;

      for (var lvl in levels) {
        if (lvl is! Map) continue;
        final cls = (lvl['class'] as String? ?? '').toLowerCase();

        int surge = 0;
        if (lvl['surge'] is Map && lvl['surge']['value'] != null) {
          surge = (lvl['surge']['value'] as num).toInt();
        } else {
          final priceStr = lvl['price']?.toString() ?? '';
          final match = RegExp(r'\d+').firstMatch(priceStr);
          if (match != null) {
            final startPrice = int.tryParse(match.group(0)!) ?? 0;
            final base = basePrices[cls] ?? 30;
            surge = (startPrice - base).clamp(0, 500);
          }
        }

        if (cls == 'econom') econSurge = surge;
        if (cls == 'business' || cls == 'comfort') comfSurge = surge;
        if (cls == 'comfortplus') plusSurge = surge;
      }

      return SurgeResult(
        econom: econSurge,
        comfort: comfSurge,
        comfortPlus: plusSurge,
      );
    } catch (_) {
      return null;
    }
  }

  static Future<Map<String, dynamic>?> calculateRouteFare({
    required List<List<double>> coordinates, // [[lonA, latA], [lonB, latB]]
  }) async {
    try {
      final payload = {
        'route': coordinates,
        'selected_class': '',
        'format_currency': true,
        'summary_version': 2,
        'is_lightweight': false,
        'supports_paid_options': true,
        'use_toll_roads': false,
        'tariff_requirements': [
          {'class': 'econom'},
          {'class': 'business'},
          {'class': 'comfortplus'}
        ]
      };

      final response = await http.post(
        Uri.parse('https://ya-authproxy.taxi.yandex.md/3.0/routestats'),
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Accept': '*/*',
          'Accept-Language': 'ro,ru;q=0.9',
          'Origin': 'https://taxi.yandex.md',
          'Referer': 'https://taxi.yandex.md/',
          'User-Agent':
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'X-Requested-With': 'XMLHttpRequest',
          'X-Request-Id': DateTime.now().millisecondsSinceEpoch.toString(),
        },
        body: jsonEncode(payload),
      ).timeout(const Duration(seconds: 8));

      if (response.statusCode != 200) return null;
      return jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>?;
    } catch (_) {
      return null;
    }
  }
}
