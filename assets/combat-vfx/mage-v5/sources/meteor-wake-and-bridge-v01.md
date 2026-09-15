# Meteor wake and in-between drawings v01

Built-in imagegen. **FIX-FIRST**, not complete Mage art approval.

## Files and actual outputs

- `meteor-wake-v01.png`: 887×1774 RGB. Generated `exec-b1a14c8f-e4bd-4d15-8b1f-13cb640f9b24.png`.
  SHA256 `cfc71a0f03fb97b3a34cd43717edbabeda24fd711866583961f4f0aa111faf89`.
- `meteor-impact-bridge-v01.png`: 1254×1254 RGB. Generated `exec-52d4cb3d-39d3-4cd1-b19d-7bda6be21c05.png`.
  SHA256 `a05490861ccdb5b9d938eb96e55a65ebcff28660a547b548872cbd9ae60474a1`.
- Both copied unchanged to the RP, using underscore names. No raster editing/resizing.
- Wake is an opaque UV material as requested. It is not a physical 32×64 PNG and is not strictly limited to 7 colours.
- Bridge did not meet requested RGBA transparency. The checkerboard is baked RGB, so it is never mapped to a full quad. The existing native geometry mask places faces only where the warm foreground is sampled.
- Source drawings 0–3 are used at impact frames 1–4, between the first flash and the original separated flame pieces. Native source/UV tests explicitly cover each cell.

## Reference evidence

R12 Wynncraft Meteor, `https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s`.
The 155.553961s full-size saved browser frame shows dark dense burning matter, broad pale heat and vertical orange marks. 155.70s shows a burning column overlapping first contact, followed by the raised opening flame front at 155.84s.
The bridge input is our prior original atlas, whose art was informed by R12 and R09. No author screenshot is shipped.

## Exact wake prompt

Use case: stylized-concept. Asset type: an original opaque Minecraft VFX texture for the SIDE of a long burning meteor wake. One portrait 1:2 texture, fills every edge, no margins, no text. Reference image is ONLY for the 155.55s falling tail: the dense dark-red/charcoal column, long vertical orange flame streaks, and broad pale-yellow hot band. Ignore all terrain, UI, words, explosion and other frames. Draw a flat UV material, not a 3D object or scene. Coarse clean hand-painted pixel art, logical 32 pixels wide and 64 pixels tall, solid rectangular pixel clusters, strong long vertical shapes, a restrained 7-colour palette of cream, pale yellow, orange, rusty red, plum, charcoal and near-black. No smooth gradients or soft light. The bottom third is an uneven broad hot cream/peach band with ragged ascending tongues; long uneven orange seams rise through dark red and charcoal matter over the upper two thirds, becoming darker toward the top. The burning matter is dense, the flame is not thin isolated strands. No holes, transparency, checkerboard, shadows, lens glow, bloom, round stones, crystals, decorative runes or concentric shapes. The texture is to be wrapped onto a sculpted coarse native-game trail, so there must be NO painted outer silhouette, bevel, side faces or perspective. Preserve the reference's broad value groups and vertical motion feeling, not its exact pixels.

## Exact bridge prompt

Use case: stylized-concept. Asset type: 4-frame IN-BETWEEN animation atlas for an original Minecraft meteor impact. Input image is a style AND endpoint reference, not a sheet to duplicate in full. Use ONLY its TOP ROW SECOND CELL (two rising flame wings) and TOP ROW THIRD CELL (arched explosion). Generate one square sheet in a strict 2-column by 2-row grid, four equal square cells, no labels, no divider lines, no UI. Actual transparent RGBA background, no checkerboard. Maintain the original's hard-edged coarse pixel-art colour masses: cream, yellow, orange, dark orange. Each cell shares the exact same scale, orthographic frontal camera and ground anchor, centred x=50%, baseline y=90%. Frame 1 upper left: faithfully reproduce the rising wing shape from the reference second cell. Frame 4 lower right: faithfully reproduce the arch shape from its third cell. Frame 2 upper right and frame 3 lower left MUST connect those endpoints by moving and reshaping the SAME primary flame masses, not by suddenly replacing pointed flames with unrelated round blobs. Track the dominant left wing and taller right wing through all four cells: their hot tips rise, spread and roll inward; the central pale body lifts and thins into a membrane; a small hole opens from below and gradually enlarges into the arch's opening. Left and right attachments to ground remain in the same places. Do not spawn new detached circles on top; top flame lobes must grow from existing tips and tear off only near frame 4. Maximum visible displacement of a tracked major lobe between adjacent frames roughly one-sixth of a cell width. Preserve broad solid areas and irregular crisp pixel edges, not smooth flames or thin ornamental curls. No photorealistic glow, blur, gradients, rocks, smoke background or extra particles outside the existing burst envelope. This is the missing in-between drawing sequence, not four different explosions.

## Consumer

`scripts/build_mage_meteor.py`: `burning_wake()` wraps the wake on an irregular native volume; `contact_wake()` carries torn heat into the first two impact frames. `impact_drawing()` selects the in-between drawing, then existing connected-piece decay follows. No damage/CD/MP/input changes.

## Known limitation

The new intermediate hole opening is visible, but the largest pointed-to-rounded flame transition still needs art review. The contact residue is short native strips, not a simulated fluid. Neither the generated sheet nor valid JSON proves reference-level motion.
