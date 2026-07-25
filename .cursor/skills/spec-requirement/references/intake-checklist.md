# Requirements Intake Checklist

`spec-requirement` で draft を作るときに使う checklist。
受け入れ基準は EARS 風で記述すると spec 化以降の検証がやりやすい。

## EARS 風記法

- **Ubiquitous**: `THE SYSTEM SHALL <expected behavior>.`
- **Event-driven**: `WHEN <trigger>, THE SYSTEM SHALL <expected behavior>.`
- **State-driven**: `WHILE <state>, THE SYSTEM SHALL <expected behavior>.`
- **Unwanted behavior**: `IF <unwanted condition>, THEN THE SYSTEM SHALL <mitigation>.`
- **Optional feature**: `WHERE <feature is included>, THE SYSTEM SHALL <expected behavior>.`

例:

- WHEN 利用者がフォームを送信したとき、システムは確認画面を表示する。
- IF 入力検証が失敗した場合、システムはエラーメッセージを表示し送信を拒否する。

## Draft で埋めるべき項目

- [ ] 背景 / 目的 (なぜ今やるか)
- [ ] 想定ユーザー / ステークホルダー
- [ ] 提供価値 (成功条件)
- [ ] スコープ (やること / やらないこと)
- [ ] 受け入れ基準 (EARS 風で 3 件以上)
- [ ] 例外シナリオ (エラー時 / 競合時の挙動)
- [ ] 関連 PRD / Design Doc 節
- [ ] 未決事項 (担当 / 期限つき)

## 上位文書整合の最低ライン

draft を提示する前に最低限以下を確認する:

- PRD の `スコープ` と矛盾していないか
- Design Doc の `モジュール責務` / `Non Goals` と矛盾していないか
- 矛盾している場合は draft に「上位文書差分」セクションを設け、`spec-lifecycle` の sync phase 候補として残す
