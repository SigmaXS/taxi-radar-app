/// Поправка на пробки: во сколько раз поездка дольше, чем по пустым дорогам.
/// Таблицу по часам сервер собирает по поездкам всех водителей (Android 1.16+).
class TrafficFactor {
  final double value;

  /// true — поправка из поездок водителей; false — данных нет, 1.0 (+ час пик в расчёте).
  final bool fromDrivers;
  const TrafficFactor(this.value, this.fromDrivers);
}

class TrafficModel {
  static List<double?> sharedWeekday = [];
  static List<double?> sharedWeekend = [];

  static void updateSharedTraffic({
    required List<double?> weekday,
    required List<double?> weekend,
  }) {
    sharedWeekday = weekday;
    sharedWeekend = weekend;
  }

  static TrafficFactor factor([DateTime? at]) {
    final now = at ?? DateTime.now();
    final isWeekend = now.weekday == DateTime.saturday || now.weekday == DateTime.sunday;
    final list = isWeekend ? sharedWeekend : sharedWeekday;
    final v = now.hour < list.length ? list[now.hour] : null;
    return v != null ? TrafficFactor(v, true) : const TrafficFactor(1.0, false);
  }

  static double getMultiplierNow() => factor().value;
}
