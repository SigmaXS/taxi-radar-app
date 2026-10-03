import ActivityKit
import Foundation

public struct TaxiRadarAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var surge: String
        public var zone: String
        public var price: String
        public var alert: String
        public var updatedAt: String

        public init(surge: String, zone: String, price: String, alert: String, updatedAt: String) {
            self.surge = surge
            self.zone = zone
            self.price = price
            self.alert = alert
            self.updatedAt = updatedAt
        }
    }

    public init() {}
}
