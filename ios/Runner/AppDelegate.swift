import Flutter
import UIKit
import ActivityKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
    private static var channel: FlutterMethodChannel?

    public static func handleIncomingUrl(_ url: URL) {
        DispatchQueue.main.async {
            channel?.invokeMethod("onUrl", arguments: url.absoluteString)
        }
    }

    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        if let controller = window?.rootViewController as? FlutterViewController {
            GeneratedPluginRegistrant.register(with: self)
            setupLiveActivityChannel(binaryMessenger: controller.binaryMessenger)
        }

        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }

    func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
        GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
        setupLiveActivityChannel(binaryMessenger: engineBridge.applicationRegistrar.messenger())
    }

    private func setupLiveActivityChannel(binaryMessenger: FlutterBinaryMessenger) {
        guard AppDelegate.channel == nil else { return }

        let ch = FlutterMethodChannel(name: "taxiradar/live_activity", binaryMessenger: binaryMessenger)
        AppDelegate.channel = ch

        ch.setMethodCallHandler { [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) in
            self?.handleLiveActivityCall(call, result: result)
        }
    }

    override func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey : Any] = [:]
    ) -> Bool {
        AppDelegate.handleIncomingUrl(url)
        return super.application(app, open: url, options: options)
    }

    @available(iOS 16.1, *)
    private static func makeContentState(from args: [String: Any], method: String) -> TaxiRadarAttributes.ContentState {
        let price = args["price"] as? String ?? ""
        let surge = args["surge"] as? String ?? "+0"
        let pointA = args["pointA"] as? String ?? ""
        let pointB = args["pointB"] as? String ?? ""
        let alert = args["alert"] as? String ?? ""
        let hasOrder = args["hasOrder"] as? Bool ?? !price.isEmpty
        print("[TaxiRadar] \(method) state <- surge=\(surge) price=\(price.isEmpty ? "-" : price) A=\(pointA.isEmpty ? "-" : pointA) B=\(pointB.isEmpty ? "-" : pointB) alert=\(alert.isEmpty ? "-" : alert) hasOrder=\(hasOrder)")
        return TaxiRadarAttributes.ContentState(
            surge: surge,
            price: price,
            pointA: pointA,
            pointB: pointB,
            alert: alert,
            hasOrder: hasOrder,
            updatedAt: args["updatedAt"] as? String ?? ""
        )
    }

    private func handleLiveActivityCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        DispatchQueue.main.async {
            if #available(iOS 16.1, *) {
                self.handleLiveActivityCallImpl(call, result: result)
            } else {
                result(FlutterError(code: "UNSUPPORTED_IOS", message: "Требуется iOS 16.1+ для Dynamic Island", details: nil))
            }
        }
    }

    @available(iOS 16.1, *)
    private func handleLiveActivityCallImpl(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "areActivitiesEnabled":
            var isEnabled = false
            do {
                try NSExceptionCatcher.catchException {
                    isEnabled = ActivityAuthorizationInfo().areActivitiesEnabled
                }
                result(isEnabled)
                print("[TaxiRadar] areActivitiesEnabled = \(isEnabled)")
            } catch {
                print("[TaxiRadar] areActivitiesEnabled error: \(error)")
                result(false)
            }

        case "start":
            guard let args = call.arguments as? [String: Any] else {
                result(FlutterError(code: "INVALID_ARGS", message: "Данные не переданы", details: nil))
                return
            }

            let state = AppDelegate.makeContentState(from: args, method: call.method)

            // Проверяем существующие активности
            var existingActivity: Activity<TaxiRadarAttributes>?
            try? NSExceptionCatcher.catchException {
                existingActivity = Activity<TaxiRadarAttributes>.activities.first
            }

            if let existing = existingActivity {
                Task {
                    try? NSExceptionCatcher.catchException {
                        Task {
                            if #available(iOS 16.2, *) {
                                await existing.update(ActivityContent(state: state, staleDate: nil))
                            } else {
                                await existing.update(using: state)
                            }
                        }
                    }
                }
                result(existing.id)
                print("[TaxiRadar] start: reused existing activity \(existing.id)")
                return
            }

            // Создаем новую активность с перехватом системных исключений
            var activityId: String?
            do {
                try NSExceptionCatcher.catchException {
                    if #available(iOS 16.2, *) {
                        if let activity = try? Activity<TaxiRadarAttributes>.request(
                            attributes: TaxiRadarAttributes(),
                            content: ActivityContent(state: state, staleDate: nil),
                            pushType: nil
                        ) {
                            activityId = activity.id
                        }
                    } else {
                        if let activity = try? Activity<TaxiRadarAttributes>.request(
                            attributes: TaxiRadarAttributes(),
                            contentState: state,
                            pushType: nil
                        ) {
                            activityId = activity.id
                        }
                    }
                }
            } catch {
                print("[TaxiRadar] LiveActivity start exception: \(error)")
                result(FlutterError(code: "OBJC_EXCEPTION", message: "Ошибка системы: \(error.localizedDescription)", details: nil))
                return
            }

            if let id = activityId {
                print("[TaxiRadar] start: NEW activity created id=\(id)")
                result(id)
            } else {
                print("[TaxiRadar] start FAILED: Activity.request returned nil (areActivitiesEnabled=false or system refused)")
                result(FlutterError(code: "START_FAILED", message: "Не удалось запустить Dynamic Island", details: nil))
            }

        case "update":
            guard let args = call.arguments as? [String: Any] else {
                result(FlutterError(code: "INVALID_ARGS", message: "Данные не переданы", details: nil))
                return
            }

            let state = AppDelegate.makeContentState(from: args, method: call.method)

            var currentActivities: [Activity<TaxiRadarAttributes>] = []
            try? NSExceptionCatcher.catchException {
                currentActivities = Activity<TaxiRadarAttributes>.activities
            }

            if currentActivities.isEmpty {
                // Если активности нет, пробуем запустить
                var newId: String?
                do {
                    try NSExceptionCatcher.catchException {
                        if #available(iOS 16.2, *) {
                            if let act = try? Activity<TaxiRadarAttributes>.request(
                                attributes: TaxiRadarAttributes(),
                                content: ActivityContent(state: state, staleDate: nil),
                                pushType: nil
                            ) {
                                newId = act.id
                            }
                        } else {
                            if let act = try? Activity<TaxiRadarAttributes>.request(
                                attributes: TaxiRadarAttributes(),
                                contentState: state,
                                pushType: nil
                            ) {
                                newId = act.id
                            }
                        }
                    }
                } catch {
                    print("[TaxiRadar] Update-start error: \(error)")
                }

                if let id = newId {
                    print("[TaxiRadar] update: no activity found, created \(id)")
                    result(id)
                } else {
                    print("[TaxiRadar] update FAILED: no activity and request returned nil")
                    result(FlutterError(code: "UPDATE_START_FAILED", message: "Активность не найдена", details: nil))
                }
                return
            }

            for act in currentActivities {
                Task {
                    try? NSExceptionCatcher.catchException {
                        Task {
                            if #available(iOS 16.2, *) {
                                await act.update(ActivityContent(state: state, staleDate: nil))
                            } else {
                                await act.update(using: state)
                            }
                        }
                    }
                }
            }
            print("[TaxiRadar] update: applied to \(currentActivities.count) activit(y/ies)")
            result(true)

        case "stop":
            var activitiesToEnd: [Activity<TaxiRadarAttributes>] = []
            try? NSExceptionCatcher.catchException {
                activitiesToEnd = Activity<TaxiRadarAttributes>.activities
            }

            for act in activitiesToEnd {
                Task {
                    try? NSExceptionCatcher.catchException {
                        Task {
                            if #available(iOS 16.2, *) {
                                await act.end(nil, dismissalPolicy: .immediate)
                            } else {
                                await act.end(using: nil, dismissalPolicy: .immediate)
                            }
                        }
                    }
                }
            }
            result(true)

        default:
            result(FlutterMethodNotImplemented)
        }
    }
}
