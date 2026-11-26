// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import ImageIO
import UniformTypeIdentifiers
import WorkeeperKit

final class ExerciseImageFiles {
    let directory: URL
    private let files = FileManager.default
    private let replace: (Data, URL) throws -> Void
    private var staging: URL { directory.appendingPathComponent(".tmp", isDirectory: true) }

    init(support: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0],
         replace: @escaping (Data, URL) throws -> Void = { try $0.write(to: $1, options: .atomic) }) {
        self.replace = replace
        directory = support.appendingPathComponent("WorkeeperMedia/exercise_images", isDirectory: true)
    }

    func temporaryURL() throws -> URL {
        try files.createDirectory(at: staging, withIntermediateDirectories: true)
        return staging.appendingPathComponent(UUID().uuidString)
    }

    func ownsTemp(_ url: URL) -> Bool {
        UUID(uuidString: url.lastPathComponent) != nil &&
            url.deletingLastPathComponent().standardizedFileURL.path == staging.standardizedFileURL.path &&
            url.resolvingSymlinksInPath().path == staging.resolvingSymlinksInPath().appendingPathComponent(url.lastPathComponent).path
    }

    func resolved(_ path: String) -> URL? {
        let url = URL(fileURLWithPath: path)
        guard path.hasPrefix("/"), url.pathExtension == "jpg",
              UUID(uuidString: url.deletingPathExtension().lastPathComponent) != nil else { return nil }
        let parent = url.deletingLastPathComponent()
        if parent.path != directory.path {
            let parts = parent.pathComponents
            guard Array(parts.suffix(4)) == ["Library", "Application Support", "WorkeeperMedia", "exercise_images"],
                  parts.count >= 6, parts[parts.count - 6] == "Application",
                  UUID(uuidString: parts[parts.count - 5]) != nil else { return nil }
        }
        let target = directory.appendingPathComponent(url.lastPathComponent)
        guard target.resolvingSymlinksInPath().path == directory.resolvingSymlinksInPath().appendingPathComponent(url.lastPathComponent).path
        else { return nil }
        return target
    }

    func save(source: String, uuid: String, maxEdge: Int32, quality: Int32) -> AppleImageWrite {
        let sourceURL = URL(fileURLWithPath: source)
        guard UUID(uuidString: uuid) != nil, ownsTemp(sourceURL),
              let input = CGImageSourceCreateWithURL(sourceURL as CFURL, nil) else {
            return AppleImageWrite(path: nil, outOfSpace: false, unreadable: true)
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxEdge
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(input, 0, options as CFDictionary) else {
            return AppleImageWrite(path: nil, outOfSpace: false, unreadable: true)
        }
        let encoded = NSMutableData()
        guard let jpeg = CGImageDestinationCreateWithData(encoded, UTType.jpeg.identifier as CFString, 1, nil) else {
            return AppleImageWrite(path: nil, outOfSpace: false, unreadable: false)
        }
        CGImageDestinationAddImage(jpeg, image, [kCGImageDestinationLossyCompressionQuality: Double(quality) / 100] as CFDictionary)
        guard CGImageDestinationFinalize(jpeg) else {
            return AppleImageWrite(path: nil, outOfSpace: false, unreadable: false)
        }
        // Room still points at the previous image until it accepts this edit.
        let target = directory.appendingPathComponent(UUID().uuidString + ".jpg")
        do {
            try replace(encoded as Data, target)
            return AppleImageWrite(path: target.path, outOfSpace: false, unreadable: false)
        } catch {
            let e = error as NSError
            return AppleImageWrite(path: nil, outOfSpace: e.domain == NSCocoaErrorDomain && e.code == NSFileWriteOutOfSpaceError, unreadable: false)
        }
    }

    func cleanTemps() {
        guard let contents = try? files.contentsOfDirectory(at: staging, includingPropertiesForKeys: nil) else { return }
        for file in contents where ownsTemp(file) {
            if (try? file.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true {
                try? files.removeItem(at: file)
            }
        }
    }
}
