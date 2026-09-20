import Foundation

enum Format {
    /// 0:07, 4:32, 1:12:05 — the badge format.
    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds > 0 else { return "0:00" }
        let total = Int(seconds.rounded())
        let (h, m, s) = (total / 3600, (total % 3600) / 60, total % 60)
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    /// Time remaining, as the player shows it when you tap the right-hand timecode.
    static func remaining(_ current: Double, _ total: Double) -> String {
        "-" + duration(max(0, total - current))
    }

    static func plays(_ count: Int) -> String {
        let unit = count == 1 ? "view" : "views"
        return "\(compact(count)) \(unit)"
    }

    static func compact(_ count: Int) -> String {
        switch count {
        case ..<1_000: return "\(count)"
        case ..<1_000_000:
            let value = Double(count) / 1_000
            return value < 10 ? String(format: "%.1fK", value) : String(format: "%.0fK", value)
        default:
            let value = Double(count) / 1_000_000
            return value < 10 ? String(format: "%.1fM", value) : String(format: "%.0fM", value)
        }
    }

    static func fileSize(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }

    private static let relative: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter
    }()

    static func age(_ date: Date) -> String {
        relative.localizedString(for: date, relativeTo: Date())
    }

    /// "1.2K views · 3 days ago"
    static func subtitle(for video: Video) -> String {
        "\(plays(video.plays)) · \(age(video.dateAdded))"
    }
}
