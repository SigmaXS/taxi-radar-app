import ActivityKit
import WidgetKit
import SwiftUI

struct TaxiRadarLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TaxiRadarAttributes.self) { context in
            // Lock Screen / Notification Banner View
            let surge = context.state.surge
            let zone = context.state.zone
            let price = context.state.price
            let alert = context.state.alert
            let updatedAt = context.state.updatedAt

            let hasOrder = !price.isEmpty
            let hasSurge = surge != "+0" && !surge.isEmpty && surge != "+0 L" && surge != "0"

            VStack(alignment: .leading, spacing: 8) {
                // Header Row
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "car.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.yellow)
                        Text(hasOrder ? "TaxiRadar · Заказ" : "TaxiRadar · Радар")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }

                    Spacer()

                    if !updatedAt.isEmpty {
                        Text(updatedAt)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.gray)
                    }
                }

                if hasOrder {
                    // Вид распознанного заказа: Чистая цена + километры/минуты
                    HStack(alignment: .center, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Цена поездки")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.gray)
                            Text(price)
                                .font(.system(size: 26, weight: .heavy))
                                .foregroundColor(.green)
                        }

                        Spacer()

                        if !surge.isEmpty {
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("Маршрут")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.gray)
                                Text(surge)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.cyan)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))

                    if !zone.isEmpty || !alert.isEmpty {
                        VStack(alignment: .leading, spacing: 3) {
                            if !zone.isEmpty {
                                HStack(spacing: 4) {
                                    Text("📍").font(.system(size: 11))
                                    Text(zone).font(.system(size: 12, weight: .medium)).foregroundColor(.white).lineLimit(1)
                                }
                            }
                            if !alert.isEmpty {
                                HStack(spacing: 4) {
                                    Text("🏁").font(.system(size: 11))
                                    Text(alert).font(.system(size: 12, weight: .medium)).foregroundColor(.white).lineLimit(1)
                                }
                            }
                        }
                    }
                } else {
                    // Режим ожидания: Только надбавка (+15, +35, +55 или +0)
                    HStack(alignment: .center, spacing: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: hasSurge ? "flame.fill" : "chart.line.uptrend.xyaxis")
                                .font(.system(size: 22))
                                .foregroundColor(hasSurge ? .orange : .green)

                            VStack(alignment: .leading, spacing: 1) {
                                Text(hasSurge ? "Надбавка к тарифу" : "Спрос базовый")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.gray)
                                Text(surge)
                                    .font(.system(size: 28, weight: .heavy))
                                    .foregroundColor(hasSurge ? .orange : .green)
                            }
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))

                    // Дорожные предупреждения (радары/полиция)
                    if !alert.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 11))
                                .foregroundColor(.yellow)
                            Text(alert)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.yellow)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.yellow.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                    }
                }
            }
            .padding(14)
            .background(Color(red: 0.11, green: 0.14, blue: 0.20))
            .widgetURL(URL(string: "taxiradar://open"))

        } dynamicIsland: { context in
            let surge = context.state.surge
            let zone = context.state.zone
            let price = context.state.price
            let alert = context.state.alert
            let updatedAt = context.state.updatedAt

            let hasOrder = !price.isEmpty
            let hasSurge = surge != "+0" && !surge.isEmpty && surge != "+0 L" && surge != "0"

            return DynamicIsland {
                // Развернутый вид (по долгому нажатию на Остров)
                DynamicIslandExpandedRegion(.leading) {
                    if hasOrder {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Цена")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                            Text(price)
                                .font(.system(size: 22, weight: .heavy))
                                .foregroundColor(.green)
                        }
                        .padding(.leading, 4)
                    } else {
                        HStack(spacing: 6) {
                            Image(systemName: hasSurge ? "flame.fill" : "car.fill")
                                .font(.system(size: 18))
                                .foregroundColor(hasSurge ? .orange : .yellow)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(hasSurge ? "Надбавка" : "Спрос")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.secondary)
                                Text(surge)
                                    .font(.system(size: 22, weight: .heavy))
                                    .foregroundColor(hasSurge ? .orange : .green)
                            }
                        }
                        .padding(.leading, 4)
                    }
                }

                DynamicIslandExpandedRegion(.trailing) {
                    if hasOrder {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("Маршрут")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                            Text(surge)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.cyan)
                        }
                        .padding(.trailing, 4)
                    } else {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("TaxiRadar")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.yellow)
                            if !updatedAt.isEmpty {
                                Text(updatedAt)
                                    .font(.system(size: 10))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.trailing, 4)
                    }
                }

                DynamicIslandExpandedRegion(.bottom) {
                    if hasOrder {
                        if !zone.isEmpty || !alert.isEmpty {
                            VStack(alignment: .leading, spacing: 2) {
                                if !zone.isEmpty {
                                    HStack(spacing: 4) {
                                        Text("📍").font(.system(size: 9))
                                        Text(zone).font(.system(size: 11, weight: .medium)).foregroundColor(.white).lineLimit(1)
                                    }
                                }
                                if !alert.isEmpty {
                                    HStack(spacing: 4) {
                                        Text("🏁").font(.system(size: 9))
                                        Text(alert).font(.system(size: 11, weight: .medium)).foregroundColor(.white).lineLimit(1)
                                    }
                                }
                            }
                            .padding(.top, 2)
                        }
                    } else {
                        if !alert.isEmpty {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.shield.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(.yellow)
                                Text(alert)
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(.yellow)
                                    .lineLimit(1)
                            }
                            .padding(.top, 4)
                        }
                    }
                }
            } compactLeading: {
                // Левая часть пилюли
                if hasOrder {
                    HStack(spacing: 2) {
                        Image(systemName: "dollarsign.circle.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.green)
                        Text(price)
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(.green)
                    }
                    .padding(.leading, 2)
                } else {
                    HStack(spacing: 2) {
                        Image(systemName: hasSurge ? "flame.fill" : "car.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(hasSurge ? .orange : .yellow)
                        Text(surge)
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(hasSurge ? .orange : .white)
                    }
                    .padding(.leading, 2)
                }
            } compactTrailing: {
                // Правая часть пилюли
                if hasOrder {
                    Text(surge)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.cyan)
                        .lineLimit(1)
                        .padding(.trailing, 2)
                } else {
                    if alert.contains("Радар") || alert.contains("Полиция") {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 10))
                            .foregroundColor(.yellow)
                            .padding(.trailing, 2)
                    } else {
                        EmptyView()
                    }
                }
            } minimal: {
                // Минимальная пилюля
                if hasOrder {
                    Text(price)
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.green)
                } else {
                    Text(surge)
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(hasSurge ? .orange : .yellow)
                }
            }
            .widgetURL(URL(string: "taxiradar://open"))
        }
    }
}

