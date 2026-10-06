class ChatMessage {
  final int id;
  final String author;
  final String text;
  final int timestamp;
  final bool mine;
  final bool admin;

  ChatMessage({
    required this.id,
    required this.author,
    required this.text,
    required this.timestamp,
    required this.mine,
    required this.admin,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> j) {
    int ts = 0;
    if (j['ts'] != null) {
      try {
        ts = DateTime.parse(j['ts'].toString()).millisecondsSinceEpoch;
      } catch (_) {}
    }

    return ChatMessage(
      id: (j['id'] as num?)?.toInt() ?? 0,
      author: (j['nickname'] ?? j['author'])?.toString() ?? 'Водитель',
      text: j['text'] as String? ?? '',
      timestamp: ts,
      mine: j['mine'] == true,
      admin: j['admin'] == true,
    );
  }
}
