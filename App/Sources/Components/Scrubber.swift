import SwiftUI
import UIKit

/// The red progress bar. Grows under the finger while dragging, like the real one.
struct Scrubber: View {
    let progress: Double
    let duration: Double
    var compact = false
    var onBegin: () -> Void = {}
    var onChange: (Double) -> Void = { _ in }
    var onEnd: (Double) -> Void = { _ in }

    @State private var isDragging = false
    @State private var dragProgress: Double = 0

    private var shown: Double { isDragging ? dragProgress : max(0, min(1, progress)) }

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(.white.opacity(0.28))
                    .frame(height: isDragging ? 5 : 3)
                Capsule()
                    .fill(Theme.accent)
                    .frame(width: width * shown, height: isDragging ? 5 : 3)
                Circle()
                    .fill(Theme.accent)
                    .frame(width: isDragging ? 16 : (compact ? 0 : 11),
                           height: isDragging ? 16 : (compact ? 0 : 11))
                    .offset(x: width * shown - (isDragging ? 8 : (compact ? 0 : 5.5)))
            }
            .frame(height: geo.size.height, alignment: .center)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !isDragging {
                            isDragging = true
                            onBegin()
                            UISelectionFeedbackGenerator().selectionChanged()
                        }
                        dragProgress = min(1, max(0, value.location.x / width))
                        onChange(dragProgress * duration)
                    }
                    .onEnded { value in
                        let final = min(1, max(0, value.location.x / width))
                        dragProgress = final
                        isDragging = false
                        onEnd(final * duration)
                    }
            )
            .animation(.easeOut(duration: 0.12), value: isDragging)
        }
        .frame(height: 22)
    }
}

/// Thin non-interactive version drawn under the mini player.
struct ThinProgress: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Rectangle().fill(.white.opacity(0.18))
                Rectangle().fill(Theme.accent).frame(width: geo.size.width * max(0, min(1, progress)))
            }
        }
        .frame(height: 2)
    }
}
