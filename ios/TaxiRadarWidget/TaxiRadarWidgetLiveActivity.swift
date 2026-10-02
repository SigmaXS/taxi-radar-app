import ActivityKit
import WidgetKit
import SwiftUI

// Struct MUST match LiveActivitiesAppAttributes required by live_activities plugin
struct LiveActivitiesAppAttributes: ActivityAttributes, Identifiable {
    public typealias LiveDeliveryData = ContentState
    public struct ContentState: Codable, Hashable {}
    var id = UUID()
}

extension LiveActivitiesAppAttributes {
    func prefixedKey(_ key: String) -> String {
        return "\(id)_\(key)"
    }
}

let sharedDefault = UserDefaults(suiteName: "group.com.example.taxiradar.taxiRadarApp")

private func readSharedString(forKey key: String, context: ActivityViewContext<LiveActivitiesAppAttributes>, defaultVal: String = "") -> String {
    let prefKey = context.attributes.prefixedKey(key)
    if let val = sharedDefault?.string(forKey: prefKey), !val.isEmpty {
        return val
    }
    return defaultVal
}

private func readSharedBool(forKey key: String, context: ActivityViewContext<LiveActivitiesAppAttributes>, defaultVal: Bool = false) -> Bool {
    let prefKey = context.attributes.prefixedKey(key)
    return sharedDefault?.bool(forKey: prefKey) ?? defaultVal
}

@available(iOSApplicationExtension 16.1, *)
struct TaxiRadarLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LiveActivitiesAppAttributes.self) { context in
            // Lock Screen / Notification Banner View
            let surge = readSharedString(forKey: "surge", context: context, defaultVal: "+0 L")
            let zone = readSharedString(forKey: "zone", context: context, defaultVal: "Кишинёв")
            let econom = readSharedString(forKey: "econom", context: context, defaultVal: "--")
            let comfort = readSharedString(forKey: "comfort", context: context, defaultVal: "--")
            let comfortPlus = readSharedString(forKey: "comfortPlus", context: context, defaultVal: "--")
            let alert = readSharedString(forKey: "alert", context: context, defaultVal: "")
            let updatedAt = readSharedString(forKey: "updatedAt", context: context, defaultVal: "")
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

                // Main Surge Banner
                HStack(alignment: .center, spacing: 12) {
                    HStack(spacing: 6) {
                        Image(systemName: hasSurge ? "flame.fill" : "chart.line.uptrend.xyaxis")
                            .font(.system(size: 20))
                            .foregroundColor(hasSurge ? .orange : .green)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(hasSurge ? "Надбавка к тарифу" : "Спрос в норме")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.gray)
                            Text(surge)
                                .font(.system(size: 22, weight: .heavy))
                                .foregroundColor(hasSurge ? .orange : .green)
                        }
                    }

                    Spacer()

                    if !updatedAt.isEmpty {
                        Text("Обновлено \(updatedAt)")
                            .font(.system(size: 10))
                            .foregroundColor(.gray)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))

                // Tariff Columns
                HStack(spacing: 6) {
                    TariffPill(title: "Эконом", price: econom, icon: "car")
                    TariffPill(title: "Комфорт", price: comfort, icon: "car.side")
                    TariffPill(title: "Комфорт+", price: comfortPlus, icon: "sparkles")
                }

                // Road Alert Banner (if radar / police is detected nearby)
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
            let surge = readSharedString(forKey: "surge", context: context, defaultVal: "+0 L")
            let zone = readSharedString(forKey: "zone", context: context, defaultVal: "Кишинёв")
            let econom = readSharedString(forKey: "econom", context: context, defaultVal: "--")
            let comfort = readSharedString(forKey: "comfort", context: context, defaultVal: "--")
            let comfortPlus = readSharedString(forKey: "comfortPlus", context: context, defaultVal: "--")
            let alert = readSharedString(forKey: "alert", context: context, defaultVal: "")
            let updatedAt = readSharedString(forKey: "updatedAt", context: context, defaultVal: "")
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
                        Text("TaxiRadar")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.yellow)
                        if !updatedAt.isEmpty {
                            Text(updatedAt)
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                    }
                    .padding(.trailing, 4)
                }

                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 6) {
                        HStack(spacing: 8) {
                            ExpandedTariffItem(title: "Эконом", price: econom)
                            ExpandedTariffItem(title: "Комфорт", price: comfort)
                            ExpandedTariffItem(title: "Комфорт+", price: comfortPlus)
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
                        }
                    }
                    .padding(.top, 4)
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
                // Right Pill: Sector name
                Text(zone)
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

// Helper Views
private struct TariffPill: View {
    let title: String
    let price: String
    let icon: String

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.gray)
            Text(price)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 5)
        .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct ExpandedTariffItem: View {
    let title: String
    let price: String

    var body: some View {
        VStack(spacing: 1) {
            Text(title)
                .font(.system(size: 9))
                .foregroundColor(.gray)
            Text(price)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 3)
        .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 6))
    }
}
