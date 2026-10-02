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

  static double getMultiplierNow() {
    final now = DateTime.now();
    final hour = now.hour;
    final isWeekend = now.weekday == DateTime.saturday || now.weekday == DateTime.sunday;

    final list = isWeekend ? sharedWeekend : sharedWeekday;
    if (hour >= 0 && hour < list.length && list[hour] != null) {
      return list[hour]!;
    }

    // По умолчанию средние поправки по Кишинёву
    if (isWeekend) {
      return 1.0;
    } else {
      if (hour >= 8 && hour <= 9) return 1.35;
      if (hour >= 17 && hour <= 19) return 1.40;
      if (hour >= 12 && hour <= 14) return 1.15;
      return 1.05;
    }
  }
}
