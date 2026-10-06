import 'dart:convert';
import 'dart:io';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class ApiService {
  static const String baseUrl = 'https://taxi-radar-license-production.up.railway.app';
  static final DeviceInfoPlugin _deviceInfoPlugin = DeviceInfoPlugin();

  static String? _cachedDeviceId;
  static String? _cachedDeviceInfo;

  static Future<String> getDeviceId() async {
    if (_cachedDeviceId != null) return _cachedDeviceId!;
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString('device_id');
    if (saved != null && saved.isNotEmpty) {
      _cachedDeviceId = saved;
      return saved;
    }

    String id = '';
    try {
      if (Platform.isIOS) {
        final ios = await _deviceInfoPlugin.iosInfo;
        id = ios.identifierForVendor ?? '';
      } else if (Platform.isAndroid) {
        final android = await _deviceInfoPlugin.androidInfo;
        id = android.id;
      }
    } catch (_) {}

    // Удаляем все лишние спецсимволы, оставляем только A-Za-z0-9_-
    id = id.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '');

    if (id.isEmpty) {
      final now = DateTime.now().millisecondsSinceEpoch;
      id = 'ios_$now';
    }

    if (id.length > 80) {
      id = id.substring(0, 80);
    }

    await prefs.setString('device_id', id);
    _cachedDeviceId = id;
    return id;
  }

  static Future<String> getDeviceInfo() async {
    if (_cachedDeviceInfo != null) return _cachedDeviceInfo!;
    try {
      if (Platform.isIOS) {
        final ios = await _deviceInfoPlugin.iosInfo;
        _cachedDeviceInfo = 'iPhone · iOS ${ios.systemVersion} · v1.16';
      } else if (Platform.isAndroid) {
        final android = await _deviceInfoPlugin.androidInfo;
        _cachedDeviceInfo = '${android.manufacturer} ${android.model} · Android ${android.version.release} · v1.16';
      } else {
        _cachedDeviceInfo = 'Mobile · v1.16';
      }
    } catch (_) {
      _cachedDeviceInfo = 'Mobile · v1.16';
    }
    return _cachedDeviceInfo!;
  }

  /// GET без тела — например, /api/app-config (на POST сервер отвечает 404).
  static Future<Map<String, dynamic>?> get(String path) async {
    try {
      final response = await http
          .get(Uri.parse('$baseUrl$path'), headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 15));
      if (response.statusCode < 200 || response.statusCode >= 300) return null;
      final body = jsonDecode(utf8.decode(response.bodyBytes));
      return body is Map<String, dynamic> ? body : null;
    } catch (e) {
      if (kDebugMode) print('API GET ERROR $path: $e');
      return null;
    }
  }

  static Future<Map<String, dynamic>?> post(String path, [Map<String, dynamic>? body]) async {
    try {
      final deviceId = await getDeviceId();
      final deviceInfo = await getDeviceInfo();
      final map = body != null ? Map<String, dynamic>.from(body) : <String, dynamic>{};
      map['device_id'] = deviceId;
      map['device_info'] = deviceInfo;

      final url = Uri.parse('$baseUrl$path');
      final response = await http.post(
        url,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Accept': 'application/json',
        },
        body: jsonEncode(map),
      ).timeout(const Duration(seconds: 15));

      if (kDebugMode) {
        print('API POST $path -> ${response.statusCode}: ${response.body}');
      }

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>?;
      }
      // 403 «Нужна активная подписка» и т. п.: отдаём ответ с ok:false и текстом
      // ошибки, чтобы экран мог его показать, а не молчать.
      try {
        final body = jsonDecode(utf8.decode(response.bodyBytes));
        if (body is Map<String, dynamic>) return body..putIfAbsent('ok', () => false);
      } catch (_) {}
      return null;
    } catch (e) {
      if (kDebugMode) {
        print('API POST ERROR $path: $e');
      }
      return null;
    }
  }

  /// Геокодирование адреса через Яндекс API на сервере Railway
  static Future<Map<String, double>?> geocodeAddress(String address) async {
    final query = address.trim();
    if (query.isEmpty) return null;
    try {
      final res = await post('/api/geocode', {'q': query});
      if (res != null && res['ok'] == true && res['found'] == true) {
        final lat = (res['lat'] as num?)?.toDouble();
        final lon = (res['lon'] as num?)?.toDouble();
        if (lat != null && lon != null) {
          return {'lat': lat, 'lon': lon};
        }
      }
    } catch (e) {
      if (kDebugMode) print('Geocode error for "$query": $e');
    }
    return null;
  }
}
