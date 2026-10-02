import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/ride_item.dart';

class RidesSearchResult {
  final int total;
  final String route;
  final List<RideItem> items;

  RidesSearchResult({required this.total, required this.route, required this.items});
}

class RidesService {
  static const String apiBase = 'https://transfer-production-2342.up.railway.app/api';
  static const String apiKey = 'taxiradar-app';

  static Future<List<String>> fetchPlaces() async {
    try {
      final res = await http
          .get(Uri.parse('$apiBase/places?key=$apiKey'))
          .timeout(const Duration(seconds: 12));
      if (res.statusCode == 200) {
        final data = jsonDecode(utf8.decode(res.bodyBytes));
        final all = data['all'] as List?;
        if (all != null) {
          return all.map((e) => e.toString()).toList();
        }
      }
      return [];
    } catch (_) {
      return [];
    }
  }

  static Future<RidesSearchResult?> searchRides({
    required String kind, // 'passenger' or 'driver'
    required String from,
    required String to,
    int offset = 0,
    int limit = 20,
  }) async {
    try {
      final encFrom = Uri.encodeComponent(from);
      final encTo = Uri.encodeComponent(to);
      final url =
          '$apiBase/rides?key=$apiKey&kind=$kind&offset=$offset&limit=$limit&from=$encFrom&to=$encTo&both=1';

      final res = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 15));
      if (res.statusCode == 200) {
        final data = jsonDecode(utf8.decode(res.bodyBytes));
        final total = (data['total'] as num?)?.toInt() ?? 0;
        final route = data['route']?.toString() ?? '';
        final itemsList = <RideItem>[];
        if (data['items'] is List) {
          for (var item in data['items']) {
            if (item is Map<String, dynamic>) {
              itemsList.add(RideItem.fromJson(item));
            }
          }
        }
        return RidesSearchResult(total: total, route: route, items: itemsList);
      }
      return null;
    } catch (_) {
      return null;
    }
  }
}
