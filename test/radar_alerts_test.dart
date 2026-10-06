import 'package:flutter_test/flutter_test.dart';
import 'package:taxi_radar_app/services/radar_alerts.dart';

void main() {
  final defaults = RadarAlertSettings(); // рост от +15, падение и пропажа — молча

  test('первое значение после включения — без баннера', () {
    expect(surgeChangeMessage(null, 35, defaults), isNull);
  });

  test('рост и появление — баннер', () {
    expect(surgeChangeMessage(0, 15, defaults), contains('появилась'));
    expect(surgeChangeMessage(15, 35, defaults), contains('выросла'));
  });

  test('порог «от +35» — +15 молчим, +35 сообщаем', () {
    final s = RadarAlertSettings(minSurge: 35);
    expect(surgeChangeMessage(0, 15, s), isNull);
    expect(surgeChangeMessage(15, 35, s), contains('+35'));
  });

  test('падение и пропажа — только если включены', () {
    expect(surgeChangeMessage(35, 15, defaults), isNull);
    expect(surgeChangeMessage(35, 0, defaults), isNull);
    final s = RadarAlertSettings(onDown: true, onGone: true);
    expect(surgeChangeMessage(35, 15, s), contains('упала'));
    expect(surgeChangeMessage(35, 0, s), contains('пропала'));
  });

  test('без изменений — молчим', () {
    expect(surgeChangeMessage(35, 35, defaults), isNull);
  });

  test('тариф в тексте', () {
    expect(surgeChangeMessage(0, 55, RadarAlertSettings(tariff: 'comfortplus')), contains('Комфорт+'));
  });
}
