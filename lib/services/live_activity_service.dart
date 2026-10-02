import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';
import 'package:intl/intl.dart';
import 'package:live_activities/live_activities.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'community_service.dart';
import 'yandex_surge_service.dart';

class LiveActivityService {
  static const String appGroupId = 'group.com.example.taxiradar.taxiRadarApp';

  static final LiveActivities _liveActivities = LiveActivities();
  static String? _currentActivityId;
  static Timer? _monitorTimer;
  static bool _isMonitoring = false;

  static bool get isMonitoring => _isMonitoring;

  static Future<void> init() async {
    if (!Platform.isIOS) return;
    try {
      await _liveActivities.init(appGroupId: appGroupId);
    } catch (e) {
      if (kDebugMode) print('LiveActivities init error: $e');
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

  /// Запуск фонового мониторинга с отображением в Dynamic Island
  static Future<bool> startMonitoring({Function(String status)? onStatus}) async {
    if (!Platform.isIOS) {
      _isMonitoring = true;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('is_monitoring', true);
      return true;
    }

    try {
      final supported = await _liveActivities.areActivitiesEnabled();
      if (!supported) {
        if (kDebugMode) print('Live activities are disabled in iOS Settings');
      }
    } catch (_) {}

    _isMonitoring = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('is_monitoring', true);

    // Первичное обновление данных
    await _refreshSurgeAndPushActivity();

    // Запуск периодического обновления каждые 25 секунд
    _monitorTimer?.cancel();
    _monitorTimer = Timer.periodic(const Duration(seconds: 25), (timer) async {
      if (!_isMonitoring) {
        timer.cancel();
        return;
      }
      await _refreshSurgeAndPushActivity();
    });

    return true;
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
        if (_currentActivityId != null) {
          await _liveActivities.endActivity(_currentActivityId!);
          _currentActivityId = null;
        }
        await _liveActivities.endAllActivities();
      } catch (e) {
        if (kDebugMode) print('Error ending activity: $e');
      }
    }
  }

  /// Получение геопозиции, данных Яндекс и радаров, отправка в Dynamic Island
  static Future<void> _refreshSurgeAndPushActivity() async {
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

    final sector = getSectorName(lat, lon);
    final surge = await YandexSurgeService.getSurgeAll(lon, lat);

    int econSurge = surge?.econom ?? 0;
    int comfSurge = surge?.comfort ?? 0;
    int plusSurge = surge?.comfortPlus ?? 0;

    final baseEcon = YandexSurgeService.basePrices['econom'] ?? 30;
    final baseComf = YandexSurgeService.basePrices['comfort'] ?? 45;
    final basePlus = YandexSurgeService.basePrices['comfortplus'] ?? 65;

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

    final surgeDisplay = econSurge > 0 ? '+$econSurge L' : '+0 L';
    final nowTime = DateFormat('HH:mm').format(DateTime.now());

    final Map<String, dynamic> activityData = {
      'surge': surgeDisplay,
      'zone': sector,
      'econom': '${baseEcon + econSurge} L',
      'comfort': '${baseComf + comfSurge} L',
      'comfortPlus': '${basePlus + plusSurge} L',
      'alert': nearestAlert,
      'updatedAt': nowTime,
      'hasSurge': econSurge > 0 || comfSurge > 0,
    };

    if (Platform.isIOS) {
      try {
        if (_currentActivityId == null) {
          _currentActivityId = await _liveActivities.createActivity('taxiradar_surge', activityData);
        } else {
          await _liveActivities.updateActivity(_currentActivityId!, activityData);
        }
      } catch (e) {
        if (kDebugMode) print('LiveActivity update error: $e');
        // Если активность устарела или была закрыта системой, пересоздаем
        try {
          _currentActivityId = await _liveActivities.createActivity('taxiradar_surge', activityData);
        } catch (_) {}
      }
    }
  }
}
