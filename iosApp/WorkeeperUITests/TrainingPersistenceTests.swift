// SPDX-License-Identifier: GPL-3.0-only
import XCTest

@MainActor
class WorkeeperUITestCase: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    @discardableResult
    func ready(_ element: XCUIElement, _ message: String, file: StaticString = #filePath,
                       line: UInt = #line) -> XCUIElement {
        if !element.waitForExistence(timeout: 15) {
            XCTFail(message + "\n" + app.debugDescription, file: file, line: line)
        }
        return element
    }

    func tap(_ identifier: String) {
        ready(element(identifier), "Missing " + identifier).tap()
    }

    func enter(_ text: String, in identifier: String) {
        tap(identifier)
        app.typeText(text)
        if app.keyboards.firstMatch.exists { app.typeText("\n") }
    }

}

@MainActor
final class TrainingPersistenceTests: WorkeeperUITestCase {
    private func trainings() {
        tap("BottomAppBarItem_TRAININGS")
        ready(element("AllTrainingsFab"), "Trainings did not open")
    }

    private func rows(_ name: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(
            format: "identifier BEGINSWITH %@ AND label == %@", "AllTrainingsItemName_", name))
    }

    func testStartupOpensExpectedSurface() {
        app.launch()
        if ProcessInfo.processInfo.environment["EXPECTED_RECOVERY"] == "YES" {
            ready(app.staticTexts["Workeeper recovery"], "Unsupported database did not open recovery")
            ready(app.buttons["Retry"], "Recovery is missing Retry").tap()
            ready(app.staticTexts["Retry failed. Your workouts are still on this device."], "Recovery retry did not finish safely")
            XCTAssertFalse(element("HomeGraph").exists)
        } else {
            ready(element("HomeGraph"), "Healthy database did not reach Home")
            ready(app.staticTexts["Ready when you are"], "Home feature string missing")
            XCTAssertEqual(element("BottomAppBarItem_TRAININGS").label, "Trainings")
        }
    }

    func testTemplateSurvivesProcessRelaunch() {
        let suffix = String(UUID().uuidString.prefix(8))
        let template = "Training " + suffix
        let exercise = "Exercise " + suffix
        app.launch()
        ready(element("HomeGraph"), "Shared Home missing")
        trainings()
        XCTAssertEqual(rows(template).count, 0)
        tap("BottomAppBarItem_EXERCISES")
        tap("AllExercisesFab")
        enter(exercise, in: "ExerciseEditNameField")
        tap("ExerciseEditSaveButton")
        ready(app.staticTexts[exercise].firstMatch, "Created exercise missing from Exercises")
        trainings()
        tap("AllTrainingsFab")
        enter(template, in: "TrainingEditNameField")
        tap("TrainingEditAddExerciseButton")
        ready(app.staticTexts[exercise].firstMatch, "Created exercise missing from picker").tap()
        tap("ExercisePickerConfirm")
        tap("TrainingEditSaveButton")
        ready(rows(template).firstMatch, "Initial template save missing from Trainings")
        XCTAssertEqual(rows(template).count, 1)
        XCTContext.runActivity(named: "Initial unique template row saved") { _ in }
        app.terminate()
        XCTAssertTrue(app.wait(for: .notRunning, timeout: 15))
        app.launch()
        ready(element("HomeGraph"), "Shared Home missing after relaunch")
        trainings()
        ready(rows(template).firstMatch, "Post-relaunch template missing from Trainings")
        XCTAssertEqual(rows(template).count, 1)
        rows(template).element.tap()
        ready(app.staticTexts[exercise].firstMatch, "Saved exercise missing from reopened template")
    }
}

@MainActor
final class WorkeeperMediaUITests: WorkeeperUITestCase {
    func testDeniedCameraCanOpenAppSettings() {
        app.launch()
        tap("BottomAppBarItem_EXERCISES")
        tap("AllExercisesFab")
        tap("ExerciseDescriptionImage")
        tap("ExerciseImageSourceCamera")
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deny = system.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Don’t Allow", "Don't Allow"])).firstMatch
        if deny.waitForExistence(timeout: 5) { deny.tap() }
        ready(app.staticTexts["Camera permission denied"], "Camera denial missing")
        ready(app.buttons["Open settings"].firstMatch, "App Settings action missing").tap()
        XCTAssertTrue(XCUIApplication(bundleIdentifier: "com.apple.Preferences").wait(for: .runningForeground, timeout: 10))
    }

    func testSettingsHandsPrivacyLinkToSafari() {
        app.launch()
        tap("HomeSettingsButton")
        let privacy = app.staticTexts["Privacy Policy"].firstMatch
        for _ in 0..<4 {
            if privacy.exists && privacy.isHittable { break }
            app.swipeUp()
        }
        ready(privacy, "Privacy policy link missing").tap()
        XCTAssertTrue(XCUIApplication(bundleIdentifier: "com.apple.mobilesafari").wait(for: .runningForeground, timeout: 10))
    }

    func testPickedImageSurvivesRelaunchInPreviewAndViewer() throws {
        let exercise = "Photo " + String(UUID().uuidString.prefix(8))
        app.launch()
        ready(element("HomeGraph"), "Shared Home missing")
        tap("BottomAppBarItem_EXERCISES")
        tap("AllExercisesFab")
        enter(exercise, in: "ExerciseEditNameField")
        tap("ExerciseDescriptionImage")
        tap("ExerciseImageSourceGallery")
        let photos = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Photos")).firstMatch
        ready(photos, "System photo picker did not expose Photos")
        let cancel = app.buttons.matching(NSPredicate(format: "identifier == %@", "Cancel")).firstMatch
        ready(cancel, "System photo picker missing").tap()
        tap("ExerciseDescriptionImage")
        tap("ExerciseImageSourceGallery")
        let photo = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        ready(photo, "Fixture photo missing from system picker")
        photo.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        try assertPhoto(element("ExerciseDescriptionImage"))
        tap("ExerciseEditSaveButton")
        ready(app.staticTexts[exercise].firstMatch, "Saved exercise missing")
        app.terminate()
        XCTAssertTrue(app.wait(for: .notRunning, timeout: 15))
        app.launch()
        ready(element("HomeGraph"), "Shared Home missing after relaunch")
        tap("BottomAppBarItem_EXERCISES")
        ready(app.staticTexts[exercise].firstMatch, "Saved exercise missing on relaunch").tap()
        try assertPhoto(element("ExerciseDescriptionImage"))
        tap("ExerciseDescriptionImage")
        let fullSize = ready(element("ImageViewerImage"), "Full-size exercise image missing")
        XCTAssertEqual(fullSize.label, "Exercise image, full size")
        try assertPhoto(fullSize)
    }

    private func assertPhoto(_ element: XCUIElement) throws {
        ready(element, "Photo region missing")
        let check = NSPredicate { _, _ in
            guard let image = element.screenshot().image.cgImage else { return false }
            var rgba = [UInt8](repeating: 0, count: 16 * 16 * 4)
            let drawn = rgba.withUnsafeMutableBytes { bytes in
                guard let context = CGContext(data: bytes.baseAddress, width: 16, height: 16, bitsPerComponent: 8,
                                              bytesPerRow: 64, space: CGColorSpaceCreateDeviceRGB(),
                                              bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return false }
                context.draw(image, in: CGRect(x: 0, y: 0, width: 16, height: 16))
                return true
            }
            guard drawn else { return false }
            let left = (8 * 16 + 4) * 4
            let right = (8 * 16 + 11) * 4
            return rgba[left] > 180 && rgba[left + 2] < 70 && rgba[right] < 70 && rgba[right + 2] > 180
        }
        let loaded = XCTNSPredicateExpectation(predicate: check, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [loaded], timeout: 12), .completed, "Expected red left and blue right in the loaded image")
    }
}
