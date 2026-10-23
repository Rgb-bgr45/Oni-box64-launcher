# ONI Box64 Launcher (starter)

## Changelog
### 0.2.0
- Application id is now `dev.onilauncher.bgr45` (installs separately from 0.1.0)
- Data moved to `Android/data/dev.onilauncher.bgr45/files/`: `games/oni`, `controls`, `home` (game saves), `logs`
- The rootfs stays in internal storage: it needs symlinks, which external app storage does not support
- Game files are optional: the launcher starts without them; "Launch game" only reports what is missing
- New Diagnostics button (device info, folders, runs `box64 -v` if present, writes `logs/diagnostics.txt`)
- Touch editor: tap a control to edit label/action/size/mode or delete it; Add button/joystick/layer toggle/new layer; Layers dialog
- Game output is saved to `logs/last-run.log`

Note: on Android 11+ many file managers cannot open `Android/data`. Connecting the phone to a PC over USB,
`adb pull`, or a file manager with special access (e.g. via Shizuku) usually works.

Android launcher skeleton that runs the **Linux x86_64 build of Oxygen Not Included**
under **box64**. It contains **no game files**: the user imports their own copy.

> Status: early skeleton. It will NOT run the game yet. Not compiled or tested on a device.

## What is in the code
- Import a game folder via the system folder picker (copied into app storage)
- Install an x86_64 rootfs archive (.tar / .tar.gz / .tar.xz) chosen by the user
- Detect the Unity executable (`<Name>` next to `<Name>_Data`), preflight checks, launch under box64 with live log
- **Touch controls**: JSON layouts (buttons, joystick, layer toggles), touchpad-style mouse,
  edit mode (drag controls), import/export of layout files. See `docs/CONTROLS_FORMAT.md`.
  Open it from the "Touch controls" button; events are only logged until a display backend exists.

## Still missing (cannot be produced inside this repo's code alone)
1. **box64 for Android arm64** as `app/src/main/jniLibs/arm64-v8a/libbox64.so`
   (Android 10+ blocks exec from app data, so it lives in the native lib dir). Build it yourself or take a build whose license allows redistribution.
2. **An x86_64 glibc rootfs archive** to install through the app.
3. **Display + GL backend**: an X server (or equivalent) that draws into an Android surface, plus a GL translation layer.
   This is the largest piece. Existing open-source projects solve it; check their licenses before reusing code
   (some are GPL). Once it exists, implement `InputSink` to forward events to it and put `ControlOverlayView` over its surface.
4. Audio, per-device box64 tuning, saving instance settings.

## Build
Open the folder in Android Studio (it creates the Gradle wrapper), sync, run on an arm64 device with Android 8+.

## Upload to GitHub without a terminal
GitHub -> New repository -> "uploading an existing file" -> drag the contents of this folder in -> Commit.

## Legal
Only use game files you own. Do not commit game files or binaries you cannot redistribute.
