import Flutter
import UIKit

class SceneDelegate: FlutterSceneDelegate {
    override func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        super.scene(scene, openURLContexts: URLContexts)
        if let url = URLContexts.first?.url {
            AppDelegate.handleIncomingUrl(url)
        }
    }
}
