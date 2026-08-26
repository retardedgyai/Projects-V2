# Progression / Skill Tree v0

## 方針

- XPはserverの確定Hitからのみ与える。v0はprocess内メモリのみで、永続化しない。
- 次Levelに必要なXPは `100 + (level - 1) * 50`。Level upごとにSkill Pointを1点与える。
- Twin Blades専用の8 nodeを固定定義し、各nodeは1点。取得条件はtreeの前提node。
- `keen-edge` は通常攻撃のDamage、`swift-step` / `air-dancer` はdash、`wide-cut` / `falling-star` は判定半径、`execution-rhythm` はpulse間隔、残りはSkill Damageを変更する。

## 理由

小さいtreeで `combat -> XP -> level -> point -> node -> skill変化` を確認することを優先する。数値とnode名は可逆なv0判断であり、DB、UI、respec、generic frameworkは含めない。
