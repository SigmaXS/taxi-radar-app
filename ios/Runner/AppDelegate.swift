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
        GeneratedPluginRegistrant.register(with: self)

        if AppDelegate.channel == nil, let controller = window?.rootViewController as? FlutterViewController {
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
            guard #available(iOS 16.1, *) else {
                result(FlutterError(code: "UNSUPPORTED_IOS", message: "Требуется iOS 16.1+ для работы Dynamic Island", details: nil))
                return
            }

            switch call.method {
            case "areActivitiesEnabled":
                let enabled = ActivityAuthorizationInfo().areActivitiesEnabled
                result(enabled)

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

                // Завершаем старые активности перед запуском новой
                for act in Activity<TaxiRadarAttributes>.activities {
                    Task {
                        if #available(iOS 16.2, *) {
                            await act.end(nil, dismissalPolicy: .immediate)
                        } else {
                            await act.end(using: nil, dismissalPolicy: .immediate)
                        }
                    }
                }

                do {
                    if #available(iOS 16.2, *) {
                        let activity = try Activity<TaxiRadarAttributes>.request(
                            attributes: TaxiRadarAttributes(),
                            content: ActivityContent(state: state, staleDate: nil),
                            pushType: nil
                        )
                        result(activity.id)
                    } else {
                        let activity = try Activity<TaxiRadarAttributes>.request(
                            attributes: TaxiRadarAttributes(),
                            contentState: state,
                            pushType: nil
                        )
                        result(activity.id)
                    }
                } catch {
                    result(FlutterError(code: "START_FAILED", message: error.localizedDescription, details: "\(error)"))
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

                let activities = Activity<TaxiRadarAttributes>.activities
                if activities.isEmpty {
                    // Если активности не было, автоматически стартуем
                    do {
                        if #available(iOS 16.2, *) {
                            let activity = try Activity<TaxiRadarAttributes>.request(
                                attributes: TaxiRadarAttributes(),
                                content: ActivityContent(state: state, staleDate: nil),
                                pushType: nil
                            )
                            result(activity.id)
                        } else {
                            let activity = try Activity<TaxiRadarAttributes>.request(
                                attributes: TaxiRadarAttributes(),
                                contentState: state,
                                pushType: nil
                            )
                            result(activity.id)
                        }
                    } catch {
                        result(FlutterError(code: "UPDATE_START_FAILED", message: error.localizedDescription, details: "\(error)"))
                    }
                    return
                }

                for act in activities {
                    Task {
                        if #available(iOS 16.2, *) {
                            await act.update(ActivityContent(state: state, staleDate: nil))
                        } else {
                            await act.update(using: state)
                        }
                    }
                }
                result(true)

            case "stop":
                for act in Activity<TaxiRadarAttributes>.activities {
                    Task {
                        if #available(iOS 16.2, *) {
                            await act.end(nil, dismissalPolicy: .immediate)
                        } else {
                            await act.end(using: nil, dismissalPolicy: .immediate)
                        }
                    }
                }
                result(true)

            default:
                result(FlutterMethodNotImplemented)
            }
        }
    }
}
