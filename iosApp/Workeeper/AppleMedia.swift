// SPDX-License-Identifier: GPL-3.0-only
import AVFoundation
import PhotosUI
import UIKit
import UniformTypeIdentifiers
import WorkeeperKit

final class AppleMedia: NSObject, AppleMediaServices {
    weak var presenter: UIViewController? {
        didSet {
            if oldValue !== presenter, let pending {
                pending.controller?.dismiss(animated: false)
                pending.cancel()
            }
        }
    }
    private let files = ExerciseImageFiles()
    private var pending: Acquisition?
    private let cameraAvailable: () -> Bool

    private let requestVideoAccess: (@escaping (Bool) -> Void) -> Void

    init(requestVideoAccess: @escaping (@escaping (Bool) -> Void) -> Void = { reply in
             AVCaptureDevice.requestAccess(for: .video) { reply($0) }
         }, cameraAvailable: @escaping () -> Bool = { UIImagePickerController.isSourceTypeAvailable(.camera) }) {
        self.requestVideoAccess = requestVideoAccess
        self.cameraAvailable = cameraAvailable
        super.init()
    }

    func cameraGranted() -> Bool { AVCaptureDevice.authorizationStatus(for: .video) == .authorized }

    func save(source: String, uuid: String, maxEdge: Int32, quality: Int32) -> AppleImageWrite {
        files.save(source: source, uuid: uuid, maxEdge: maxEdge, quality: quality)
    }

    func capturePath() -> String { (try? files.temporaryURL().path) ?? "" }
    func resolve(path: String) -> String? { files.resolved(path)?.path }
    func delete(path: String) -> Bool {
        guard let url = files.resolved(path) else { return false }
        do {
            try FileManager.default.removeItem(at: url)
            return true
        } catch { return false }
    }

    func modified(path: String) -> Int64 {
        guard let url = files.resolved(path),
              let date = try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate else { return 0 }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    func cleanTemps() { files.cleanTemps() }

    private func begin(_ request: AppleMediaRequest, onCancel: @escaping (AppleMediaRequest) -> Void = { $0.cancelled() }) -> Acquisition? {
        dispatchPrecondition(condition: .onQueue(.main))
        if let pending, pending.request === request { return nil }
        guard pending == nil, let root = presenter, root.viewIfLoaded?.window?.windowScene?.activationState == .foregroundActive,
              root.presentedViewController == nil else {
            onCancel(request)
            return nil
        }
        let session = Acquisition(owner: self, request: request, onCancel: onCancel)
        pending = session
        return session
    }

    func gallery(request: AppleMediaRequest) {
        guard let session = begin(request) else { return }
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = session
        session.present(picker)
    }

    func camera(target: String, request: AppleMediaRequest) {
        guard let session = begin(request) else { return }
        let url = URL(fileURLWithPath: target)
        guard files.ownsTemp(url), cameraAvailable(), cameraGranted() else {
            session.finish { $0.cancelled() }
            return
        }
        let camera = UIImagePickerController()
        camera.sourceType = .camera
        camera.mediaTypes = [UTType.image.identifier]
        camera.delegate = session
        session.target = url
        session.present(camera)
    }

    func permission(request: AppleMediaRequest) {
        guard let session = begin(request, onCancel: { $0.permissionResult(granted: false) }) else { return }
        requestVideoAccess { granted in
            DispatchQueue.main.async { session.finish { $0.permissionResult(granted: granted) } }
        }
    }

    func settings() {
        guard presenter?.viewIfLoaded?.window?.windowScene?.activationState == .foregroundActive,
              let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func retire(request: AppleMediaRequest) {
        guard let pending, pending.request === request else { return }
        self.pending = nil
        pending.controller?.dismiss(animated: false)
    }

    private final class Acquisition: NSObject, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate,
                                     UINavigationControllerDelegate, UIAdaptivePresentationControllerDelegate {
        weak var owner: AppleMedia?
        let request: AppleMediaRequest
        weak var controller: UIViewController?
        var target: URL?

        private let onCancel: (AppleMediaRequest) -> Void

        init(owner: AppleMedia, request: AppleMediaRequest, onCancel: @escaping (AppleMediaRequest) -> Void) {
            self.onCancel = onCancel
            self.owner = owner
            self.request = request
        }

        func cancel() { finish(onCancel) }

        func present(_ controller: UIViewController) {
            self.controller = controller
            controller.presentationController?.delegate = self
            owner?.presenter?.present(controller, animated: true)
            controller.presentationController?.delegate = self
        }

        func finish(_ deliver: @escaping (AppleMediaRequest) -> Void) {
            guard let owner, owner.pending === self else { return }
            owner.pending = nil
            deliver(request)
        }

        func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
            finish { $0.cancelled() }
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard let files = owner?.files, let provider = results.first?.itemProvider else {
                finish { $0.cancelled() }
                return
            }
            provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                let staged = url.flatMap { source -> URL? in
                    do {
                        let destination = try files.temporaryURL()
                        try FileManager.default.copyItem(at: source, to: destination)
                        return destination
                    } catch { return nil }
                }
                DispatchQueue.main.async {
                    self.finish { request in
                        if let staged { request.picked(path: staged.path) } else { request.cancelled() }
                    }
                }
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
            finish { $0.cancelled() }
        }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            picker.dismiss(animated: true)
            guard let image = info[.originalImage] as? UIImage, let target else {
                finish { $0.cancelled() }
                return
            }
            DispatchQueue.global(qos: .userInitiated).async {
                let saved: Bool
                if let bytes = image.jpegData(compressionQuality: 1) {
                    saved = (try? bytes.write(to: target, options: .atomic)) != nil
                } else { saved = false }
                DispatchQueue.main.async {
                    self.finish { request in
                        if saved { request.picked(path: target.path) } else { request.cancelled() }
                    }
                }
            }
        }
    }
}
