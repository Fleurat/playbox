import Foundation

/// The swipe feed's path through the library.
///
/// `reel` is the linear trail actually visited: swiping up walks forward, swiping
/// down walks back through exactly what you already saw — even in shuffle mode,
/// where the next pick is random but, once drawn, becomes a fixed part of history.
@MainActor
final class ShortsFeed: ObservableObject {
    @Published private(set) var reel: [Video] = []
    @Published private(set) var cursor: Int = 0
    @Published var isShuffling = true { didSet { bag.removeAll() } }
    @Published var isMuted = false

    private var pool: [Video] = []
    private var bag: [Video] = []
    private let trailLimit = 60

    var current: Video? { reel.indices.contains(cursor) ? reel[cursor] : nil }
    var previous: Video? { cursor > 0 ? reel[cursor - 1] : nil }
    var next: Video? { reel.indices.contains(cursor + 1) ? reel[cursor + 1] : nil }
    var isEmpty: Bool { pool.isEmpty }

    /// Re-seeds from the library. Keeps the current video on screen if it survived.
    func refresh(with videos: [Video]) {
        pool = videos
        guard !pool.isEmpty else {
            reel = []
            cursor = 0
            return
        }

        let ids = Set(pool.map(\.id))
        let keep = current.flatMap { ids.contains($0.id) ? $0 : nil }

        // Drop anything deleted from the trail, then rebuild around the current video.
        reel = reel.filter { ids.contains($0.id) }
        if let keep, let index = reel.firstIndex(where: { $0.id == keep.id }) {
            cursor = index
        } else {
            reel = [isShuffling ? (pool.randomElement() ?? pool[0]) : pool[0]]
            cursor = 0
        }
        bag.removeAll()
        ensureNext()
    }

    func advance() {
        guard next != nil else { return }
        cursor += 1
        ensureNext()
        trim()
    }

    func goBack() {
        guard cursor > 0 else { return }
        cursor -= 1
    }

    /// "Surprise me": swaps whatever was queued for a fresh random pick, leaving
    /// the caller to animate into it exactly as it animates a normal swipe.
    func stageRandomNext() {
        guard pool.count > 1 else { return }
        if reel.count > cursor + 1 {
            reel.removeSubrange((cursor + 1)...)
        }
        if let pick = drawRandom() { reel.append(pick) }
    }

    func jump(to video: Video) {
        reel.removeSubrange(min(cursor + 1, reel.count)...)
        reel.append(video)
        cursor = reel.count - 1
        ensureNext()
        trim()
    }

    // MARK: - Picking

    /// Guarantees `next` is materialised so the page below is rendered and preloaded
    /// before the finger even moves.
    private func ensureNext() {
        guard !pool.isEmpty, next == nil else { return }
        if let pick = isShuffling ? drawRandom() : sequentialNext() {
            reel.append(pick)
        }
    }

    private func sequentialNext() -> Video? {
        guard let current, let index = pool.firstIndex(where: { $0.id == current.id }) else { return pool.first }
        return pool[(index + 1) % pool.count]
    }

    /// Bag-shuffle: nothing repeats until the whole library has been seen once.
    private func drawRandom() -> Video? {
        if bag.isEmpty {
            bag = pool.filter { $0.id != current?.id }.shuffled()
            if bag.isEmpty { bag = pool.shuffled() }
        }
        return bag.popLast()
    }

    private func trim() {
        guard reel.count > trailLimit, cursor > 20 else { return }
        let drop = cursor - 20
        reel.removeFirst(drop)
        cursor -= drop
    }
}
