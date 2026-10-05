import ActivityKit
import Foundation

@available(iOS 16.1, *)
public struct TaxiRadarAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var surge: String
        public var price: String
        public var pointA: String
        public var pointB: String
        public var alert: String
        public var hasOrder: Bool
        public var updatedAt: String

        public init(
            surge: String,
            price: String,
            pointA: String,
            pointB: String,
            alert: String,
            hasOrder: Bool,
            updatedAt: String
        ) {
            self.surge = surge
            self.price = price
            self.pointA = pointA
            self.pointB = pointB
            self.alert = alert
            self.hasOrder = hasOrder
            self.updatedAt = updatedAt
        }

        /// Устойчив к состояниям, отправленным прошлыми версиями приложения:
        /// неизвестные ключи игнорируются, отсутствующие берутся из значений по умолчанию.
        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            let decodedPrice = try container.decodeIfPresent(String.self, forKey: .price) ?? ""
            surge = try container.decodeIfPresent(String.self, forKey: .surge) ?? "+0"
            price = decodedPrice
            pointA = try container.decodeIfPresent(String.self, forKey: .pointA) ?? ""
            pointB = try container.decodeIfPresent(String.self, forKey: .pointB) ?? ""
            alert = try container.decodeIfPresent(String.self, forKey: .alert) ?? ""
            hasOrder = try container.decodeIfPresent(Bool.self, forKey: .hasOrder) ?? !decodedPrice.isEmpty
            updatedAt = try container.decodeIfPresent(String.self, forKey: .updatedAt) ?? ""
        }
    }

    public init() {}
}