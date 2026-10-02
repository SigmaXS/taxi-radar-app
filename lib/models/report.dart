import 'package:flutter/material.dart';

class ReportType {
  final String key;
  final String emoji;
  final String labelRu;
  final String labelRo;
  final bool road;
  final Color color;

  const ReportType({
    required this.key,
    required this.emoji,
    required this.labelRu,
    required this.labelRo,
    required this.road,
    required this.color,
  });
}

class RoadReports {
  static const types = [
    ReportType(key: 'police', emoji: '🚓', labelRu: 'Полиция', labelRo: 'Poliție', road: true, color: Color(0xFF1565C0)),
    ReportType(key: 'radar', emoji: '📸', labelRu: 'Радар', labelRo: 'Radar', road: true, color: Color(0xFF6A1B9A)),
    ReportType(key: 'accident', emoji: '💥', labelRu: 'ДТП', labelRo: 'Accident', road: true, color: Color(0xFFC62828)),
    ReportType(key: 'closure', emoji: '⛔', labelRu: 'Перекрытие', labelRo: 'Drum blocat', road: true, color: Color(0xFF8E0000)),
    ReportType(key: 'jam', emoji: '🚦', labelRu: 'Пробка', labelRo: 'Ambuteiaj', road: true, color: Color(0xFFEF6C00)),
    ReportType(key: 'pothole', emoji: '🕳', labelRu: 'Яма', labelRo: 'Groapă', road: true, color: Color(0xFF6D4C41)),
    ReportType(key: 'addr_noshow', emoji: '🙅', labelRu: 'Адрес: не выходят', labelRo: 'Adresă: nu ies', road: false, color: Color(0xFF546E7A)),
    ReportType(key: 'addr_hard', emoji: '🚧', labelRu: 'Адрес: сложная подача', labelRo: 'Adresă: acces dificil', road: false, color: Color(0xFF546E7A)),
    ReportType(key: 'addr_cancel', emoji: '❌', labelRu: 'Адрес: частые отмены', labelRo: 'Adresă: anulări frecvente', road: false, color: Color(0xFF546E7A)),
  ];

  static ReportType? byKey(String key) {
    try {
      return types.firstWhere((t) => t.key == key);
    } catch (_) {
      return null;
    }
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
    int parsedCreated = 0;
    if (j['created'] != null) {
      try {
        parsedCreated = DateTime.parse(j['created'].toString()).millisecondsSinceEpoch;
      } catch (_) {}
    }

    return ReportItem(
      id: (j['id'] as num?)?.toInt() ?? 0,
      type: j['type'] as String? ?? 'police',
      lat: (j['lat'] as num?)?.toDouble() ?? 0.0,
      lon: (j['lon'] as num?)?.toDouble() ?? 0.0,
      createdMs: parsedCreated,
      mine: j['mine'] == true,
      voted: j['voted'] == true,
    );
  }
}
