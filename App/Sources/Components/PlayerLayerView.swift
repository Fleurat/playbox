import AVFoundation
import SwiftUI
import UIKit

/// AVPlayerLayer wrapped for SwiftUI. VideoPlayer would bring Apple's own
/// controls along with it, and this app draws its own.
struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    var gravity: AVLayerVideoGravity = .resizeAspect

    func makeUIView(context: Context) -> PlayerLayerHost {
        let view = PlayerLayerHost()
        view.backgroundColor = .black
        view.playerLayer.player = player
        view.playerLayer.videoGravity = gravity
        return view
    }

    func updateUIView(_ view: PlayerLayerHost, context: Context) {
        if view.playerLayer.player !== player {
            view.playerLayer.player = player
        }
        if view.playerLayer.videoGravity != gravity {
            view.playerLayer.videoGravity = gravity
        }
    }
}

final class PlayerLayerHost: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
