import SwiftUI

@main
struct PlayboxApp: App {
    @StateObject private var library: LibraryStore
    @StateObject private var playback: PlaybackController
    @StateObject private var importer: VideoImporter
    @Environment(\.scenePhase) private var scenePhase

    @MainActor
    init() {
        let store = LibraryStore()
        _library = StateObject(wrappedValue: store)
        _playback = StateObject(wrappedValue: PlaybackController(library: store))
        _importer = StateObject(wrappedValue: VideoImporter(library: store))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(library)
                .environmentObject(playback)
                .environmentObject(importer)
                .environmentObject(ThumbnailStore.shared)
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
                .task { await library.rescan() }
                .onOpenURL { url in
                    // "Open with Playbox" from Files / AirDrop.
                    Task {
                        await importer.importFromFiles(.success([url]))
                    }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { library.saveNow() }
        }
    }
}
