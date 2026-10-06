import '../models/chat_message.dart';
import '../models/client_summary.dart';
import '../models/flight.dart';
import '../models/report.dart';
import 'api_service.dart';

class CommunityService {
  // ---------- CHAT ----------
  static Future<List<ChatMessage>> getChatMessages({int afterId = 0}) async {
    final res = await ApiService.post('/api/chat/messages', {
      'count': 50,
      if (afterId > 0) 'after': afterId,
    });
    if (res == null || res['ok'] != true || res['messages'] is! List) {
      return [];
    }
    final list = <ChatMessage>[];
    for (var m in res['messages']) {
      if (m is Map<String, dynamic>) {
        list.add(ChatMessage.fromJson(m));
      }
    }
    return list;
  }

  static Future<bool> sendChatMessage(String text, String author) async {
    final res = await ApiService.post('/api/chat/send', {
      'text': text,
      'author': author,
    });
    return res != null && res['ok'] == true;
  }

  static Future<int> getUnreadChatCount(int lastSeenId) async {
    final res = await ApiService.post('/api/chat/unread', {'since_id': lastSeenId});
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
