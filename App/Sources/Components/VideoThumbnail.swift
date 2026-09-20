import SwiftUI
import UIKit

/// Poster frame with the duration badge and the watched-progress bar.
struct VideoThumbnail: View {
    let video: Video
    var aspect: CGFloat = 16.0 / 9.0
    var cornerRadius: CGFloat = 12
    var showsDuration = true
    var showsProgress = true

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Rectangle().fill(Theme.surface)

            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .transition(.opacity)
            } else {
                Image(systemName: "film")
                    .font(.system(size: 22))
                    .foregroundStyle(Theme.secondaryText.opacity(0.5))
            }
        }
        .aspectRatio(aspect, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipped()
        .overlay(alignment: .bottomTrailing) {
            if showsDuration, video.duration > 0 {
                Text(Format.duration(video.duration))
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.white)
                    .pillBackground()
                    .padding(6)
            }
        }
        .overlay(alignment: .bottom) {
            if showsProgress, video.watchProgress > 0.01 {
                WatchProgressBar(progress: video.watchProgress)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .task(id: video.id) {
            if let cached = ThumbnailStore.shared.cached(video) {
                image = cached
                return
            }
            let loaded = await ThumbnailStore.shared.poster(for: video)
            withAnimation(.easeOut(duration: 0.2)) { image = loaded }
        }
    }
}
