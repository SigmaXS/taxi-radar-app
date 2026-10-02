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
      return null;
    } catch (e) {
      if (kDebugMode) {
        print('API POST ERROR $path: $e');
      }
      return null;
    }
  }
}
