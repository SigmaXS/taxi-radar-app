class AppConfig {
  final String telegram;
  final String whatsapp;
  final String viber;
  final String phone;
  final String groupUrl;
  final String tilesApiKey;
  final int referralBonusDays;
  final bool sharedGeocoder;
  final int latestVersionCode;
  final String latestVersionName;
  final String updateUrl;
  final String updateNotes;
  final List<Tariff> tariffs;
  final String currency;
  final Map<String, int> surgeBase;
  final int minVersionCode;
  final List<double?> trafficWeekday;
  final List<double?> trafficWeekend;

  AppConfig({
    required this.telegram,
    required this.whatsapp,
    required this.viber,
    required this.phone,
    required this.groupUrl,
    required this.tilesApiKey,
    required this.referralBonusDays,
    required this.sharedGeocoder,
    this.latestVersionCode = 0,
    this.latestVersionName = '',
    this.updateUrl = '',
    this.updateNotes = '',
    this.tariffs = const [Tariff(days: 30, price: 99)],
    this.currency = 'лей',
    this.surgeBase = const {},
    this.minVersionCode = 0,
    this.trafficWeekday = const [],
    this.trafficWeekend = const [],
  });

  static AppConfig defaultPlan() => AppConfig(
        telegram: 'sigmalxl',
        whatsapp: '+37378293919',
        viber: '+37378293919',
        phone: '+37378293919',
        groupUrl: 'https://t.me/taxi_radar_chisinau',
        tilesApiKey: '',
        referralBonusDays: 3,
        sharedGeocoder: false,
      );

  factory AppConfig.fromJson(Map<String, dynamic> j) {
    List<double?> parseHours(dynamic a) {
      if (a is! List) return [];
      return a.map<double?>((e) {
        if (e == null) return null;
        if (e is num) return e.toDouble();
        return null;
      }).toList();
    }

    final tariffsList = <Tariff>[];
    if (j['tariffs'] is List) {
      for (var t in j['tariffs']) {
        if (t is Map<String, dynamic>) {
          tariffsList.add(Tariff(
            days: (t['days'] as num?)?.toInt() ?? 30,
            price: (t['price'] as num?)?.toInt() ?? 99,
          ));
        }
      }
    }

    final Map<String, int> bases = {};
    if (j['surge_base'] is Map) {
      j['surge_base'].forEach((k, v) {
        if (v is num && v > 0) bases[k.toString()] = v.toInt();
      });
    }

    return AppConfig(
      telegram: (j['telegram'] as String? ?? 'sigmalxl').replaceAll('@', ''),
      whatsapp: j['whatsapp'] as String? ?? '+37378293919',
      viber: j['viber'] as String? ?? '+37378293919',
      phone: j['phone'] as String? ?? '+37378293919',
      groupUrl: j['group_url'] as String? ?? 'https://t.me/taxi_radar_chisinau',
      tilesApiKey: j['tiles_api_key'] as String? ?? '',
      referralBonusDays: (j['referral_bonus_days'] as num?)?.toInt() ?? 3,
      sharedGeocoder: j['shared_geocoder'] == true,
      latestVersionCode: (j['latest_version_code'] as num?)?.toInt() ?? 0,
      latestVersionName: j['latest_version_name'] as String? ?? '',
      updateUrl: j['update_url'] as String? ?? '',
      updateNotes: j['update_notes'] as String? ?? '',
      tariffs: tariffsList.isNotEmpty ? tariffsList : [const Tariff(days: 30, price: 99)],
      currency: j['currency'] as String? ?? 'лей',
      surgeBase: bases,
      minVersionCode: (j['min_version_code'] as num?)?.toInt() ?? 0,
      trafficWeekday: parseHours(j['traffic']?['wd']),
      trafficWeekend: parseHours(j['traffic']?['we']),
    );
  }
}

class Tariff {
  final int days;
  final int price;
  const Tariff({required this.days, required this.price});
}
