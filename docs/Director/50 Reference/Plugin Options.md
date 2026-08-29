# Plugin Options

## 最初は追加Pluginなし

このVaultはObsidian標準のProperties、Templates、Daily Notes、Links、Searchだけで動く。

2週間使い、手動更新が本当に負担になったら次だけ検討する。

## Dataview（任意）

Status/Health/Decisionを自動一覧にする時だけ入れる。

```dataview
TABLE design_status, implementation_status, priority, user_decision_needed
FROM "Director/10 Systems" OR "Director/20 Content"
WHERE design_status != "FIXED" OR implementation_status != "DONE"
SORT priority ASC
```

```dataview
TABLE design_status, implementation_status, priority
FROM "Director/10 Systems" OR "Director/20 Content"
WHERE user_decision_needed = true
```

## Tasks（任意）

複数ページのcheckboxをTodayへ自動収集したくなった時だけ入れる。

```tasks
not done
path includes Director/30 Current
sort by priority
```

## 入れないもの

- Theme調整用Plugin
- Graph View整理用Plugin
- 複雑なKanban/Database
- 自動化のための自動化

Pluginは「今困っている具体的な1作業を減らすか」で判断する。
