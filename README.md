# Playbox — a local video app with YouTube's shape

A SwiftUI iPhone/iPad app that plays videos stored on the device. No network, no
accounts, no streaming: everything lives in the app's own folder and plays offline.

The layout and interaction model follow YouTube — home feed, a full-screen swipe
feed, a player that shrinks into a docked bar — but the name, icon, colours and
code are original. Nothing from YouTube is reproduced.

## What you need first

Full **Xcode** from the Mac App Store. You currently have only Command Line
Tools, so the project generates but cannot build. After installing:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

## Setup

1. Open `Playbox.xcodeproj`.
2. Select the **Playbox** target → Signing & Capabilities → pick your Team.
3. Change the bundle identifier from `com.example.Playbox` to something unique
   (e.g. `com.yourname.playbox`) in `project.yml`, then re-run `xcodegen generate`.
4. Build and run to your iPhone.

A free Apple ID works, but the app expires after 7 days and must be reinstalled.

## Getting videos in

Three ways, all of which copy the file into the app so playback never depends on
anything else:

- **+ in the tab bar → Photos** — multi-select straight from your camera roll.
- **+ in the tab bar → Files** — anything in iCloud Drive, a USB drive, etc.
- **Files app → On My iPhone → Playbox** — drop files (or whole folders) in
  directly. Pull to refresh on Home and they appear. Folder names become the
  "channel" line under each video.

Supported: mp4, mov, m4v, mpg, mpeg, 3gp, and anything else AVFoundation opens.
(avi/mkv are listed but depend on the codecs inside.)

## Gestures

**Shorts tab — the swipe feed**

| Gesture | Result |
| --- | --- |
| Swipe up | Next video |
| Swipe down | The video you just came from |
| Tap | Pause / resume |
| Double tap | Like |
| Hold | 2× speed while held |
| Shuffle button | Makes "next" a random pick |
| Dice button | Jump straight to a random video |

Shuffle is a bag shuffle: every video plays once before any repeats. Swiping
down always retraces the exact path you took, even in shuffle mode.

**Player**

| Gesture | Result |
| --- | --- |
| Tap | Show / hide controls |
| Double tap left / right | Back 10s / forward 10s |
| Double tap centre | Play / pause |
| Hold | 2× speed while held |
| Drag sideways | Scrub, with a preview frame |
| Drag down | Shrink to the mini player |
| Drag up | Open the queue |
| Pinch | Fit ⇄ fill |
| Swipe up *(landscape)* | Slide up the picker: choose what plays next |
| Swipe down *(landscape)* | The previous video — not a rewind |

**Mini player**: tap to expand, swipe up to expand, swipe down to close.

## What else is in there

- Autoplay through the shelf you tapped from, with shuffle and repeat one/all.
- Resume where you left off, plus the red watched-progress bar on thumbnails.
- Watch history, liked videos, playlists, search.
- Lock screen and Control Centre controls, and background audio.
- Poster frames generated on a background thread and cached to disk.

## Known limits — read these

- **Not App Store material.** It is a personal sideload; there is no content
  pipeline, moderation, or rights handling, because there is no content but yours.
- **Import copies, it doesn't move.** Two copies exist until you delete the
  original from Photos or Files. A 4 GB film costs 8 GB until you do.
- **Photos import strips Photos metadata.** Titles come from the file name, and
  camera-roll exports are named things like `IMG_4312`. Rename via ⋮ → Rename.
- **Format support is whatever AVFoundation has.** MKV and AVI containers often
  hold codecs iOS will not decode; those files import and then show a black frame.
- **Rotation is requested, not forced.** The fullscreen button asks the scene to
  rotate; if the device orientation lock is on, iOS refuses and nothing happens.
- **No PiP.** The mini player is an in-app bar, not AVPictureInPicture, so it
  stops when you leave the app. Audio keeps playing.
- **Thumbnails cost a first pass.** The first scroll through a large library
  decodes a frame per video. They are cached after that.

## Regenerating the project

`project.yml` is the source of truth; `Playbox.xcodeproj` is generated.

```bash
xcodegen generate
```
