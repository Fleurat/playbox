import SwiftUI

enum Theme {
    static let background = Color(red: 0.04, green: 0.04, blue: 0.05)
    static let surface = Color(red: 0.11, green: 0.11, blue: 0.12)
    static let surfaceHigh = Color(red: 0.17, green: 0.17, blue: 0.19)
    static let accent = Color(red: 0.898, green: 0.188, blue: 0.188)
    static let primaryText = Color.white
    static let secondaryText = Color(white: 0.63)
    static let divider = Color(white: 1, opacity: 0.08)

    /// Deterministic avatar tint so a given video always gets the same colour.
    static func avatarColor(for text: String) -> Color {
        let palette: [Color] = [
            Color(red: 0.90, green: 0.29, blue: 0.24),
            Color(red: 0.20, green: 0.60, blue: 0.86),
            Color(red: 0.61, green: 0.35, blue: 0.71),
            Color(red: 0.18, green: 0.72, blue: 0.53),
            Color(red: 0.95, green: 0.61, blue: 0.07),
            Color(red: 0.91, green: 0.30, blue: 0.54),
            Color(red: 0.30, green: 0.40, blue: 0.80)
        ]
        let hash = text.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0xFFFFFF }
        return palette[hash % palette.count]
    }
}

extension View {
    /// Small pill used for durations, badges and chips.
    func pillBackground(_ color: Color = .black.opacity(0.78), radius: CGFloat = 4) -> some View {
        padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(color, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    @ViewBuilder
    func hidden(_ shouldHide: Bool) -> some View {
        if shouldHide { hidden() } else { self }
    }
}

struct Avatar: View {
    let video: Video
    var size: CGFloat = 36

    var body: some View {
        Circle()
            .fill(Theme.avatarColor(for: video.source + video.title))
            .frame(width: size, height: size)
            .overlay {
                Text(video.initial)
                    .font(.system(size: size * 0.44, weight: .semibold))
                    .foregroundStyle(.white)
            }
    }
}

/// The red "already watched to here" bar along the bottom of a thumbnail.
struct WatchProgressBar: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Rectangle().fill(Color.white.opacity(0.35))
                Rectangle()
                    .fill(Theme.accent)
                    .frame(width: geo.size.width * progress)
            }
        }
        .frame(height: 3)
    }
}

struct EmptyStateView: View {
    let symbol: String
    let title: String
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: symbol)
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(Theme.secondaryText)
            Text(title)
                .font(.headline)
                .foregroundStyle(Theme.primaryText)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(Color.white, in: Capsule())
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60)
    }
}
