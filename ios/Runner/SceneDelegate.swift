import Flutter
import UIKit

class SceneDelegate: FlutterSceneDelegate {
    // Приложение было закрыто, и его открыла команда: ссылка приходит здесь,
    // а не в openURLContexts. Сохраняем — Dart заберёт её при старте.
    override func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        super.scene(scene, willConnectTo: session, options: connectionOptions)
        if let url = connectionOptions.urlContexts.first?.url {
            AppDelegate.pendingUrl = url.absoluteString
        }
    }

    override func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        super.scene(scene, openURLContexts: URLContexts)
        if let url = URLContexts.first?.url {
            AppDelegate.handleIncomingUrl(url)
        }
    }
}
