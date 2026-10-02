class RideItem {
  final int id;
  final String kind;
  final String from;
  final String to;
  final String when;
  final int? people;
  final int? seats;
  final String text;
  final String? phone;
  final String? telegram;
  final String? groupLink;
  final String? groupKind;
  final String? author;
  final String? source;
  final bool isCarrier;

  RideItem({
    required this.id,
    required this.kind,
    required this.from,
    required this.to,
    required this.when,
    this.people,
    this.seats,
    required this.text,
    this.phone,
    this.telegram,
    this.groupLink,
    this.groupKind,
    this.author,
    this.source,
    required this.isCarrier,
  });

  factory RideItem.fromJson(Map<String, dynamic> j) {
    return RideItem(
      id: (j['id'] as num?)?.toInt() ?? 0,
      kind: j['kind'] as String? ?? 'passenger',
      from: j['from'] as String? ?? '?',
      to: j['to'] as String? ?? '?',
      when: j['when'] as String? ?? '',
      people: (j['people'] as num?)?.toInt(),
      seats: (j['seats'] as num?)?.toInt(),
      text: [j['text'], j['comment']]
          .where((s) => s != null && s.toString().trim().isNotEmpty && s != 'null')
          .join('\n'),
      phone: j['phone']?.toString().takeIf((s) => s.isNotEmpty && s != 'null'),
      telegram: j['telegram']?.toString().replaceAll('@', '').takeIf((s) => s.isNotEmpty && s != 'null'),
      groupLink: j['group_link']?.toString().takeIf((s) => s.startsWith('http')),
      groupKind: j['group_kind'] as String? ?? 'telegram',
      author: j['author']?.toString().takeIf((s) => s.isNotEmpty && s != 'null'),
      source: j['source']?.toString().takeIf((s) => s.isNotEmpty && s != 'null'),
      isCarrier: j['is_carrier'] == true,
    );
  }
}

extension ObjectTakeIf<T> on T {
  T? takeIf(bool Function(T) predicate) => predicate(this) ? this : null;
}
