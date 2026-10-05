import ActivityKit
import WidgetKit
import SwiftUI

struct TaxiRadarLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TaxiRadarAttributes.self) { context in
            let surge = context.state.surge
            let price = context.state.price
            let pointA = context.state.pointA
            let pointB = context.state.pointB
            let alert = context.state.alert
            let hasOrder = context.state.hasOrder
            let hasSurge = !surge.isEmpty && surge != "+0"

            VStack(alignment: .leading, spacing: 8) {
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

                    if !context.state.updatedAt.isEmpty {
                        Text(context.state.updatedAt)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.gray)
                    }
                }

                if hasOrder {
                    // Цена крупно, вместе с километрами и минутами: «85 MDL (5.2 км · 12 мин)»
                    Text(price)
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.green)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))

                    if !pointA.isEmpty || !pointB.isEmpty {
                        VStack(alignment: .leading, spacing: 3) {
                            if !pointA.isEmpty {
                                HStack(spacing: 5) {
                                    Text("A")
                                        .font(.system(size: 11, weight: .heavy))
                                        .foregroundColor(.green)
                                    Text(pointA)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                            if !pointB.isEmpty {
                                HStack(spacing: 5) {
                                    Text("B")
                                        .font(.system(size: 11, weight: .heavy))
                                        .foregroundColor(.orange)
                                    Text(pointB)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                        }
                    }
                } else {
                    // Только текущая надбавка: +15 / +35 / +55 / +0
                    HStack(alignment: .center, spacing: 12) {
                        Image(systemName: hasSurge ? "flame.fill" : "chart.line.uptrend.xyaxis")
                            .font(.system(size: 22))
                            .foregroundColor(hasSurge ? .orange : .green)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(hasSurge ? "Надбавка" : "Спрос базовый")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.gray)
                            Text(surge)
                                .font(.system(size: 28, weight: .heavy))
                                .foregroundColor(hasSurge ? .orange : .green)
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))
                }

                // Радарные предупреждения показываются независимо от наличия заказа
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
            .padding(14)
            .background(Color(red: 0.11, green: 0.14, blue: 0.20))
            .widgetURL(URL(string: "taxiradar://open"))

        } dynamicIsland: { context in
            let surge = context.state.surge
            let price = context.state.price
            let pointA = context.state.pointA
            let pointB = context.state.pointB
            let alert = context.state.alert
            let hasOrder = context.state.hasOrder
            let hasSurge = !surge.isEmpty && surge != "+0"
            // В компактной пилюле показываем чистую цену без километров
            let shortPrice = price.components(separatedBy: " (").first ?? price

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    if hasOrder {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Цена")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                            Text(price)
                                .font(.system(size: 22, weight: .heavy))
                                .foregroundColor(.green)
                                .lineLimit(1)
                                .minimumScaleFactor(0.6)
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
                            Text(hasSurge ? "Надбавка" : "Спрос")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                            Text(surge)
                                .font(.system(size: 18, weight: .heavy))
                                .foregroundColor(hasSurge ? .orange : .green)
                        }
                        .padding(.trailing, 4)
                    } else {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("TaxiRadar")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.yellow)
                            if !context.state.updatedAt.isEmpty {
                                Text(context.state.updatedAt)
                                    .font(.system(size: 10))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.trailing, 4)
                    }
                }

                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 3) {
                        if hasOrder {
                            if !pointA.isEmpty {
                                HStack(spacing: 4) {
                                    Text("A")
                                        .font(.system(size: 9, weight: .heavy))
                                        .foregroundColor(.green)
                                    Text(pointA)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                            if !pointB.isEmpty {
                                HStack(spacing: 4) {
                                    Text("B")
                                        .font(.system(size: 9, weight: .heavy))
                                        .foregroundColor(.orange)
                                    Text(pointB)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                }
                            }
                        }
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
                            .padding(.top, 1)
                        }
                    }
                }
            } compactLeading: {
                if hasOrder {
                    Image(systemName: "car.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.green)
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
                if hasOrder {
                    Text(shortPrice)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.green)
                        .lineLimit(1)
                        .padding(.trailing, 2)
                } else {
                    EmptyView()
                }
            } minimal: {
                if hasOrder {
                    Text(shortPrice)
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