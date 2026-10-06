import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/app_strings.dart';
import 'order_parser_service.dart';

/// Настройки оповещений радара — водитель выбирает сам на экране «Уведомления радара».
class RadarAlertSettings {
  /// Тариф, по которому следим за надбавкой: econom / comfort / comfortplus.
  String tariff;

  /// Цифра надбавки на иконке приложения.
  bool badge;

  /// Тихое уведомление со статусом на экране блокировки (обновляется без звука).
  bool lockStatus;

  /// Баннер, когда надбавка выросла / упала / пропала.
  bool onUp, onDown, onGone;

  /// Сообщать о росте, только если надбавка не меньше этого (0 — любая).
  int minSurge;

  /// Звук у баннеров.
  bool sound;

  /// Баннер с ценой заказа после скриншота.
  bool orderBanner;

  RadarAlertSettings({
    this.tariff = 'econom',
    this.badge = true,
    this.lockStatus = true,
    this.onUp = true,
    this.onDown = false,
    this.onGone = false,
    this.minSurge = 15,
    this.sound = true,
    this.orderBanner = true,
  });

  static Future<RadarAlertSettings> load() async {
    final p = await SharedPreferences.getInstance();
    return RadarAlertSettings(
      tariff: p.getString('ra_tariff') ?? 'econom',
      badge: p.getBool('ra_badge') ?? true,
      lockStatus: p.getBool('ra_lock_status') ?? true,
      onUp: p.getBool('ra_on_up') ?? true,
      onDown: p.getBool('ra_on_down') ?? false,
      onGone: p.getBool('ra_on_gone') ?? false,
      minSurge: p.getInt('ra_min_surge') ?? 15,
      sound: p.getBool('ra_sound') ?? true,
      orderBanner: p.getBool('ra_order_banner') ?? true,
    );
  }

  Future<void> save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString('ra_tariff', tariff);
    await p.setBool('ra_badge', badge);
    await p.setBool('ra_lock_status', lockStatus);
    await p.setBool('ra_on_up', onUp);
    await p.setBool('ra_on_down', onDown);
    await p.setBool('ra_on_gone', onGone);
    await p.setInt('ra_min_surge', minSurge);
    await p.setBool('ra_sound', sound);
    await p.setBool('ra_order_banner', orderBanner);
  }

  static const tariffNames = {'econom': 'Эконом', 'comfort': 'Комфорт', 'comfortplus': 'Комфорт+'};
  String get tariffName => switch (tariff) {
        'comfort' => AppStrings.t('Комфорт', 'Confort'),
        'comfortplus' => AppStrings.t('Комфорт+', 'Confort+'),
        _ => AppStrings.t('Эконом', 'Econom'),
      };
}

/// «Эконом» / «Econom» — тариф заказа на языке приложения.
String orderTariffLabel(String ru) => switch (ru) {
      'Комфорт' => AppStrings.t('Комфорт', 'Confort'),
      'Комфорт+' => AppStrings.t('Комфорт+', 'Confort+'),
      _ => AppStrings.t('Эконом', 'Econom'),
    };

/// Что сказать водителю, когда надбавка изменилась (null — молчим). Отдельно от
/// отправки, чтобы правила можно было проверить тестами.
String? surgeChangeMessage(int? previous, int current, RadarAlertSettings s) {
  if (previous == null || previous == current) return null;
  final name = s.tariffName;
  if (current > previous) {
    if (!s.onUp || current < s.minSurge || current <= 0) return null;
    return previous == 0
        ? AppStrings.t('Надбавка появилась: $name +$current', 'A apărut adaos: $name +$current')
        : AppStrings.t('Надбавка выросла: $name +$current (было +$previous)', 'Adaosul a crescut: $name +$current (era +$previous)');
  }
  if (current == 0) return s.onGone ? AppStrings.t('Надбавка пропала: $name 0', 'Adaosul a dispărut: $name 0') : null;
  return s.onDown ? AppStrings.t('Надбавка упала: $name +$current (было +$previous)', 'Adaosul a scăzut: $name +$current (era +$previous)') : null;
}

/// Оповещения радара на iPhone без острова: цифра на иконке, тихий статус
/// на экране блокировки, баннер при изменении надбавки и баннер с ценой заказа.
/// Всё — обычные уведомления, работают при установке через AltStore/Sideloadly.
class RadarAlerts {
  static const MethodChannel _channel = MethodChannel('taxiradar/live_activity');
  static int? _lastSurge;
  static bool _asked = false;

  static Future<void> _call(String method, [Map<String, dynamic>? args]) async {
    if (!Platform.isIOS) return;
    try {
      await _channel.invokeMethod(method, args);
    } catch (e) {
      if (kDebugMode) print('RadarAlerts $method: $e');
    }
  }

  static Future<void> requestPermission() async {
    if (_asked) return;
    _asked = true;
    await _call('requestNotifications');
  }

  static Future<void> notify(String id, String title, String body, {bool passive = false, bool sound = true}) =>
      _notify(id, title, body, passive: passive, sound: sound);

  static Future<void> _notify(String id, String title, String body, {bool passive = false, bool sound = true}) =>
      _call('notify', {'id': id, 'title': title, 'body': body, 'passive': passive, 'sound': sound});

  /// Новая надбавка по выбранному тарифу.
  static Future<void> onSurge(int surge) async {
    final s = await RadarAlertSettings.load();
    await _call('badge', {'count': s.badge ? surge : 0});
    if (s.lockStatus) {
      final time = DateFormat('HH:mm').format(DateTime.now());
      await _notify(
        'radar_status',
        '📡 ${s.tariffName} ${surge > 0 ? '+$surge' : '0'}',
        'Радар включён · обновлено $time',
        passive: true,
      );
    }
    final msg = surgeChangeMessage(_lastSurge, surge, s);
    _lastSurge = surge;
    if (msg != null) await _notify('radar_change', msg, 'Taxi Radar', sound: s.sound);
  }

  /// Цена заказа: «считаю…» тихо, потом тот же баннер с ценой — громко.
  static Future<void> onOrder(ParsedOrder order) async {
    final s = await RadarAlertSettings.load();
    if (!s.orderBanner) return;
    // Крупно — цена, ниже «Эконом · 4.5 км · 18 мин»; адреса не нужны (водитель их видит в Яндексе).
    final line = [
      orderTariffLabel(order.tariff),
      if (order.distanceTime.isNotEmpty) order.distanceTime,
      if (order.surgeBonus > 0) AppStrings.t('надбавка +${order.surgeBonus}', 'adaos +${order.surgeBonus}'),
    ].join(' · ');
    await _notify('radar_order', order.priceText, line, passive: order.calculating, sound: s.sound);
  }

  /// Радар выключили — убираем статус и цифру.
  static Future<void> clear() async {
    _lastSurge = null;
    await _call('badge', {'count': 0});
    await _call('removeNotification', {'id': 'radar_status'});
  }
}
