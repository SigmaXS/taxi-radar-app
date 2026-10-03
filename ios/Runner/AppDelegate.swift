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
            var objcErr: NSError?
            let ok = NSExceptionCatcher.catchException({
                isEnabled = ActivityAuthorizationInfo().areActivitiesEnabled
            }, error: &objcErr)

            if ok && objcErr == nil {
                result(isEnabled)
            } else {
                let msg = objcErr?.localizedDescription ?? "Не удалось проверить Live Activities"
                print("[TaxiRadar] areActivitiesEnabled error: \(msg)")
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
            var checkErr: NSError?
            _ = NSExceptionCatcher.catchException({
                existingActivity = Activity<TaxiRadarAttributes>.activities.first
            }, error: &checkErr)

            if let existing = existingActivity {
                Task {
                    var updateErr: NSError?
                    _ = NSExceptionCatcher.catchException({
                        Task {
                            if #available(iOS 16.2, *) {
                                await existing.update(ActivityContent(state: state, staleDate: nil))
                            } else {
                                await existing.update(using: state)
                            }
                        }
                    }, error: &updateErr)
                }
                result(existing.id)
                return
            }

            // Создаем новую активность с перехватом Objective-C исключений
            var activityId: String?
            var requestError: Error?
            var objcExceptionError: NSError?

            let success = NSExceptionCatcher.catchException({
                do {
                    if #available(iOS 16.2, *) {
                        let activity = try Activity<TaxiRadarAttributes>.request(
                            attributes: TaxiRadarAttributes(),
                            content: ActivityContent(state: state, staleDate: nil),
                            pushType: nil
                        )
                        activityId = activity.id
                    } else {
                        let activity = try Activity<TaxiRadarAttributes>.request(
                            attributes: TaxiRadarAttributes(),
                            contentState: state,
                            pushType: nil
                        )
                        activityId = activity.id
                    }
                } catch {
                    requestError = error
                }
            }, error: &objcExceptionError)

            if !success || objcExceptionError != nil {
                let reason = objcExceptionError?.localizedDescription ?? "Неизвестное системное исключение"
                print("[TaxiRadar] LiveActivity start threw NSException: \(reason)")
                result(FlutterError(code: "OBJC_EXCEPTION", message: "Ошибка системы: \(reason)", details: nil))
                return
            }

            if let err = requestError {
                print("[TaxiRadar] LiveActivity start Swift error: \(err)")
                result(FlutterError(code: "START_FAILED", message: err.localizedDescription, details: "\(err)"))
                return
            }

            if let id = activityId {
                result(id)
            } else {
                result(FlutterError(code: "START_FAILED", message: "Не удалось получить ID активности", details: nil))
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
            _ = NSExceptionCatcher.catchException({
                currentActivities = Activity<TaxiRadarAttributes>.activities
            }, error: nil)

            if currentActivities.isEmpty {
                // Если активности нет, пробуем запустить
                var newId: String?
                var errDesc: String?

                _ = NSExceptionCatcher.catchException({
                    do {
                        if #available(iOS 16.2, *) {
                            let act = try Activity<TaxiRadarAttributes>.request(
                                attributes: TaxiRadarAttributes(),
                                content: ActivityContent(state: state, staleDate: nil),
                                pushType: nil
                            )
                            newId = act.id
                        } else {
                            let act = try Activity<TaxiRadarAttributes>.request(
                                attributes: TaxiRadarAttributes(),
                                contentState: state,
                                pushType: nil
                            )
                            newId = act.id
                        }
                    } catch {
                        errDesc = error.localizedDescription
                    }
                }, error: nil)

                if let id = newId {
                    result(id)
                } else {
                    result(FlutterError(code: "UPDATE_START_FAILED", message: errDesc ?? "Активность не найдена", details: nil))
                }
                return
            }

            for act in currentActivities {
                Task {
                    _ = NSExceptionCatcher.catchException({
                        Task {
                            if #available(iOS 16.2, *) {
                                await act.update(ActivityContent(state: state, staleDate: nil))
                            } else {
                                await act.update(using: state)
                            }
                        }
                    }, error: nil)
                }
            }
            result(true)

        case "stop":
            var activitiesToEnd: [Activity<TaxiRadarAttributes>] = []
            _ = NSExceptionCatcher.catchException({
                activitiesToEnd = Activity<TaxiRadarAttributes>.activities
            }, error: nil)

            for act in activitiesToEnd {
                Task {
                    _ = NSExceptionCatcher.catchException({
                        Task {
                            if #available(iOS 16.2, *) {
                                await act.end(nil, dismissalPolicy: .immediate)
                            } else {
                                await act.end(using: nil, dismissalPolicy: .immediate)
                            }
                        }
                    }, error: nil)
                }
            }
            result(true)

        default:
            result(FlutterMethodNotImplemented)
        }
    }
}
