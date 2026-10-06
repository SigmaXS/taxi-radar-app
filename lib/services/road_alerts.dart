import 'dart:async';

import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/app_strings.dart';
import '../models/report.dart';
import 'community_service.dart';
import 'radar_alerts.dart';

/// Предупреждения в дороге (мини-Waze), как на Android: едем К метке —
/// уведомление за 400 м и за 100 м; удаляемся или стоим — молчим.
/// Работает, пока включён радар (он держит геолокацию в фоне).
class RoadAlerts {
  static const _key = 'road_alerts';

  static Future<bool> enabled() async => (await SharedPreferences.getInstance()).getBool(_key) ?? false;

  static Future<void> setEnabled(bool on) async => (await SharedPreferences.getInstance()).setBool(_key, on);

  static List<ReportItem> _reports = [];
  static DateTime _loadedAt = DateTime.fromMillisecondsSinceEpoch(0);
  static final Map<int, int> _lastDistance = {};
  static final Map<int, int> _stage = {};

  /// Новая точка GPS (из фоновой геолокации радара).
  static Future<void> onPosition(Position p) async {
    if (!await enabled()) return;
    // Метки вокруг — раз в полторы минуты.
    if (DateTime.now().difference(_loadedAt).inSeconds > 90) {
      _loadedAt = DateTime.now();
      final list = await CommunityService.getReports(p.latitude, p.longitude);
      _reports = list.where((r) => (RoadReports.byKey(r.type)?.road ?? false) && !r.mine).toList();
    }
    for (final r in _reports) {
      final meters = Geolocator.distanceBetween(p.latitude, p.longitude, r.lat, r.lon).round();
      final prev = _lastDistance[r.id];
      _lastDistance[r.id] = meters;
      if (meters > 700) {
        _stage.remove(r.id);
        continue;
      }
      final approaching = prev != null && meters < prev - 15;
      final stage = _stage[r.id] ?? 0;
      final next = approaching && meters <= 150 && stage < 2
          ? 2
          : approaching && meters <= 450 && stage < 1
              ? 1
              : 0;
      if (next == 0) continue;
      _stage[r.id] = next;
      final type = RoadReports.byKey(r.type);
      if (type == null) continue;
      final rounded = (meters ~/ 50 * 50).clamp(50, 1000);
      await RadarAlerts.notify(
        'road_${r.id}',
        AppStrings.t('${type.label} через $rounded м', '${type.label} peste $rounded m'),
        AppStrings.t('Отметили водители Taxi Radar', 'Marcat de șoferii Taxi Radar'),
      );
    }
  }
}
