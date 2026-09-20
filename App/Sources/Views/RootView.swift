import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

private struct BottomChromeKey: EnvironmentKey {
    static let defaultValue: CGFloat = 0
}

extension EnvironmentValues {
    /// How much of the bottom of the screen the tab bar + mini player occupy,
    /// so scrolling content can clear it.
    var bottomChrome: CGFloat {
        get { self[BottomChromeKey.self] }
        set { self[BottomChromeKey.self] = newValue }
    }
}

struct RootView: View {
    enum Tab: Hashable { case home, shorts, playlists, library }

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @EnvironmentObject private var importer: VideoImporter

    @State private var tab: Tab = .home
    @State private var showingImportChoice = false
    @State private var showingFileImporter = false
    @State private var showingPhotoPicker = false
    @State private var photoSelection: [PhotosPickerItem] = []

    private var chromeHeight: CGFloat {
        TabBar.height + (playback.presentation == .mini ? MiniPlayerBar.height : 0)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.background.ignoresSafeArea()

            Group {
                switch tab {
                case .home: HomeView()
                case .shorts: ShortsView()
                case .playlists: PlaylistsView()
                case .library: LibraryView()
                }
            }
            .environment(\.bottomChrome, chromeHeight)

            VStack(spacing: 0) {
                if playback.presentation == .mini {
                    MiniPlayerBar()
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
                TabBar(selection: $tab, onCreate: { showingImportChoice = true })
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.85), value: playback.presentation)
        }
        .overlay {
            if importer.isImporting { ImportProgressOverlay() }
        }
        .overlay {
            if playback.presentation == .full {
                PlayerScreen()
                    .transition(.move(edge: .bottom))
                    .zIndex(2)
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.86), value: playback.presentation)
        .onChange(of: tab) { _, newValue in
            if newValue == .shorts { playback.suspendForShorts() }
        }
        .confirmationDialog("Add videos", isPresented: $showingImportChoice, titleVisibility: .visible) {
            Button("Import from Photos") { showingPhotoPicker = true }
            Button("Import from Files") { showingFileImporter = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Videos are copied into Playbox and play entirely offline.")
        }
        .photosPicker(isPresented: $showingPhotoPicker,
                      selection: $photoSelection,
                      maxSelectionCount: 25,
                      matching: .videos,
                      photoLibrary: .shared())
        .onChange(of: photoSelection) { _, items in
            guard !items.isEmpty else { return }
            Task {
                await importer.importFromPhotos(items)
                photoSelection = []
            }
        }
        .fileImporter(isPresented: $showingFileImporter,
                      allowedContentTypes: [.movie, .video, .mpeg4Movie, .quickTimeMovie],
                      allowsMultipleSelection: true) { result in
            Task { await importer.importFromFiles(result) }
        }
        .alert("Import problem", isPresented: Binding(
            get: { importer.lastError != nil },
            set: { if !$0 { importer.lastError = nil } }
        )) {
            Button("OK", role: .cancel) { importer.lastError = nil }
        } message: {
            Text(importer.lastError ?? "")
        }
    }

}

struct TabBar: View {
    static let height: CGFloat = 52

    @Binding var selection: RootView.Tab
    var onCreate: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            item(.home, "house", "house.fill", "Home")
            item(.shorts, "play.square.stack", "play.square.stack.fill", "Shorts")

            Button(action: onCreate) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(Theme.primaryText)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)

            item(.playlists, "rectangle.stack", "rectangle.stack.fill", "Playlists")
            item(.library, "person.crop.square", "person.crop.square.fill", "You")
        }
        .frame(height: Self.height)
        .padding(.top, 4)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.divider).frame(height: 0.5)
        }
    }

    private func item(_ tab: RootView.Tab, _ symbol: String, _ filled: String, _ label: String) -> some View {
        Button {
            selection = tab
        } label: {
            VStack(spacing: 3) {
                Image(systemName: selection == tab ? filled : symbol)
                    .font(.system(size: 18))
                Text(label)
                    .font(.system(size: 10, weight: selection == tab ? .semibold : .regular))
            }
            .foregroundStyle(selection == tab ? Theme.primaryText : Theme.secondaryText)
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}

struct ImportProgressOverlay: View {
    @EnvironmentObject private var importer: VideoImporter

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()
            VStack(spacing: 14) {
                ProgressView(value: importer.progress)
                    .progressViewStyle(.linear)
                    .frame(width: 180)
                Text(importer.statusText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
            }
            .padding(26)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }
}
