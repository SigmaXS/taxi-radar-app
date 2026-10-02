class Flight {
  final String flight;
  final String from;
  final String time;
  final String status;
  final bool approx;
  final bool delayed;

  Flight({
    required this.flight,
    required this.from,
    required this.time,
    required this.status,
    required this.approx,
    required this.delayed,
  });

  factory Flight.fromJson(Map<String, dynamic> j) {
    return Flight(
      flight: j['flight'] as String? ?? '',
      from: j['from'] as String? ?? '',
      time: j['time'] as String? ?? '',
      status: j['status'] as String? ?? 'scheduled',
      approx: j['approx'] == true,
      delayed: j['delayed'] == true,
    );
  }

  static const Map<String, String> cities = {
    'IST': 'Стамбул', 'SAW': 'Стамбул', 'OTP': 'Бухарест', 'BBU': 'Бухарест',
    'FCO': 'Рим', 'CIA': 'Рим', 'MXP': 'Милан', 'BGY': 'Бергамо', 'LIN': 'Милан',
    'LTN': 'Лондон', 'LGW': 'Лондон', 'STN': 'Лондон', 'LHR': 'Лондон',
    'MUC': 'Мюнхен', 'FRA': 'Франкфурт', 'VIE': 'Вена', 'WAW': 'Варшава', 'WMI': 'Варшава',
    'BCN': 'Барселона', 'TLV': 'Тель-Авив', 'PRG': 'Прага', 'BLQ': 'Болонья',
    'VCE': 'Венеция', 'TSF': 'Тревизо', 'CDG': 'Париж', 'ORY': 'Париж', 'BVA': 'Париж',
    'NCE': 'Ницца', 'MAD': 'Мадрид', 'VLC': 'Валенсия', 'LIS': 'Лиссабон', 'DUB': 'Дублин',
    'ATH': 'Афины', 'AYT': 'Анталья', 'HRG': 'Хургада', 'SSH': 'Шарм-эш-Шейх', 'DXB': 'Дубай',
    'BUD': 'Будапешт', 'BER': 'Берлин', 'DTM': 'Дортмунд', 'EIN': 'Эйндховен',
    'BRU': 'Брюссель', 'CRL': 'Брюссель', 'TRN': 'Турин', 'PSA': 'Пиза', 'NAP': 'Неаполь',
    'CTA': 'Катания', 'BRI': 'Бари', 'VRN': 'Верона', 'FLR': 'Флоренция', 'GOA': 'Генуя',
    'ZRH': 'Цюрих', 'GVA': 'Женева', 'AMS': 'Амстердам', 'CPH': 'Копенгаген',
    'LCA': 'Ларнака', 'HER': 'Ираклион', 'BOJ': 'Бургас', 'VAR': 'Варна', 'SOF': 'София',
    'RMI': 'Римини', 'PMO': 'Палермо', 'AHO': 'Альгеро', 'IAS': 'Яссы', 'CLJ': 'Клуж'
  };

  static String city(String iata) => cities[iata] ?? iata;
}

class AirportStatus {
  final int queue;
  final List<Flight> flights;

  AirportStatus({required this.queue, required this.flights});
}
