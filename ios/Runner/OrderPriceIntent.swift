import AppIntents
import Foundation
import UserNotifications

/// Действие для «Быстрых команд»: «Taxi Radar: цена заказа».
/// Работает в фоне — Taxi Radar не открывается: текст снимка уходит на сервер,
/// ответ приходит баннером. Живёт в самом приложении (не в расширении), поэтому
/// не зависит от ошибки подписи расширений на iOS 26 при установке через AltStore.
@available(iOS 16.0, *)
struct OrderPriceIntent: AppIntent {
    static var title: LocalizedStringResource = "Цена заказа"
    static var description = IntentDescription(
        "Считает цену заказа Яндекс Про по тексту снимка и присылает баннер. На экране звонка — показывает отметки о клиенте."
    )
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Текст снимка")
    var text: String

    func perform() async throws -> some IntentResult {
        let reply = await OrderPriceClient.request(text: text)
        await OrderPriceClient.notify(title: reply.title, body: reply.body)
        return .result()
    }
}

/// Запрос к серверу Taxi Radar и баннер с ответом.
enum OrderPriceClient {
    static let baseUrl = "https://taxi-radar-license-production.up.railway.app"

    struct Reply {
        let title: String
        let body: String
    }

    // Настройки Flutter (shared_preferences) лежат в UserDefaults с префиксом «flutter.».
    private static var ro: Bool { UserDefaults.standard.string(forKey: "flutter.app_lang") == "RO" }
    private static func t(_ ru: String, _ roText: String) -> String { ro ? roText : ru }

    static func request(text: String) async -> Reply {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return Reply(title: t("Текст со снимка пустой", "Textul din captură e gol"),
                         body: t("Проверьте команду: «Извлечь текст из изображения»", "Verificați comanda: «Extrage textul din imagine»"))
        }
        guard let deviceId = UserDefaults.standard.string(forKey: "flutter.device_id"), !deviceId.isEmpty else {
            return Reply(title: "Taxi Radar", body: t("Откройте Taxi Radar один раз, чтобы войти", "Deschideți Taxi Radar o dată pentru autentificare"))
        }
        guard let url = URL(string: baseUrl + "/api/order/price") else {
            return Reply(title: "Taxi Radar", body: "URL")
        }
        var req = URLRequest(url: url, timeoutInterval: 20)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        let payload: [String: Any] = ["device_id": deviceId, "text": trimmed, "lang": ro ? "ro" : "ru"]
        req.httpBody = try? JSONSerialization.data(withJSONObject: payload)
        do {
            let (data, _) = try await URLSession.shared.data(for: req)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return Reply(title: "Taxi Radar", body: t("Сервер ответил непонятно", "Răspuns neclar de la server"))
            }
            if json["ok"] as? Bool != true {
                return Reply(title: t("Цена не посчитана", "Prețul nu s-a calculat"),
                             body: json["message"] as? String ?? t("Попробуйте ещё раз", "Încercați din nou"))
            }
            if json["kind"] as? String == "client" {
                return clientReply(json)
            }
            return Reply(title: json["title"] as? String ?? "—", body: json["line"] as? String ?? "")
        } catch {
            return Reply(title: "Taxi Radar", body: t("Нет связи с сервером", "Fără conexiune la server"))
        }
    }

    /// Экран звонка: отметки водителей о клиенте; номер сохраняем для экрана «Клиенты».
    private static func clientReply(_ json: [String: Any]) -> Reply {
        let phone = json["phone"] as? String ?? ""
        UserDefaults.standard.set(phone, forKey: "flutter.last_client_phone")
        let labels: [String: (String, String)] = [
            "slow": ("Долго выходит", "Iese greu"), "noshow": ("Не вышел", "Nu a ieșit"),
            "rude": ("Неадекватный", "Inadecvat"), "unpaid": ("Не заплатил", "Nu a achitat"),
            "ok": ("Всё ок", "Totul ok"), "card": ("Оплата картой", "Plată cu cardul"), "plus": ("Яндекс Плюс", "Yandex Plus"),
        ]
        let order = ["noshow", "unpaid", "rude", "slow", "ok", "card", "plus"]
        let tags = json["tags"] as? [String: Any] ?? [:]
        let parts = order.compactMap { key -> String? in
            guard let n = (tags[key] as? NSNumber)?.intValue, n > 0, let l = labels[key] else { return nil }
            return "\(ro ? l.1 : l.0) ×\(n)"
        }
        let reviews = (json["reviews"] as? NSNumber)?.intValue ?? 0
        var body = parts.isEmpty ? t("Отметок водителей нет", "Nicio etichetă de la șoferi") : parts.joined(separator: " · ")
        if reviews > 0 { body += t(" · отзывов: \(reviews)", " · recenzii: \(reviews)") }
        return Reply(title: t("Клиент ", "Client ") + pretty(phone), body: body)
    }

    private static func pretty(_ n: String) -> String {
        guard n.hasPrefix("+373"), n.count == 12 else { return n }
        let d = Array(n.dropFirst(4))
        return "+373 \(String(d[0..<2])) \(String(d[2..<5])) \(String(d[5...]))"
    }

    static func notify(title: String, body: String) async {
        let center = UNUserNotificationCenter.current()
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.threadIdentifier = "taxiradar"
        if #available(iOS 15.0, *) { content.interruptionLevel = .active }
        center.removeDeliveredNotifications(withIdentifiers: ["radar_order"])
        try? await center.add(UNNotificationRequest(identifier: "radar_order", content: content, trigger: nil))
    }
}
