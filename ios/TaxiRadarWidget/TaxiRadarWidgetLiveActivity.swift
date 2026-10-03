import ActivityKit
import WidgetKit
import SwiftUI

@available(iOS 16.1, *)
struct TaxiRadarLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TaxiRadarAttributes.self) { context in
            // Lock Screen / Notification Banner View
            let surge = context.state.surge
            let zone = context.state.zone
            let price = context.state.price
            let alert = context.state.alert
            let updatedAt = context.state.updatedAt
            let hasSurge = surge != "+0 L" && !surge.isEmpty && surge != "0"

            VStack(alignment: .leading, spacing: 8) {
                // Header Row
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "car.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.yellow)
                        Text("TaxiRadar")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }

                    Spacer()

                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.green)
                            .frame(width: 7, height: 7)
                        Text(zone)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white.opacity(0.9))
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.white.opacity(0.12), in: Capsule())
                }

                // Main Info Banner
                HStack(alignment: .center, spacing: 12) {
                    HStack(spacing: 6) {
                        Image(systemName: hasSurge ? "flame.fill" : "chart.line.uptrend.xyaxis")
                            .font(.system(size: 20))
                            .foregroundColor(hasSurge ? .orange : .green)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(hasSurge ? "Надбавка Яндекс" : "Спрос в норме")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.gray)
                            Text(surge)
                                .font(.system(size: 22, weight: .heavy))
                                .foregroundColor(hasSurge ? .orange : .green)
                        }
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 1) {
                        Text(price)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                        if !updatedAt.isEmpty {
                            Text("Обновлено \(updatedAt)")
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))

                // Road Alert Banner (if radar / police)
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
            let zone = context.state.zone
            let price = context.state.price
            let alert = context.state.alert
            let updatedAt = context.state.updatedAt
            let hasSurge = surge != "+0 L" && !surge.isEmpty && surge != "0"

            return DynamicIsland {
                // Expanded View (Long Press)
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 4) {
                            Image(systemName: hasSurge ? "flame.fill" : "car.fill")
                                .font(.system(size: 14))
                                .foregroundColor(hasSurge ? .orange : .yellow)
                            Text(surge)
                                .font(.system(size: 18, weight: .heavy))
                                .foregroundColor(hasSurge ? .orange : .white)
                        }
                        Text(zone)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                    .padding(.leading, 4)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(price)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        if !updatedAt.isEmpty {
                            Text(updatedAt)
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                    }
                    .padding(.trailing, 4)
                }

                DynamicIslandExpandedRegion(.bottom) {
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
            } compactLeading: {
                // Left Pill: Taxi icon + Surge
                HStack(spacing: 3) {
                    Image(systemName: "car.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.yellow)
                    Text(surge)
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(hasSurge ? .orange : .white)
                }
                .padding(.leading, 2)
            } compactTrailing: {
                // Right Pill: Price or Sector name
                Text(price != "-- MDL" && !price.isEmpty ? price : zone)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.white.opacity(0.85))
                    .lineLimit(1)
                    .padding(.trailing, 2)
            } minimal: {
                // Minimal Pill
                Image(systemName: "car.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(hasSurge ? .orange : .yellow)
            }
            .widgetURL(URL(string: "taxiradar://open"))
        }
    }
}
