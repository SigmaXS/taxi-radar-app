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
            } catch {
                print("[TaxiRadar] areActivitiesEnabled error: \(error)")
                result(false)
            }

        case "start":
            guard let args = call.arguments as? [String: Any] else {
                result(FlutterError(code: "INVALID_ARGS", message: "Данные не переданы", details: nil))
                return
            }

            let state = TaxiRadarAttributes.ContentState(
                surge: args["surge"] as? String ?? "+0 L",
                zone: args["zone"] as? String ?? "Кишинёв",
                price: args["price"] as? String ?? "-- MDL",
                alert: args["alert"] as? String ?? "",
                updatedAt: args["updatedAt"] as? String ?? ""
            )

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
                result(id)
            } else {
                result(FlutterError(code: "START_FAILED", message: "Не удалось запустить Dynamic Island", details: nil))
            }

        case "update":
            guard let args = call.arguments as? [String: Any] else {
                result(FlutterError(code: "INVALID_ARGS", message: "Данные не переданы", details: nil))
                return
            }

            let state = TaxiRadarAttributes.ContentState(
                surge: args["surge"] as? String ?? "+0 L",
                zone: args["zone"] as? String ?? "Кишинёв",
                price: args["price"] as? String ?? "-- MDL",
                alert: args["alert"] as? String ?? "",
                updatedAt: args["updatedAt"] as? String ?? ""
            )

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
                    result(id)
                } else {
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
