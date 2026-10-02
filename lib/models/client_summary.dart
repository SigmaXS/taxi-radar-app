class ClientReview {
  final String text;
  final int timestamp;
  final bool mine;
  final bool admin;

  ClientReview({
    required this.text,
    required this.timestamp,
    required this.mine,
    required this.admin,
  });
}

class ClientSummary {
  final Map<String, int> tags;
  final Set<String> mine;
  final List<ClientReview> reviews;

  ClientSummary({
    required this.tags,
    required this.mine,
    required this.reviews,
  });

  static const Map<String, String> tagLabelsRu = {
    'slow': 'Долго выходит',
    'noshow': 'Не вышел',
    'rude': 'Неадекватный',
    'unpaid': 'Не заплатил',
    'ok': 'Всё ок',
    'card': 'Оплата картой',
    'plus': 'Есть Яндекс Плюс',
  };

  static const Map<String, String> tagLabelsRo = {
    'slow': 'Iese greu',
    'noshow': 'Nu a ieșit',
    'rude': 'Inadecvat',
    'unpaid': 'Nu a achitat',
    'ok': 'Totul ok',
    'card': 'Plată cu cardul',
    'plus': 'Are Yandex Plus',
  };

  static const Set<String> negativeTags = {'slow', 'noshow', 'rude', 'unpaid'};

  factory ClientSummary.fromJson(Map<String, dynamic> j) {
    final tags = <String, int>{};
    if (j['tags'] is Map) {
      j['tags'].forEach((k, v) {
        if (v is num) tags[k.toString()] = v.toInt();
      });
    }

    final mine = <String>{};
    if (j['mine'] is List) {
      for (var m in j['mine']) {
        mine.add(m.toString());
      }
    }

    final reviews = <ClientReview>[];
    if (j['reviews'] is List) {
      for (var r in j['reviews']) {
        if (r is Map) {
          int ts = 0;
          if (r['ts'] != null) {
            try {
              ts = DateTime.parse(r['ts'].toString()).millisecondsSinceEpoch;
            } catch (_) {}
          }
          reviews.add(ClientReview(
            text: r['text']?.toString() ?? '',
            timestamp: ts,
            mine: r['mine'] == true,
            admin: r['admin'] == true,
          ));
        }
      }
    }

    return ClientSummary(tags: tags, mine: mine, reviews: reviews);
  }
}
