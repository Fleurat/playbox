import AVFoundation
import CoreTransferable
import Foundation
import PhotosUI
import UniformTypeIdentifiers

/// A movie pulled out of the photo picker. `loadTransferable` hands us a file
/// that is deleted as soon as the closure returns, so we copy it immediately.
struct PickedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let staged = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension(received.file.pathExtension.isEmpty ? "mov" : received.file.pathExtension)
            try? FileManager.default.removeItem(at: staged)
            try FileManager.default.copyItem(at: received.file, to: staged)
            return PickedMovie(url: staged)
        }
    }
}

@MainActor
final class VideoImporter: ObservableObject {
    @Published private(set) var isImporting = false
    @Published private(set) var progress: Double = 0
    @Published private(set) var statusText = ""
    @Published var lastError: String?

    private unowned let library: LibraryStore

    init(library: LibraryStore) {
        self.library = library
    }

    // MARK: - Photos

    func importFromPhotos(_ items: [PhotosPickerItem]) async {
        guard !items.isEmpty else { return }
        begin(count: items.count)
        defer { finish() }

        for (offset, item) in items.enumerated() {
            statusText = "Importing \(offset + 1) of \(items.count)"
            do {
                guard let movie = try await item.loadTransferable(type: PickedMovie.self) else {
                    lastError = "One item could not be read from Photos."
                    continue
                }
                try await ingest(movie.url, moving: true, suggestedName: nil)
            } catch {
                lastError = error.localizedDescription
            }
            progress = Double(offset + 1) / Double(items.count)
        }
    }

    // MARK: - Files

    func importFromFiles(_ result: Result<[URL], Error>) async {
        switch result {
        case .failure(let error):
            lastError = error.localizedDescription
        case .success(let urls):
            guard !urls.isEmpty else { return }
            begin(count: urls.count)
            defer { finish() }

            for (offset, url) in urls.enumerated() {
                statusText = "Copying \(offset + 1) of \(urls.count)"
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                do {
                    try await ingest(url, moving: false, suggestedName: url.lastPathComponent)
                } catch {
                    lastError = error.localizedDescription
                }
                progress = Double(offset + 1) / Double(urls.count)
            }
        }
    }

    // MARK: - Shared

    /// Copies (or moves) a file into the library folder and registers it.
    private func ingest(_ source: URL, moving: Bool, suggestedName: String?) async throws {
        let ext = source.pathExtension.isEmpty ? "mov" : source.pathExtension.lowercased()
        let base = suggestedName.map { ($0 as NSString).deletingPathExtension }
            ?? "Video \(Self.stampFormatter.string(from: Date()))"
        let fileName = uniqueName(base: base, ext: ext)
        let destination = LibraryPaths.videos.appendingPathComponent(fileName)

        LibraryPaths.prepare()
        if moving {
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: source, to: destination)
        } else {
            try FileManager.default.copyItem(at: source, to: destination)
        }

        var video = Video(fileName: fileName, title: LibraryStore.prettyTitle(from: fileName))
        let attributes = try? FileManager.default.attributesOfItem(atPath: destination.path)
        video.fileSize = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        await LibraryStore.probe(&video)
        library.add(video)

        // Warm the poster so the new card is not a grey box.
        Task { _ = await ThumbnailStore.shared.poster(for: video) }
    }

    private func uniqueName(base: String, ext: String) -> String {
        let cleaned = base
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let stem = cleaned.isEmpty ? "Video" : cleaned

        var candidate = "\(stem).\(ext)"
        var counter = 2
        while FileManager.default.fileExists(atPath: LibraryPaths.videos.appendingPathComponent(candidate).path) {
            candidate = "\(stem) \(counter).\(ext)"
            counter += 1
        }
        return candidate
    }

    private func begin(count: Int) {
        isImporting = true
        progress = 0
        statusText = count == 1 ? "Importing…" : "Importing \(count) videos…"
        lastError = nil
    }

    private func finish() {
        isImporting = false
        progress = 0
        statusText = ""
    }

    private static let stampFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH-mm-ss"
        return formatter
    }()
}
