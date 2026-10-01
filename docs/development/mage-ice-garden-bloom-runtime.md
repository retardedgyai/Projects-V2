# 氷の庭：立体演出を実イベントへ接続した試作

状態は **隔離ブランチで実装・自動検証済み／Creator Manual Smoke未判定**。
`38d948e4ea1a419f5c0942ee2a369ce4d592a466` の原版worktreeを維持し、
`play/gyai/mage-ice-garden-visual-study` だけで接続した。
damage・hit・slow・MP/CD・術式・ボス・床・入力・寿命のロジックには差分がない。
原版の170モデルも残す。main merge／push／deployはしない。次の技能へは進まない。

## 削減した演出

普通の16セルは一つの静止モデルを使う。5か所の結晶群だけが3段階で育ち、
保持中は動かず、最後の1秒で2段階に先端を折る。
境界は展開した瞬間から全21セルに存在し、形成を遅延damage波にはしない。
終了時には全境界と青を消し、5群だけに低い無彩色の破片を残して消す。
中心は約7cmの床だけ、最大高さは約66cm。通行を阻む氷壁・collisionは作らない。

初回のダメージ受理後だけ、足元の根の割れ→小片の飛散→無彩色の着地を出す。
contactは3モデル、1イベントにつき1 Display、2回のモデル切替で表現する。
後続の打撃や再入場で初回演出は再生しない。ボスsealなどの拒否中には出さない。
MP20／CD9秒／0.3秒の初動／6秒の場／1体4打／一度の術式獲得は原版のまま。

## 静的負荷比較

| 項目 | 原版38d948e4 | 画像試作2c3938a5 | 接続した今回 |
|---|---:|---:|---:|
| 演出の新規nativeモデル | 170（原版一式） | 473 | 47（原版に追加） |
| 保持中のcuboid | 45 | 101 | 61 |
| 庭のDisplay | 21 | 21の提案 | 21 |
| 初回接触のDisplay | 1/event | 1/eventの提案 | 1/event |
| 保持中のmetadata更新 | 0 | 未接続 | 0（cached通知も検査） |
| 庭のモデル切替／一周期 | 147 | 未接続 | 51 |
| contactモデル | 1＋毎tick変形 | 11 | 3、切替2回 |

47モデルは静止16＋結晶5群×5段階＋共通終了3＋共通contact3。
原版prepareを共用するため、今回の演出から参照するモデルは48個。
原版170モデルを残すので、出荷パック内の庭関連モデルは合計217個になる。
画像試作473モデルは `.tools` にあり、サーバーresource packへは取り込まない。

新規116素材ファイル、非圧縮89,800 bytes、素材だけのzipで48,007 bytes。
このzipサイズは追加素材を単独で圧縮した静的比較であり、HTTP転送量の実測ではない。
保持形状は画像試作から約40%減るが、原版より約36%増える。
Display上限はowner48／scene384のまま。部分的な庭の予約は行わない。

一周期のモデル切替は原版から約65%減る。これは共通pose選択を全tick調べた値。
自然終了シナリオでは、ownerのheadless接続が受け取ったmetadataをcached形式も解決し、
合計96 packet body／9,552 bytesをシリアライズした。初期表示の通知も含む。
この数値はテストのentity IDに依存し、transport framing・圧縮・実回線・client FPSは含まない。
正確な最終記録は `.tools/ice-garden-bloom-review/event-evidence.json` を参照する。

実機FPS・実通信の読みやすさは測定していない。形状の増加は性能上の残るリスク。
全面bloomや新shaderを追加せず、形成・contact・終了の短い変化へ負荷を寄せる。

## 実イベントと寿命

既存の `CorePlayerCombat.tickGarden` → 受理された `hit` → contact の経路をそのまま使う。
`CoreIceGardenChoreography` だけがnativeのモデルphaseを選ぶ。
`CoreCombatMeshes` は庭とcontactをモデルが変わる時だけ更新する。
予兆・一括予約・viewers・上限・late pack acceptanceは原版の経路を保持する。

`GreatswordVfx` に庭専用の短いvisual cue clockを置き、unpackedの観客にも
nativeと同じcontactのsplit/fly/settle、警告色、終了の無彩色点を届ける。
packedの本人へfallbackを重ねない。5個の重要な終了点は観客密度で間引かず、
既存の180/viewer、240/shared viewer、2400/scene particle予算内で送る。
別castのF取消なら庭のclockとnativeを保持し、reset／clear／切断ならcueも全て消す。
いつものPULSE schedulerが取消されても、設置済みの視覚は実体と同じ時刻に終了する。

## 最終版の検証

Java25／offline／no daemon／worker1／Gradle Xmx1400m／Kotlin in-processで
関連11suite、216テストを実行。failure／error／skip 0。
同じ最終版で `:server-minestom:build` が成功。server jar／startScripts／distTar／distZipを生成。
clientやゲームを起動せず、依存install・外部AI・課金も行わない。

既存210テストに、実ダメージ受理と一度だけのcontact、実イベントexport、
全phaseのモデル参照と切替予算、packed/unpackedのcontact clock、
F保持後の警告・終了clock、clear直後の遅延cue掃除を加えた。
二重使用・床・柱の陰・boss耐性・敵死亡・退出・切断・Instance移動・
表示上限・partial reservation拒否・最小・RPなし/mixed/late acceptanceも回帰対象。
素材検査は原版4＋追加6で10 PASS。

## GIFの根拠

`CorePlayerCombatTest` で実際にskill入力、別castのF取消、敵の移動、boss seal解除、
resetを実行し、受理damage／初回contact／ItemDisplayMeta／観客ParticlePacketを記録する。
`scripts/preview_ice_garden_bloom.py` はその出力をnative形状とpixel UVで投影する。
右側に別の提案clockや強制contactを足していない。
左側は原版のKotlin pose exportを、同じ実イベント時刻・同じ取消/解除条件で比較する。
両側で訂正済みの前面・奥行き処理を使い、橙枠だけ模式人物を描く。

出力は `.tools/ice-garden-bloom-review/`。

- `ice-garden-natural-events.gif`: 遅着の受理命中、別cast取消後の保持、自然終了。
- `ice-garden-cancel_prepare-events.gif`: 未展開の取消。
- `ice-garden-clear_reset-events.gif`: 展開後resetによる即時解除。
- `ice-garden-sealed_then_accept-events.gif`: 拒否中はcontactなし、受理後だけcontact。
- `ice-garden-unpacked-packet-events.gif`: 同じイベントで観客へ送られたparticle位置のみ。
- `ice-garden-runtime-storyboard.png`: 6段階の比較。

ゲーム録画ではなく、試験ハーネスのサーバーイベント投影。
粒子位置版はそのtickの送信位置・色だけで、client粒子の運動・寿命・透過を再現しない。
音の試聴、手触り、client FPS、実機reloadは未確認。
AGENTSのCreator Manual Smokeによる最終feel判定は未完了。

破損時の入口は `CoreIceGardenChoreography`（形・時刻）、`GreatswordVfx`（fallback/cancel）、
`scripts/build_ice_garden_bloom.py`（47素材）と `scripts/test_ice_garden_bloom.py`（素材契約）。
gameplay問題の原版は `38d948e4` に保持し、ここでdamage/slowを再設計しない。
