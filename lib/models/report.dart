import 'package:flutter/cupertino.dart';

import '../l10n/app_strings.dart';

/// Тип метки водителей на карте (как в Waze) — те же ключи, что у Android и сервера.
class ReportType {
  final String key;
  final IconData icon;
  final String labelRu;
  final String labelRo;

  /// true — дорожная (предупреждаем в пути), false — про адрес подачи.
  final bool road;
  final Color color;

  const ReportType({
    required this.key,
    required this.icon,
    required this.labelRu,
    required this.labelRo,
    required this.road,
    required this.color,
  });

  String get label => AppStrings.t(labelRu, labelRo);
}

class RoadReports {
  static const types = [
    ReportType(key: 'police', icon: CupertinoIcons.shield_lefthalf_fill, labelRu: 'Полиция', labelRo: 'Poliție', road: true, color: Color(0xFF1565C0)),
    ReportType(key: 'radar', icon: CupertinoIcons.camera_fill, labelRu: 'Радар', labelRo: 'Radar', road: true, color: Color(0xFF6A1B9A)),
    ReportType(key: 'danger', icon: CupertinoIcons.exclamationmark_triangle_fill, labelRu: 'Опасность', labelRo: 'Pericol', road: true, color: Color(0xFFF9A825)),
    ReportType(key: 'accident', icon: CupertinoIcons.car_fill, labelRu: 'ДТП', labelRo: 'Accident', road: true, color: Color(0xFFC62828)),
    ReportType(key: 'closure', icon: CupertinoIcons.nosign, labelRu: 'Перекрытие', labelRo: 'Drum închis', road: true, color: Color(0xFF8E0000)),
    ReportType(key: 'jam', icon: CupertinoIcons.timer_fill, labelRu: 'Пробка', labelRo: 'Ambuteiaj', road: true, color: Color(0xFFEF6C00)),
    ReportType(key: 'pothole', icon: CupertinoIcons.circle_bottomthird_split, labelRu: 'Яма', labelRo: 'Groapă', road: true, color: Color(0xFF6D4C41)),
    ReportType(key: 'addr_noshow', icon: CupertinoIcons.person_crop_circle_badge_xmark, labelRu: 'Адрес: не выходят', labelRo: 'Adresă: nu ies', road: false, color: Color(0xFF546E7A)),
    ReportType(key: 'addr_hard', icon: CupertinoIcons.map_pin_ellipse, labelRu: 'Адрес: сложная подача', labelRo: 'Adresă: acces dificil', road: false, color: Color(0xFF546E7A)),
    ReportType(key: 'addr_cancel', icon: CupertinoIcons.xmark_octagon_fill, labelRu: 'Адрес: частые отмены', labelRo: 'Adresă: anulări frecvente', road: false, color: Color(0xFF546E7A)),
  ];

  static ReportType? byKey(String key) {
    for (final t in types) {
      if (t.key == key) return t;
    }
    return null;
  }
}

class ReportItem {
  final int id;
  final String type;
  final double lat;
  final double lon;
  final int createdMs;
  final bool mine;
  final bool voted;

  ReportItem({
    required this.id,
    required this.type,
    required this.lat,
    required this.lon,
    required this.createdMs,
    required this.mine,
    required this.voted,
  });

  factory ReportItem.fromJson(Map<String, dynamic> j) {
    var created = 0;
    if (j['created'] != null) {
      try {
        created = DateTime.parse(j['created'].toString()).millisecondsSinceEpoch;
      } catch (_) {}
    }
    return ReportItem(
      id: (j['id'] as num?)?.toInt() ?? 0,
      type: j['type'] as String? ?? 'police',
      lat: (j['lat'] as num?)?.toDouble() ?? 0.0,
      lon: (j['lon'] as num?)?.toDouble() ?? 0.0,
      createdMs: created,
      mine: j['mine'] == true,
      voted: j['voted'] == true,
    );
  }
}

/// «Ваша точка»: где поесть, помыть машину, заправиться — как на Android.
class PlaceType {
  final String key;
  final IconData icon;
  final String labelRu;
  final String labelRo;
  final Color color;
  const PlaceType(this.key, this.icon, this.labelRu, this.labelRo, this.color);

  String get label => AppStrings.t(labelRu, labelRo);

  static const types = [
    PlaceType('food', CupertinoIcons.cart_fill, 'Поесть', 'Mâncare', Color(0xFFE65100)),
    PlaceType('coffee', CupertinoIcons.flame_fill, 'Кофе', 'Cafea', Color(0xFF6D4C41)),
    PlaceType('wash', CupertinoIcons.drop_fill, 'Мойка', 'Spălătorie', Color(0xFF0288D1)),
    PlaceType('fuel', CupertinoIcons.gauge, 'Заправка', 'Benzinărie', Color(0xFF2E7D32)),
    PlaceType('tire', CupertinoIcons.wrench_fill, 'Шиномонтаж', 'Vulcanizare', Color(0xFF455A64)),
    PlaceType('wc', CupertinoIcons.person_2_fill, 'Туалет', 'Toaletă', Color(0xFF5E35B1)),
    PlaceType('parking', CupertinoIcons.car_detailed, 'Парковка', 'Parcare', Color(0xFF1565C0)),
  ];

  static PlaceType? byKey(String key) {
    for (final t in types) {
      if (t.key == key) return t;
    }
    return null;
  }
}

class PlaceItem {
  final int id;
  final String type;
  final String name;
  final String note;
  final double lat;
  final double lon;
  final int up;
  final int down;
  final bool mine;
  final int vote;

  PlaceItem.fromJson(Map<String, dynamic> j)
      : id = (j['id'] as num?)?.toInt() ?? 0,
        type = j['type']?.toString() ?? 'food',
        name = j['name']?.toString() ?? '',
        note = j['note']?.toString() ?? '',
        lat = (j['lat'] as num?)?.toDouble() ?? 0,
        lon = (j['lon'] as num?)?.toDouble() ?? 0,
        up = (j['up'] as num?)?.toInt() ?? 0,
        down = (j['down'] as num?)?.toInt() ?? 0,
        mine = j['mine'] == true,
        vote = (j['vote'] as num?)?.toInt() ?? 0;
}
