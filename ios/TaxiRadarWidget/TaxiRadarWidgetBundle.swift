import WidgetKit
import SwiftUI

@main
struct TaxiRadarWidgetBundle: WidgetBundle {
    var body: some Widget {
        if #available(iOS 16.1, *) {
            TaxiRadarLiveActivity()
        }
    }
}
