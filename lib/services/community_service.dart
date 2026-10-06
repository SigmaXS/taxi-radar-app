import '../models/chat_message.dart';
import '../models/client_summary.dart';
import '../models/flight.dart';
import '../models/report.dart';
import 'api_service.dart';

class ChatPage {
  final List<ChatMessage> messages;
  final Set<int> deleted;
  final String? nickname;
  final bool muted;
  final bool admin;
  const ChatPage({required this.messages, required this.deleted, this.nickname, this.muted = false, this.admin = false});
}

class CommunityService {
  // ---------- CHAT ----------
  /// Сообщения после [after] (0 — последние 50), свой ник и id удалённых админом.
  static Future<ChatPage?> getChat({int after = 0}) async {
    final res = await ApiService.post('/api/chat/list', {if (after > 0) 'after': after});
    if (res == null || res['ok'] != true) return null;
    final list = <ChatMessage>[];
    if (res['messages'] is List) {
      for (final m in res['messages']) {
        if (m is Map<String, dynamic>) list.add(ChatMessage.fromJson(m));
      }
    }
    final me = res['me'] is Map ? res['me'] as Map : null;
    return ChatPage(
      messages: list,
      deleted: (res['deleted'] is List) ? (res['deleted'] as List).map((e) => (e as num).toInt()).toSet() : <int>{},
      nickname: me?['nickname']?.toString(),
      muted: me?['muted'] == true,
      admin: me?['admin'] == true,
    );
  }

  /// Ник в чате (2–20 букв или цифр). Возвращает текст ошибки или null.
  static Future<String?> setChatNickname(String nickname) async {
    final res = await ApiService.post('/api/chat/profile', {'nickname': nickname});
    if (res == null) return 'Нет связи с сервером';
    return res['ok'] == true ? null : (res['message']?.toString() ?? 'Ошибка');
  }

  /// Текст ошибки или null, если отправлено.
  static Future<String?> sendChatMessage(String text) async {
    final res = await ApiService.post('/api/chat/send', {'text': text});
    if (res == null) return 'Нет связи с сервером';
    return res['ok'] == true ? null : (res['message']?.toString() ?? 'Ошибка');
  }

  /// Админ чата: action = delete | mute.
  static Future<String?> moderateChat(int id, String action) async {
    final res = await ApiService.post('/api/chat/moderate', {'id': id, 'action': action});
    if (res == null) return 'Нет связи с сервером';
    return res['ok'] == true ? null : (res['message']?.toString() ?? 'Ошибка');
  }

  /// Сколько новых (не своих) сообщений после [lastSeenId].
  static Future<int> getUnreadChatCount(int lastSeenId) async {
    final res = await ApiService.post('/api/chat/unread', {'after': lastSeenId});
    if (res != null && res['ok'] == true) {
      return (res['count'] as num?)?.toInt() ?? 0;
    }
    return 0;
  }

  // ---------- REPORTS ----------
  static Future<List<ReportItem>> getReports(double lat, double lon) async {
    final res = await ApiService.post('/api/reports/list', {'lat': lat, 'lon': lon});
    if (res == null || res['ok'] != true || res['reports'] is! List) {
      return [];
    }
    final list = <ReportItem>[];
    for (var r in res['reports']) {
      if (r is Map<String, dynamic>) {
        list.add(ReportItem.fromJson(r));
      }
    }
    return list;
  }

  static Future<Map<String, dynamic>> addReport(String type, double lat, double lon) async {
    final res = await ApiService.post('/api/reports/add', {
      'type': type,
      'lat': lat,
      'lon': lon,
    });
    return res ?? {'ok': false, 'message': 'Нет связи'};
  }

  static Future<bool> voteReport(int id, bool still) async {
    final res = await ApiService.post('/api/reports/vote', {
      'id': id,
      'still': still,
    });
    return res != null && res['ok'] == true;
  }

  // ---------- AIRPORT ----------
  static Future<AirportStatus?> getAirportStatus() async {
    final res = await ApiService.post('/api/airport/status');
    if (res == null || res['ok'] != true) return null;
    final flights = <Flight>[];
    if (res['flights'] is List) {
      for (var f in res['flights']) {
        if (f is Map<String, dynamic>) {
          flights.add(Flight.fromJson(f));
        }
      }
    }
    return AirportStatus(
      queue: (res['queue'] as num?)?.toInt() ?? 0,
      flights: flights,
    );
  }

  static Future<void> pingAirport() async {
    await ApiService.post('/api/airport/ping');
  }

  // ---------- CLIENTS ----------
  /// Почему последний запрос по клиенту не удался — для показа водителю.
  static String lastClientError = '';

  static ClientSummary? _clientResult(Map<String, dynamic>? res) {
    if (res == null) {
      lastClientError = 'Нет связи с сервером';
      return null;
    }
    if (res['ok'] != true) {
      lastClientError = res['message']?.toString() ?? 'Сервер не принял запрос';
      return null;
    }
    lastClientError = '';
    return ClientSummary.fromJson(res);
  }

  /// [phone] — уже в международном виде (PhoneNumbers.normalize).
  static Future<ClientSummary?> checkClient(String phone) async =>
      _clientResult(await ApiService.post('/api/clients/check', {'phone': phone}));

  static Future<ClientSummary?> tagClient(String phone, String tag, bool on) async {
    final res = await ApiService.post('/api/clients/tag', {
      'phone': phone,
      'tag': tag,
      'on': on,
    });
    return _clientResult(res);
  }

  static Future<ClientSummary?> reviewClient(String phone, String text) async {
    final res = await ApiService.post('/api/clients/review', {
      'phone': phone,
      'text': text,
    });
    return _clientResult(res);
  }
}
