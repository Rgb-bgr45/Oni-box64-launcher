# Touch layout format (version 1)

This is the launcher's own format. It is NOT the ZalithLauncher / Pojav layout format
and is not compatible with their files.

```json
{
  "version": 1,
  "name": "My layout",
  "layers": [
    { "id": "main", "visible": true, "controls": [ ... ] }
  ]
}
```

Positions: `x`, `y` are fractions (0..1) of the screen for the control's top-left corner.
Sizes (`w`, `h`, `size`) are in dp. Controls later in a layer draw on top.

## Controls
- `button`: `id`, `label`, `x`, `y`, `w`, `h`, `mode` (`hold` default, or `toggle`), `action`
- `joystick`: `id`, `x`, `y`, `size`, `keys` { `up`, `down`, `left`, `right` } (key names)
- `layerToggle`: `id`, `label`, `x`, `y`, `w`, `h`, `target` (a layer id) - shows/hides that layer

## Actions
- `{ "kind": "key", "value": "SPACE" }`
- `{ "kind": "mouse", "value": "left" | "right" | "middle" }`
- `{ "kind": "scroll", "value": "up" | "down" }` (fires once per press)

## Key names
A-Z, 0-9, F1-F12, SPACE, ESC, SHIFT, CTRL, ALT, TAB, ENTER, BACKSPACE, DELETE, UP, DOWN, LEFT, RIGHT.

## Touchpad behaviour (areas with no control)
Drag moves the cursor, tap = left click, two-finger tap = right click,
press-and-hold then drag = left button held (for drag-selecting areas).

## Notes
- Control and layer ids must be unique; invalid files are rejected with a message.
- The default layout's ONI keys are guesses based on common bindings. ONI lets you rebind keys, so edit them.

## Editing in the app
Controls screen -> Edit: drag a control to move it, tap it to change label, action, size or delete it.
Add creates a Button, Joystick, Layer toggle or a new layer. New controls go to the topmost visible layer.
Layers lets you show/hide layers while editing; the visibility you leave is saved as the starting state.
Layout files are in the app data folder: Android/data/dev.onilauncher.bgr45/files/controls/
