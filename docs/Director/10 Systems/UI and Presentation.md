---
type: system
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
approved_by:
approved_at:
priority: HOLD
design_clarity: green
backend_complexity: yellow
minecraft_limitation: yellow
ui_dependency: green
content_requirement: yellow
testing: green
user_decision_needed: false
last_reviewed: 2026-08-29
---

# UI and Presentation

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## Approved Art Direction

- Minecraft由来のpixel感 + modern MMO。
- Soft neutral charcoal/gray glass、worldを隠しすぎない。
- Ivoryは選択/重要actionの限定accent。
- Main panelはsoft corner、Item slot/iconはsquare/pixel寄り。
- Minecraft standard fontを基準。
- muted red / blue-teal / amber / warm off-white。
- generic black-gold fantasy、neon、AI gradient、Web app card wallをreject。

Source: [UI Art Direction #114](https://github.com/retardedgyai/Projects-V2/issues/114) — User approved 2026-08-26。

## 実装済み

- Combat HUD: HP/Mana/Skills/Cooldown/Ult state等。
- Inventory Character Screen。
- Navigation rail、equipment slots、inventory grid、stats、hotbar integration。
- Authoritative Equipment presentation stats。
- Approved UI asset kitとstat icons。
- HUD layout designer / VFX slash editor等のdevelopment tools。
- PixelLab generation/adoption pipeline。

## 現在の方針

Issue #127ではUI作業を凍結。Fire/Iceは既存particle/sound/health feedbackだけで読ませる。

## 未決定 / HOLD

- Equipment Tooltip #105。
- 追加menu icon batch #126。
- Passive Tree final layout/Keystone。
- Dedicated Client optional範囲。
- Crafting/Quest/Party screen。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟢 | Art Direction承認済み |
| Backend complexity | 🟡 | ProjectSClient/Protocolがhotspot |
| Minecraft limitation | 🟡 | Inventory/hotbar/tooltipとの共存 |
| UI dependency | 🟢 | 現行Combat/HUD/Inventoryは動く |
| Content requirement | 🟡 | icon/tooltip/menuは必要時に追加 |
| Testing | 🟢 | layout/routing/stats/protocol testsあり |

## User Decision Needed

現在なし。#127が通るまで新UI approval laneを始めない。

## Related

[UI Art Direction #114](https://github.com/retardedgyai/Projects-V2/issues/114) · [Equipment Tooltip #105](https://github.com/retardedgyai/Projects-V2/issues/105) · [Pixel Icons #126](https://github.com/retardedgyai/Projects-V2/issues/126)
