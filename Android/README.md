# Playbox for Android

The Android port of Playbox: a local video app with YouTube's shape. Kotlin +
Jetpack Compose + Media3 ExoPlayer. No network, no accounts — it plays what is
already on the phone. The name, icon, colours and code are original.

## The APK

Already built and signed with the standard Android debug key:

```
Android/app/build/outputs/apk/debug/app-debug.apk   (19 MB)
```

A copy is on the Desktop as `Playbox-debug.apk`.

**Installing it:** copy the file to the phone (USB, Drive, email to yourself),
tap it, and allow "install unknown apps" for whichever app you opened it from.
Android will warn you — that is normal for a debug-signed app that did not come
from Play.

With a cable and USB debugging on, this is faster:

```bash
~/Library/Android/sdk/platform-tools/adb install -r ~/Desktop/Playbox-debug.apk
```

## Where the videos come from

Two sources, both scanned on launch:

- **The phone's own video collection** (MediaStore) — camera roll, downloads,
  WhatsApp, screen recordings. Playbox asks for video access on first launch;
  the folder each file sits in becomes its "channel" line.
- **Playbox's own folder** — anything brought in with the **+** button is
  copied to `Android/data/com.example.playbox/files/Videos`. Only these copies
  can be deleted from inside the app.

## Gestures

**Shorts tab**

| Gesture | Result |
| --- | --- |
| Swipe up | Next video (random when shuffle is on) |
| Swipe down | The video you just came from |
| Tap | Pause / resume |
| Double tap | Like |
| Hold | 2× speed while held |
| Shuffle button | Makes "next" a random pick |
| Dice button | Jump straight to a random video |

Shuffle is a bag shuffle: everything plays once before anything repeats, and
swiping down retraces the exact path you took even in random mode.

**Player**

| Gesture | Result |
| --- | --- |
| Tap | Show / hide controls |
| Double tap left / right | Back 10s / forward 10s |
| Double tap centre | Play / pause |
| Hold | 2× speed while held |
| Drag sideways | Scrub |
| Drag down | Shrink to the mini player |
| Drag up | Open the queue |
| Pinch | Fit ⇄ fill |
| Fullscreen button | Rotate to landscape, hide the system bars |
| Back button | Exit fullscreen, then shrink to the mini player |

**In fullscreen** the vertical drags switch to the same grammar as the Shorts
feed, because that is what they are for once the details panel is gone:

| Gesture | Result |
| --- | --- |
| Swipe up | Slide up the picker: choose what plays next |
| Swipe down | The previous video — not a rewind |
| Drag sideways | Scrub (unchanged) |
| Double tap sides | ±10s (unchanged) |

Swiping up opens a panel over the video listing the queue first and then the
rest of the library, so there is always something to choose from. Tap a row to
play it; picking from the queue keeps the queue, picking from the library
rebuilds it around your choice. Close it by dragging the handle down, tapping
the dimmed video above it, or the ✕.

The panel and the fullscreen control bar both carry a shuffle toggle and a dice
button, if you would rather not choose.

Note that swipe-down no longer exits fullscreen — it is the previous video now.
Leave fullscreen with the ⛶ button, the chevron top-left, or the back button.

**Fullscreen** is the ⛶ button at the right of the scrubber row, or the
*Fullscreen* pill under the video. It locks the activity to landscape and hides
the status and navigation bars — swipe from an edge to get them back
temporarily. Rotating the phone by itself does not trigger it; the button does.
Leaving the player always restores portrait and the system bars, so the app
cannot get stranded sideways.

## Known limits — read these

- **I never ran this.** It compiles and packages cleanly, but there is no
  emulator on this Mac, so no screen of it has ever been drawn. Treat the first
  launch as the real test.
- **No background playback.** There is no media service in this build, so audio
  stops when you leave the app and there are no lock-screen controls. This was
  cut deliberately: Android 14's foreground-service rules are the most
  crash-prone part of a first build, and it is worth adding once the app is
  known to run.
- **Scrub previews are missing.** iOS shows a frame above the scrubber while
  dragging; this build shows only the timecode.
- **The first scroll is slow on a big library.** Coil decodes a frame per video
  for thumbnails, then caches them.
- **The screen stays awake while playing.** Held with the View's keep-screen-on
  flag, not a wake lock, so it needs no permission and Android releases it by
  itself if the app is backgrounded or killed. It is on whenever the player is
  playing or the Shorts tab is open, and off while you browse.
- **Codec support is whatever the phone has.** MKV and AVI files are listed and
  will often play, but a container holding a codec the device cannot decode
  shows a black frame.
- **Deleting only works on imported copies.** Removing a MediaStore video needs
  a system consent dialog that is not wired up, so the delete option is hidden
  for those.

## Building it again

```bash
cd Android
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

The toolchain used here: OpenJDK 17 (Homebrew, keg-only), Android SDK 34 +
build-tools 34.0.0 at `~/Library/Android/sdk`, Gradle 8.7 via the wrapper,
AGP 8.5.2, Kotlin 1.9.24. `local.properties` points at the SDK and is not
meant to be shared.

To remove the toolchain again: delete `~/Library/Android/sdk` and run
`brew uninstall openjdk@17`.
