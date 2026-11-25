// SPDX-License-Identifier: GPL-3.0-only
import UIKit
import WorkeeperKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    let media = AppleMedia()
    lazy var host = WorkeeperHost(media: media)
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private(set) weak var sharedController: UIViewController?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene,
              let app = UIApplication.shared.delegate as? AppDelegate else { return }
        let window = UIWindow(windowScene: scene)
        let controller = app.host.makeController()
        app.media.presenter = controller
        sharedController = controller
        window.rootViewController = controller
        self.window = window
        window.makeKeyAndVisible()
    }
}
