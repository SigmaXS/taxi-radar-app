import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:geolocator/geolocator.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'community_service.dart';
import 'order_parser_service.dart';
import 'radar_alerts.dart';
import 'road_alerts.dart';
import 'yandex_surge_service.dart';

class LiveActivityResult {
  final bool success;
  final String? errorMessage;
  LiveActivityResult({required this.success, this.errorMessage});
}

class LiveActivityService {
  static const MethodChannel _channel = MethodChannel('taxiradar/live_activity');

  static const Duration _orderTtl = Duration(seconds: 15);

  static Timer? _monitorTimer;
  static Timer? _orderTtlTimer;
  static AppLifecycleListener? _lifecycleListener;
  static bool _isMonitoring = false;

  /// Остров запустился. На iOS 26.6.1 при установке через AltStore/Sideloadly он не
  /// работает (ошибка подписи расширений) — тогда радар живёт на уведомлениях.
  static bool _liveActivityOk = false;

  /// Фоновая геолокация: пока она идёт, iOS не усыпляет приложение и надбавка
  /// обновляется раз в минуту даже в свёрнутом виде.
  static StreamSubscription<Position>? _positionSub;
  static Position? _lastPosition;

  /// Возврат из фона должен сразу обновлять остров, иначе водитель смотрит
  /// на устаревшую надбавку до конца интервала опроса.
  static void _ensureLifecycleListener() {
    _lifecycleListener ??= AppLifecycleListener(
      onResume: () {
        if (_isMonitoring && Platform.isIOS) {
          _refreshSurgeAndPushActivity(isStart: false);
        }
      },
    );
  }

  static final ValueNotifier<ParsedOrder?> latestOrderNotifier = ValueNotifier<ParsedOrder?>(null);
  static final ValueNotifier<String> currentSurgeNotifier = ValueNotifier<String>('+0');

  /// Когда надбавку последний раз получили с сервера — «обновлено 14:32» на главном экране.
  static final ValueNotifier<DateTime?> surgeUpdatedNotifier = ValueNotifier<DateTime?>(null);

  /// Последний результат обмена с нативной частью — виден в интерфейсе,
  /// потому что в установленном IPA логи консоли недоступны.
  static final ValueNotifier<String> diagnosticsNotifier = ValueNotifier<String>('ожидание…');

  static void _diag(String message) {
    diagnosticsNotifier.value = message;
    if (kDebugMode) print('[LiveActivity] $message');
  }

  static String _radarAlert = '';
  static double? _radarOriginLat;
  static double? _radarOriginLon;

  /// Точка, выбранная водителем на карте: надбавка и радар считаются от неё.
  static void setRadarOrigin(double lat, double lon) {
    _radarOriginLat = lat;
    _radarOriginLon = lon;
  }

  static void clearRadarOrigin() {
    _radarOriginLat = null;
    _radarOriginLon = null;
  }

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
        // Доставку не считаем — как в Android.
        if (OrderParserService.isDelivery(text.split('\n').map((l) => l.trim()).toList())) return;
        final order = OrderParserService.parse(text);
        // Сразу — адреса и «считаю…», через секунду-две — цена по маршруту.
        processScannedOrder(order);
        OrderParserService.enrich(order).then((enriched) {
          // Пока считали, водитель отсканировал другой заказ — этот уже не нужен.
          if (identical(latestOrderNotifier.value, order)) processScannedOrder(enriched);
        });
      }
    } catch (e) {
      if (kDebugMode) print('handleIncomingUrl error: $e');
    }
  }

  /// Чистая цена поездки вместе с километрами и минутами: «85 MDL (5.2 км · 12 мин)»
  static String _buildPriceLabel(ParsedOrder order) {
    final fare = order.priceText;
    final distanceTime = order.distanceTime.trim();
    return distanceTime.isEmpty ? fare : '$fare ($distanceTime)';
  }

  static const Set<String> _pointPlaceholders = {'точка подачи', 'точка назначения'};

  /// Адрес точки маршрута; служебные заглушки в виджет не передаём.
  static String _routePoint(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return '';
    return _pointPlaceholders.contains(trimmed.toLowerCase()) ? '' : trimmed;
  }

  /// Обновление виджета на Dynamic Island при сканировании заказа
  static Future<void> processScannedOrder(ParsedOrder order) async {
    latestOrderNotifier.value = order;
    RadarAlerts.onOrder(order);

    final nowTime = DateFormat('HH:mm').format(DateTime.now());

    final data = <String, dynamic>{
      'surge': currentSurgeNotifier.value.isEmpty ? '+0' : currentSurgeNotifier.value,
      'price': _buildPriceLabel(order),
      'pointA': _routePoint(order.pointA),
      'pointB': _routePoint(order.pointB),
      'alert': _radarAlert,
      'hasOrder': true,
      'updatedAt': nowTime,
    };

    if (Platform.isIOS) {
      try {
        await _channel.invokeMethod('update', data);
      } catch (e) {
        if (kDebugMode) print('processScannedOrder native error: $e');
      }
    }

    _startOrderTtl();
  }

  /// Цена и адреса заказа живут 15 секунд, затем остаётся одна надбавка
  static void _startOrderTtl() {
    _orderTtlTimer?.cancel();
    _orderTtlTimer = Timer(_orderTtl, _expireOrder);
  }

  static void _expireOrder() {
    _orderTtlTimer = null;
    if (latestOrderNotifier.value == null) return;
    latestOrderNotifier.value = null;
    _refreshSurgeAndPushActivity(isStart: false);
  }

  /// Сброс текущего заказа для возврата к режиму радара надбавки
  static void clearCurrentOrder() {
    _orderTtlTimer?.cancel();
    _orderTtlTimer = null;
    latestOrderNotifier.value = null;
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

  /// Состояние нативной части: зарегистрирован ли MethodChannel, вызван ли
  /// FlutterImplicitEngineDelegate, разрешены ли Live Activities и сколько
  /// активностей сейчас живо. Это однозначно отделяет проблему канала от
  /// проблемы рендеринга виджета.
  static Future<Map<String, dynamic>> nativeInfo() async {
    if (!Platform.isIOS) return <String, dynamic>{};
    try {
      final Map<Object?, Object?>? info = await _channel.invokeMethod<Map<Object?, Object?>>('nativeInfo');
      return info?.map((key, value) => MapEntry(key.toString(), value)) ?? <String, dynamic>{};
    } catch (e) {
      _diag('nativeInfo недоступен: $e');
      return <String, dynamic>{};
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

    var activitiesEnabled = false;
    try {
      activitiesEnabled = await areActivitiesEnabled();
      final info = await nativeInfo();
      _diag(
        'Система: Live Activities $activitiesEnabled | '
        'канал=${info['channelReady']} движок=${info['engineInitialized']} '
        'активностей=${info['activityCount']} iOS ${info['iosVersion']}',
      );
    } catch (e) {
      if (kDebugMode) print('Check enabled error: $e');
    }

    _isMonitoring = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('is_monitoring', true);
    _ensureLifecycleListener();
    await RadarAlerts.requestPermission();
    _startBackgroundLocation();

    // Остров — по возможности; не запустился — радар всё равно работает на уведомлениях.
    _liveActivityOk = activitiesEnabled;
    final startRes = await _refreshSurgeAndPushActivity(isStart: true);
    if (!startRes.success) {
      _liveActivityOk = false;
      _diag('Остров не запустился (${startRes.errorMessage}) — работаем на уведомлениях');
    }

    // Раз в минуту: надбавка, цифра на иконке, статус на экране блокировки.
    _monitorTimer?.cancel();
    _monitorTimer = Timer.periodic(const Duration(seconds: 60), (timer) async {
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
    _orderTtlTimer?.cancel();
    _orderTtlTimer = null;
    latestOrderNotifier.value = null;
    _lifecycleListener?.dispose();
    _lifecycleListener = null;
    await _positionSub?.cancel();
    _positionSub = null;
    await RadarAlerts.clear();

    if (Platform.isIOS) {
      try {
        await _channel.invokeMethod('stop');
      } catch (e) {
        if (kDebugMode) print('Error stopping activity: $e');
      }
    }
  }

  /// Геолокация в фоне (синяя стрелка вверху экрана): держит радар живым в свёрнутом виде.
  static void _startBackgroundLocation() {
    if (_positionSub != null) return;
    final LocationSettings settings = Platform.isIOS
        ? AppleSettings(
            accuracy: LocationAccuracy.medium,
            distanceFilter: 30,
            activityType: ActivityType.automotiveNavigation,
            pauseLocationUpdatesAutomatically: false,
            allowBackgroundLocationUpdates: true,
            showBackgroundLocationIndicator: true,
          )
        : const LocationSettings(accuracy: LocationAccuracy.medium, distanceFilter: 100);
    try {
      _positionSub = Geolocator.getPositionStream(locationSettings: settings)
          .listen((p) {
        _lastPosition = p;
        RoadAlerts.onPosition(p);
      }, onError: (e) => _diag('Геолокация в фоне: $e'));
    } catch (e) {
      _diag('Геолокация в фоне не запустилась: $e');
    }
  }

  /// Получение геопозиции, данных Яндекс и радаров, отправка в Dynamic Island
  static Future<LiveActivityResult> _refreshSurgeAndPushActivity({bool isStart = false}) async {
    // Без своей точки надбавку не показываем: раньше бралась запасная точка на
    // Буюканах, и водитель видел чужой спрос (так же исправлено в Android).
    double? lat;
    double? lon;

    final recent = _lastPosition;
    if (recent != null && DateTime.now().difference(recent.timestamp).inMinutes < 3) {
      lat = recent.latitude;
      lon = recent.longitude;
    } else {
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
    }

    // Если водитель выбрал точку на карте, надбавка и радар считаются от неё
    final originLat = _radarOriginLat ?? lat;
    final originLon = _radarOriginLon ?? lon;
    if (originLat == null || originLon == null) {
      currentSurgeNotifier.value = '📍';
      _diag('Нет геолокации — надбавку не показываем');
      return LiveActivityResult(success: true);
    }

    final surge = await YandexSurgeService.getSurgeAll(originLon, originLat);

    // Надбавка по тарифу, выбранному в «Уведомлениях радара» (как один тариф в виджете Android).
    final settings = await RadarAlertSettings.load();
    int maxSurge = 0;
    if (surge != null) {
      maxSurge = switch (settings.tariff) {
        'comfort' => surge.comfort,
        'comfortplus' => surge.comfortPlus,
        _ => surge.econom,
      };
      await RadarAlerts.onSurge(maxSurge);
    }
    final surgeDisplay = maxSurge > 0 ? '+$maxSurge' : '+0';
    currentSurgeNotifier.value = surge == null ? '?' : surgeDisplay;
    if (surge != null) surgeUpdatedNotifier.value = DateTime.now();

    // Ближайшие предупреждения о радарах / полиции / ДТП
    String nearestAlert = '';
    try {
      final reports = await CommunityService.getReports(originLat, originLon);
      for (var r in reports) {
        final dist = Geolocator.distanceBetween(originLat, originLon, r.lat, r.lon);
        if (dist > 1500) continue;
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
    } catch (_) {}
    _radarAlert = nearestAlert;

    final nowTime = DateFormat('HH:mm').format(DateTime.now());

    // Надбавка и радар обновляются всегда, поля заказа — пока заказ активен
    final order = latestOrderNotifier.value;
    final Map<String, dynamic> data = {
      'surge': surgeDisplay,
      'price': order != null ? _buildPriceLabel(order) : '',
      'pointA': order != null ? _routePoint(order.pointA) : '',
      'pointB': order != null ? _routePoint(order.pointB) : '',
      'alert': nearestAlert,
      'hasOrder': order != null,
      'updatedAt': nowTime,
    };

    if (Platform.isIOS && _liveActivityOk) {
      try {
        if (isStart) {
          final id = await _channel.invokeMethod('start', data);
          _diag('start -> id=$id | ${data.entries.map((e) => '${e.key}=${e.value}').join(' ')}');
        } else {
          final ok = await _channel.invokeMethod('update', data);
          _diag('update -> $ok | надбавка ${data['surge']}, радар ${data['alert']}');
        }
        return LiveActivityResult(success: true);
      } on PlatformException catch (pe) {
        _diag('ОШИБКА ${pe.code}: ${pe.message}');
        if (kDebugMode) print('LiveActivity PlatformException: ${pe.code} - ${pe.message}');
        return LiveActivityResult(success: false, errorMessage: 'Ошибка Dynamic Island: ${pe.message ?? pe.code}');
      } catch (e) {
        _diag('ОШИБКА: $e');
        if (kDebugMode) print('LiveActivity error: $e');
        return LiveActivityResult(success: false, errorMessage: 'Ошибка: $e');
      }
    }

    return LiveActivityResult(success: true);
  }
}
