// SPDX-License-Identifier: GPL-3.0-only
import UIKit
import XCTest
import WorkeeperKit
@testable import Workeeper

final class HostWindowTests: XCTestCase {
    @MainActor
    func testSharedRootIsAttachedToKeyWindow() async throws {
        func keyWindow() -> UIWindow? {
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                .first { $0.activationState == .foregroundActive }?.keyWindow
        }
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            MainActor.assumeIsolated { keyWindow()?.rootViewController?.viewIfLoaded?.window != nil }
        }, object: nil)
        await fulfillment(of: [ready], timeout: 20)
        let window = try XCTUnwrap(keyWindow())
        let scene = try XCTUnwrap(window.windowScene?.delegate as? SceneDelegate)
        let controller = try XCTUnwrap(scene.sharedController)
        XCTAssertTrue(window === scene.window && window.isKeyWindow && !window.isHidden)
        XCTAssertTrue(window.rootViewController === controller)
        XCTAssertTrue(controller.view.window === window && controller.view.isDescendant(of: window))
        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertGreaterThan(controller.view.bounds.height, 0)
        XCTAssertTrue(NSStringFromClass(type(of: controller)).contains("ComposeHostingViewController"),
                      "Expected the production Compose hosting controller")
    }
}

extension HostWindowTests {
    func testInstalledSharedAndFeatureResources() throws {
        let root = try XCTUnwrap(Bundle.main.resourceURL)
            .appendingPathComponent("compose-resources/composeResources")
        func asset(_ suffix: String) -> URL {
            root.appendingPathComponent("io.github.stslex.workeeper." + suffix)
        }
        for module in ["app.common", "feature.home", "core.ui.kit"] {
            let strings = try Data(contentsOf: asset(module + ".resources/values/strings.commonMain.cvr"))
            XCTAssertGreaterThan(strings.count, 0, module)
        }
        let font = try XCTUnwrap(CGDataProvider(url: asset("core.ui.design.resources/font/ibm_plex_sans_regular.ttf") as CFURL))
        let face = try XCTUnwrap(CGFont(font))
        XCTAssertEqual(face.postScriptName as String?, "IBMPlexSans")
        let mark = asset("app.ios.resources/drawable/workeeper_mark.png")
        XCTAssertNotNil(UIImage(contentsOfFile: mark.path)?.cgImage)
    }

    func testImageReplacementAndSandboxRelocation() throws {
        let support = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: support) }
        let files = ExerciseImageFiles(support: support)
        let source = try files.temporaryURL()
        let uuid = UUID().uuidString
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let original = UIGraphicsImageRenderer(size: CGSize(width: 2400, height: 1200), format: format).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1200, height: 1200))
            UIColor.blue.setFill()
            context.fill(CGRect(x: 1200, y: 0, width: 1200, height: 1200))
        }
        let rotated = UIImage(cgImage: try XCTUnwrap(original.cgImage), scale: 1, orientation: .right)
        try XCTUnwrap(rotated.jpegData(compressionQuality: 1)).write(to: source)
        let path = try XCTUnwrap(files.save(source: source.path, uuid: uuid, maxEdge: 1280, quality: 85).path)
        let saved = URL(fileURLWithPath: path)
        let bytes = try Data(contentsOf: saved)
        XCTAssertEqual(bytes.prefix(2), Data([0xff, 0xd8]))
        let image = try XCTUnwrap(UIImage(data: bytes)?.cgImage)
        XCTAssertEqual(image.width, 640)
        XCTAssertEqual(image.height, 1280)
        let old = "/private/var/mobile/Containers/Data/Application/\(UUID())/Library/Application Support/WorkeeperMedia/exercise_images/\(uuid).jpg"
        XCTAssertEqual(files.resolved(old)?.standardizedFileURL, saved.standardizedFileURL)
        XCTAssertNil(files.resolved("/tmp/\(uuid).jpg"))
        let fullDisk = ExerciseImageFiles(support: support) { _, _ in throw CocoaError(.fileWriteOutOfSpace) }
        XCTAssertTrue(fullDisk.save(source: source.path, uuid: uuid, maxEdge: 1280, quality: 85).outOfSpace)
        XCTAssertEqual(try Data(contentsOf: saved), bytes)
        try Data([0, 1, 2]).write(to: source)
        XCTAssertTrue(files.save(source: source.path, uuid: uuid, maxEdge: 1280, quality: 85).unreadable)
        XCTAssertEqual(try Data(contentsOf: saved), bytes)
        files.cleanTemps()
        XCTAssertFalse(FileManager.default.fileExists(atPath: source.path))
        XCTAssertEqual(try Data(contentsOf: saved), bytes)
    }
}

private final class MediaOutcome: NSObject, AppleMediaRequest {
    var outcomes: [String] = []

    private func record(_ value: String) {
        XCTAssertTrue(Thread.isMainThread)
        outcomes.append(value)
    }

    func picked(path: String) { record(path) }
    func cancelled() { record("cancelled") }
    func permissionResult(granted: Bool) { record(granted ? "granted" : "denied") }
}

extension HostWindowTests {
    @MainActor
    func testUnavailableCameraAndRetiredPresenterFinishOnce() throws {
        let app = try XCTUnwrap(UIApplication.shared.delegate as? AppDelegate)
        let root = try XCTUnwrap(app.media.presenter)
        var checks = 0
        let media = AppleMedia {
            checks += 1
            return false
        }
        media.presenter = root
        let unavailable = MediaOutcome()
        media.camera(target: media.capturePath(), request: unavailable)
        XCTAssertEqual(checks, 1)
        XCTAssertEqual(unavailable.outcomes, ["cancelled"])

        let active = MediaOutcome()
        media.gallery(request: active)
        media.gallery(request: active)
        XCTAssertTrue(active.outcomes.isEmpty)
        let competing = MediaOutcome()
        media.gallery(request: competing)
        XCTAssertEqual(competing.outcomes, ["cancelled"])
        media.presenter = nil
        media.retire(request: active)
        XCTAssertEqual(active.outcomes, ["cancelled"])

        let detached = AppleMedia()
        let camera = MediaOutcome()
        detached.permission(request: camera)
        XCTAssertEqual(camera.outcomes, ["denied"])
        let gallery = MediaOutcome()
        detached.gallery(request: gallery)
        XCTAssertEqual(gallery.outcomes, ["cancelled"])
    }
}
