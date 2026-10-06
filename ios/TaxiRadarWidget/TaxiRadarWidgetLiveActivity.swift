import ActivityKit
import WidgetKit
import SwiftUI

/// Производные значения состояния с гарантированными запасными значениями.
///
/// Пустая строка в compact-регионе Dynamic Island даёт визуально пустой остров,
/// поэтому headline всегда непустой: цена при заказе, иначе надбавка, иначе «—».
private struct RadarState {
    let surge: String
    let price: String
    let pointA: String
    let pointB: String
    let alert: String
    let hasOrder: Bool
    let updatedAt: String

    init(_ raw: TaxiRadarAttributes.ContentState) {
        let trimmedSurge = raw.surge.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPrice = raw.price.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedA = raw.pointA.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedB = raw.pointB.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedAlert = raw.alert.trimmingCharacters(in: .whitespacesAndNewlines)

        surge = trimmedSurge.isEmpty ? "+0" : trimmedSurge
        price = trimmedPrice
        pointA = trimmedA
        pointB = trimmedB
        alert = trimmedAlert
        hasOrder = raw.hasOrder || !trimmedPrice.isEmpty
        updatedAt = raw.updatedAt.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var hasSurge: Bool {
        surge != "+0" && surge != "0"
    }

    /// Короткий текст для compact-региона: цена без «(км · мин)» либо надбавка.
    var headline: String {
        if hasOrder {
            guard !price.isEmpty else { return "—" }
            return price.components(separatedBy: " (").first ?? price
        }
        return surge
    }

    /// Полный текст для expanded-региона.
    var headlineFull: String {
        hasOrder ? (price.isEmpty ? "—" : price) : surge
    }

    var iconName: String {
        if hasOrder { return "car.fill" }
        return hasSurge ? "flame.fill" : "chart.line.uptrend.xyaxis"
    }

    var accent: Color {
        if hasOrder { return .green }
        return hasSurge ? .orange : .white
    }

    /// Сырое состояние для отладочной строки: позволяет прочитать проблему
    /// прямо с острова, не имея доступа к логам.
    var rawDebug: String {
        "surge=[\(surge)] price=[\(price)] A=[\(pointA)] B=[\(pointB)] "
            + "alert=[\(alert)] заказ=\(hasOrder) t=\(updatedAt)"
    }
}

struct TaxiRadarLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TaxiRadarAttributes.self) { context in
            let s = RadarState(context.state)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("ТЕСТ-ЭКРАН")
                        .font(.system(size: 12, weight: .heavy))
                        .foregroundColor(.green)

                    HStack(spacing: 6) {
                        Image(systemName: s.iconName)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.yellow)
                        Text(s.hasOrder ? "TaxiRadar · Заказ" : "TaxiRadar · Радар")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }

                    Spacer()

                    if !s.updatedAt.isEmpty {
                        Text(s.updatedAt)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.gray)
                    }
                }

                if s.hasOrder {
                    Text(s.price.isEmpty ? "—" : s.price)
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.green)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))

                    if !s.pointA.isEmpty || !s.pointB.isEmpty {
                        VStack(alignment: .leading, spacing: 3) {
                            if !s.pointA.isEmpty {
                                HStack(spacing: 5) {
                                    Text("A")
                                        .font(.system(size: 11, weight: .heavy))
                                        .foregroundColor(.green)
                                    Text(s.pointA)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                            if !s.pointB.isEmpty {
                                HStack(spacing: 5) {
                                    Text("B")
                                        .font(.system(size: 11, weight: .heavy))
                                        .foregroundColor(.orange)
                                    Text(s.pointB)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                        }
                    }
                } else {
                    HStack(alignment: .center, spacing: 12) {
                        Image(systemName: s.iconName)
                            .font(.system(size: 22))
                            .foregroundColor(s.hasSurge ? .orange : .green)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(s.hasSurge ? "Надбавка" : "Спрос базовый")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.gray)
                            Text(s.surge)
                                .font(.system(size: 28, weight: .heavy))
                                .foregroundColor(s.hasSurge ? .orange : .green)
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))
                }

                if !s.alert.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 11))
                            .foregroundColor(.yellow)
                        Text(s.alert)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.yellow)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.yellow.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                }

                Text(s.rawDebug)
                    .font(.system(size: 9, weight: .regular))
                    .foregroundColor(.gray)
                    .lineLimit(2)
            }
            .padding(14)
            .background(Color(red: 0.11, green: 0.14, blue: 0.20))
            .widgetURL(URL(string: "taxiradar://open"))

        } dynamicIsland: { context in
            let s = RadarState(context.state)

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ТЕСТ")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundColor(.green)
                        Text(s.headlineFull)
                            .font(.system(size: 20, weight: .heavy))
                            .foregroundColor(s.accent)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                    }
                    .padding(.leading, 4)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(s.hasSurge ? "Надбавка" : "Спрос")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.secondary)
                        Text(s.surge)
                            .font(.system(size: 18, weight: .heavy))
                            .foregroundColor(s.hasSurge ? .orange : .green)
                        if !s.updatedAt.isEmpty {
                            Text(s.updatedAt)
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.trailing, 4)
                }

                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("ТЕСТ-РАЗВЁРНУТ")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundColor(.green)

                        if s.hasOrder {
                            if !s.pointA.isEmpty {
                                HStack(spacing: 4) {
                                    Text("A")
                                        .font(.system(size: 9, weight: .heavy))
                                        .foregroundColor(.green)
                                    Text(s.pointA)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                            if !s.pointB.isEmpty {
                                HStack(spacing: 4) {
                                    Text("B")
                                        .font(.system(size: 9, weight: .heavy))
                                        .foregroundColor(.orange)
                                    Text(s.pointB)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                        }

                        if !s.alert.isEmpty {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.shield.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(.yellow)
                                Text(s.alert)
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(.yellow)
                                    .lineLimit(1)
                            }
                            .padding(.top, 1)
                        }

                        Text(s.rawDebug)
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                    }
                }
            } compactLeading: {
                // Всегда непустое содержимое: иначе остров визуально пуст.
                HStack(spacing: 2) {
                    Image(systemName: s.iconName)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(s.accent)
                    Text(s.headline)
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(s.accent)
                        .lineLimit(1)
                }
                .padding(.leading, 2)
            } compactTrailing: {
                // Раньше здесь был Color.clear.frame(0, 0) — регион нулевого
                // размера. Теперь всегда время обновления: заодно видно,
                // доходят ли обновления до острова (должно тикать раз в 25 сек).
                Text(s.updatedAt.isEmpty ? "--:--" : s.updatedAt)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                    .padding(.trailing, 2)
            } minimal: {
                Text(s.headline)
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(s.accent)
            }
            .widgetURL(URL(string: "taxiradar://open"))
        }
    }
}