# ProjectS UI Asset Kit v0

These PNGs are the authored visual baseline for the ProjectS UI. They are not
runtime-integrated by Issue #117.

All images are RGBA PNGs authored for nearest-neighbor sampling. Surfaces and
long controls keep pixel edges, asymmetric highlights, and transparent corners
so the Minecraft world can remain visible behind them.

| Asset | Size | Intent |
| --- | ---: | --- |
| `glass_main` | 96x56 | Main panel; 10px corner, 9-slice candidate |
| `glass_secondary` | 88x52 | Character/inventory panel; 8px corner, 9-slice candidate |
| `glass_detail` | 96x48 | Detail card; 7px corner, 9-slice candidate |
| `ivory_active_tab` | 72x20 | Selected navigation plate; horizontal stretch only |
| `nav_row_idle` | 84x20 | Inactive navigation row; horizontal stretch only |
| `nav_row_hover` | 84x20 | Hover navigation row; horizontal stretch only |
| `nav_row_disabled` | 84x20 | Disabled navigation row; horizontal stretch only |
| `item_slot_idle` | 24x24 | Square item slot frame; do not stretch |
| `item_slot_hover` | 24x24 | Teal hover edge state; do not stretch |
| `item_slot_selected` | 24x24 | Ivory selected edge state; do not stretch |
| `equipment_slot_idle` | 28x28 | Equipment slot frame; do not stretch |
| `equipment_slot_selected` | 28x28 | Selected equipment frame; do not stretch |
| `empty_equipment_marker` | 12x12 | Empty equipment marker; nearest-neighbor scale |
| `button_primary_ivory` | 96x24 | Important action; horizontal stretch only |
| `button_secondary_glass` | 96x24 | Secondary action; horizontal stretch only |
| `close_button` | 20x20 | Compact close control; do not stretch |
| `divider` | 96x4 | Information separator; horizontal stretch only |
| `thin_highlight` | 96x2 | Subtle material highlight; horizontal stretch only |
| `small_badge_frame` | 56x18 | Compact rarity/status badge; horizontal stretch only |
| `corner_accent_marker` | 12x12 | Small section/corner accent; nearest-neighbor scale |

`projects_ui_preview.png` is a static visual gate. It contains a native-size
catalog, 4x nearest-neighbor samples, and a mini inventory composition using
the authored surfaces, navigation states, slots, and actions.
