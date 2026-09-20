import Foundation

/// Where everything the app owns lives on disk.
enum LibraryPaths {
    static let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    static let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
    static let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]

    /// Files dropped here (Finder / Files.app / AirDrop) are picked up automatically.
    static let videos = documents.appendingPathComponent("Videos", isDirectory: true)
    static let thumbnails = caches.appendingPathComponent("Thumbnails", isDirectory: true)
    static let metadata = support.appendingPathComponent("library.json")

    static let videoExtensions: Set<String> = ["mp4", "mov", "m4v", "mpg", "mpeg", "avi", "mkv", "3gp", "webm"]

    static func prepare() {
        for dir in [videos, thumbnails, support] {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
    }
}

struct Video: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    /// File name relative to `LibraryPaths.videos` — storing a relative path keeps
    /// the library valid across app reinstalls, where the container UUID changes.
    var fileName: String
    var title: String
    var duration: Double = 0
    var fileSize: Int64 = 0
    var width: Int = 0
    var height: Int = 0
    var dateAdded: Date = Date()
    var plays: Int = 0
    var isLiked: Bool = false
    var lastPlayed: Date?
    /// Where playback stopped last time, so a video can resume.
    var resumeTime: Double = 0

    var url: URL { LibraryPaths.videos.appendingPathComponent(fileName) }

    var isPortrait: Bool { height > 0 && height > width }
    /// The Shorts feed pulls from portrait clips first, then anything short.
    var qualifiesAsShort: Bool { isPortrait || (duration > 0 && duration <= 90) }

    var aspectRatio: Double {
        guard width > 0, height > 0 else { return 16.0 / 9.0 }
        return Double(width) / Double(height)
    }

    /// Progress through the video, used for the red bar under thumbnails.
    var watchProgress: Double {
        guard duration > 0, resumeTime > 0 else { return 0 }
        return min(1, resumeTime / duration)
    }

    var isFinished: Bool { duration > 0 && resumeTime >= duration - 2 }

    /// Stand-in for a channel name: the folder the file came from, or the app name.
    var source: String {
        let parts = fileName.split(separator: "/")
        return parts.count > 1 ? String(parts[parts.count - 2]) : "My videos"
    }

    var initial: String { String(title.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased() }
}

struct Playlist: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var videoIDs: [UUID] = []
    var dateCreated: Date = Date()
}

/// On-disk shape of the library. Kept separate from `LibraryStore` so the
/// published properties can change without breaking saved files.
struct LibrarySnapshot: Codable {
    var videos: [Video] = []
    var playlists: [Playlist] = []
    var history: [UUID] = []
}
