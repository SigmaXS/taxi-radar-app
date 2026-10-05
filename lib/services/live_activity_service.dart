import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'community_service.dart';
import 'order_parser_service.dart';
import 'yandex_surge_service.dart';

class LiveActivityResult {
  final bool success;
  final String? errorMessage;
  LiveActivityResult({required this.success, this.errorMessage});
}

class LiveActivityService {
  static const MethodChannel _channel = MethodChannel('taxiradar/live_activity');

  static Timer? _monitorTimer;
  static bool _isMonitoring = false;
  static final ValueNotifier<ParsedOrder?> latestOrderNotifier = ValueNotifier<ParsedOrder?>(null);
  static final ValueNotifier<String> currentSurgeNotifier = ValueNotifier<String>('+0');
  static DateTime? _latestOrderTime;

  static bool get isMonitoring => _isMonitoring;

  static Future<void> init() async {
    if (!Platform.isIOS) return;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onUrl') {
        final url = call.arguments as String?;
        if (url != null) handleIncomingUrl(url);
      }
    });
  }

  /// Обработка входящего URL (taxiradar://order?text=...)
  static void handleIncomingUrl(String url) {
    try {
      final uri = Uri.parse(url);
      String text = uri.queryParameters['text'] ?? '';
      if (text.isEmpty && uri.queryParameters['data'] != null) {
        text = uri.queryParameters['data']!;
      }
      if (text.isEmpty && uri.path.isNotEmpty) {
        text = Uri.decodeComponent(uri.path);
      }
      if (text.isNotEmpty) {
        final order = OrderParserService.parse(text);
        processScannedOrder(order);

        // Асинхронно обогащаем через геокодер Яндекс API на бэкенде Railway
        OrderParserService.enrich(order).then((enriched) {
          if (enriched.distanceTime != order.distanceTime || enriched.price != order.price) {
            processScannedOrder(enriched);
          }
        });
      }
    } catch (e) {
      if (kDebugMode) print('handleIncomingUrl error: $e');
    }
  }

  /// Обновление виджета на Dynamic Island при сканировании заказа
  static Future<void> processScannedOrder(ParsedOrder order) async {
    latestOrderNotifier.value = order;
    _latestOrderTime = DateTime.now();
    final nowTime = DateFormat('HH:mm').format(DateTime.now());

    final priceStr = '${order.price.round()} MDL';
    final distTimeStr = order.distanceTime.isNotEmpty ? order.distanceTime : '';

    final Map<String, dynamic> data = {
      'surge': distTimeStr.isNotEmpty ? distTimeStr : priceStr,
      'zone': order.pointA,
      'price': priceStr,
      'alert': order.pointB,
      'updatedAt': nowTime,
    };

    if (Platform.isIOS) {
      try {
        await _channel.invokeMethod('update', data);
      } catch (e) {
        if (kDebugMode) print('processScannedOrder native error: $e');
      }
    }
  }

  /// Сброс текущего заказа для возврата к режиму радара надбавки
  static void clearCurrentOrder() {
    latestOrderNotifier.value = null;
    _latestOrderTime = null;
    _refreshSurgeAndPushActivity(isStart: false);
  }

  /// Проверка доступности Live Activities в iOS
  static Future<bool> areActivitiesEnabled() async {
    if (!Platform.isIOS) return false;
    try {
      final bool? enabled = await _channel.invokeMethod<bool>('areActivitiesEnabled');
      return enabled ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Запуск фонового мониторинга с отображением в Dynamic Island
  static Future<LiveActivityResult> startMonitoring({Function(String status)? onStatus}) async {
    if (!Platform.isIOS) {
      _isMonitoring = true;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('is_monitoring', true);
      return LiveActivityResult(success: true);
    }

    try {
      final enabled = await areActivitiesEnabled();
      if (!enabled) {
        return LiveActivityResult(
          success: false,
          errorMessage: 'Эфир активности выключен. Включите: Настройки -> Taxi Radar -> Эфир активности.',
        );
      }
    } catch (e) {
      if (kDebugMode) print('Check enabled error: $e');
    }

    _isMonitoring = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('is_monitoring', true);

    // Первичное обновление и старт Dynamic Island
    final startRes = await _refreshSurgeAndPushActivity(isStart: true);
    if (!startRes.success) {
      return startRes;
    }

    // Запуск периодического обновления каждые 25 секунд
    _monitorTimer?.cancel();
    _monitorTimer = Timer.periodic(const Duration(seconds: 25), (timer) async {
      if (!_isMonitoring) {
        timer.cancel();
        return;
      }
      await _refreshSurgeAndPushActivity(isStart: false);
    });

    return LiveActivityResult(success: true);
  }

  /// Остановка мониторинга
  static Future<void> stopMonitoring() async {
    _isMonitoring = false;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('is_monitoring', false);

    _monitorTimer?.cancel();
    _monitorTimer = null;

    if (Platform.isIOS) {
      try {
        await _channel.invokeMethod('stop');
      } catch (e) {
        if (kDebugMode) print('Error stopping activity: $e');
      }
    }
  }

  /// Определение района Кишинёва по координатам
  static String getSectorName(double lat, double lon) {
    if (lat < 46.96 && lon > 28.90) return 'Аэропорт';
    if (lat < 47.005 && lon > 28.835) return 'Ботаника';
    if (lat < 47.015 && lon < 28.83) return 'Телецентр';
    if (lat >= 47.015 && lat <= 47.035 && lon >= 28.815 && lon <= 28.865) return 'Центр';
    if (lat > 47.035 && lon < 28.825) return 'Буюканы';
    if (lat > 47.035 && lon >= 28.825 && lon <= 28.875) return 'Рышкановка';
    if (lat > 47.025 && lon > 28.875) return 'Чеканы';
    if (lat > 47.06) return 'Ставчены';
    if (lat < 47.01 && lon < 47.80) return 'Дурлешты';
    return 'Кишинёв';
  }

  /// Получение геопозиции, данных Яндекс и радаров, отправка в Dynamic Island
  static Future<LiveActivityResult> _refreshSurgeAndPushActivity({bool isStart = false}) async {
    double lat = 47.0245;
    double lon = 28.8353;

    try {
      LocationPermission perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied) {
        perm = await Geolocator.requestPermission();
      }
      if (perm == LocationPermission.whileInUse || perm == LocationPermission.always) {
        final pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.medium,
            timeLimit: Duration(seconds: 5),
          ),
        );
        lat = pos.latitude;
        lon = pos.longitude;
      }
    } catch (_) {}

    // Если недавно был распознан заказ (в течение 5 минут), сохраняем его на экране
    if (_latestOrderTime != null &&
        DateTime.now().difference(_latestOrderTime!).inMinutes < 5 &&
        latestOrderNotifier.value != null) {
      return LiveActivityResult(success: true);
    }

    final surge = await YandexSurgeService.getSurgeAll(lon, lat);

    // Определяем текущую единую надбавку (+15, +35, +55 или +0)
    int maxSurge = 0;
    if (surge != null) {
      maxSurge = [surge.econom, surge.comfort, surge.comfortPlus].reduce((a, b) => a > b ? a : b);
    }
    final surgeDisplay = maxSurge > 0 ? '+$maxSurge' : '+0';
    currentSurgeNotifier.value = surgeDisplay;

    // Проверка ближайших предупреждений о радарах/полиции
    String nearestAlert = '';
    try {
      final reports = await CommunityService.getReports(lat, lon);
      if (reports.isNotEmpty) {
        for (var r in reports) {
          final dist = Geolocator.distanceBetween(lat, lon, r.lat, r.lon);
          if (dist <= 1500) {
            final distStr = '${dist.round()}м';
            if (r.type == 'radar') {
              nearestAlert = '📸 Радар ($distStr)';
              break;
            } else if (r.type == 'police') {
              nearestAlert = '🚓 Полиция ($distStr)';
              break;
            } else if (r.type == 'accident') {
              nearestAlert = '💥 ДТП ($distStr)';
              break;
            }
          }
        }
      }
    } catch (_) {}

    final nowTime = DateFormat('HH:mm').format(DateTime.now());

    // В режиме ожидания (нет заказа): передаем только надбавку и дорожные радары
    final Map<String, dynamic> data = {
      'surge': surgeDisplay,
      'zone': '',
      'price': '',
      'alert': nearestAlert,
      'updatedAt': nowTime,
    };

    if (Platform.isIOS) {
      try {
        if (isStart) {
          await _channel.invokeMethod('start', data);
        } else {
          await _channel.invokeMethod('update', data);
        }
        return LiveActivityResult(success: true);
      } on PlatformException catch (pe) {
        if (kDebugMode) print('LiveActivity PlatformException: ${pe.code} - ${pe.message}');
        return LiveActivityResult(
          success: false,
          errorMessage: 'Ошибка Dynamic Island: ${pe.message ?? pe.code}',
        );
      } catch (e) {
        if (kDebugMode) print('LiveActivity error: $e');
        return LiveActivityResult(
          success: false,
          errorMessage: 'Ошибка: $e',
        );
      }
    }

    return LiveActivityResult(success: true);
  }
}
