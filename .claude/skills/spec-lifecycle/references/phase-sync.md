# Phase: sync (upstream sync)

spec で確定した決定のうち、PRD / Design Doc / feature doc / context / ADR の範囲を超えるものを **上位文書に書き戻す** phase。
spec が `## 上位資料からの変更点` テーブルに残した「変更提案」行を処理する。phase: scaffold / clarify が「変更提案」を理由に停止したときにも実行する。

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- 対象 spec の `index.md`
- 反映候補の上位文書 (PRD / Design Doc / feature doc / context / 関連 ADR)

## 手順

### 1. 変更提案の列挙

spec の `## 上位資料からの変更点` から、未反映の「変更提案」行を抜き出す。
phase: clarify が決めた決定の中で、上位文書を書き換えるべきものを列挙する。

### 2. 反映先の振り分け (ユーザー承認必須)

各変更提案に対し、以下のいずれかを **ユーザーに確認した上で** 選ぶ:

| 反映先      | 採用条件                                                             |
| ----------- | -------------------------------------------------------------------- |
| PRD         | プロダクト原則 / 成功条件 / スコープ / Persona / Outcome の変更      |
| Design Doc  | モジュール責務 / Phase 方針 / 全体像 (landscape) の変更              |
| feature doc | 特定 feature の設計方針 / データ構造 / 主要ユースケースの変更        |
| context     | architecture / toolchain / engineering 規約 / testing / infra の変更 |
| ADR (新規)  | 技術選定 / 責務境界 / 不可逆な意思決定の記録                         |
| spec のみ   | 上位文書側の変更不要 (spec で閉じる) — テーブルから除去              |

> **重要**: ユーザーの承認なしに PRD / Design Doc / feature doc / context を書き換えてはならない。

### 3. 上位文書への差分適用

- PRD / Design Doc / feature doc / context は `Edit` で対象節のみ差し替える
- ADR を新規作成する場合は `templates/adr/template.md` をコピーし、`<adr dir>/<NNNN>-<slug>.md` で作る
- ADR の番号は `<adr dir>` の最大連番 + 1 を採番する
- 影響範囲を上位文書側に必ず明記する
- **durable 成果 (IA / サイトマップ / データモデル / フロー / アーキ判断) を design 側へ反映する場合は、反映先 (feature doc 等) を以後の正本として明記する** (`Spec Workflow Contract` の `正本境界`)

### 3.5 正本ハンドオフ (durable 成果のみ)

durable な設計成果を design 側へ反映したときは、正本を design に移す。spec 側は記録に降格する:

- design 側: 反映した節に「本 doc を正本とする」旨を明記する (該当する場合は spec への決定経緯リンクを併記)
- spec 側: 反映した durable 節を「決定時スナップショット」と明示し、design への正本リンクを張る
- 同一 durable 成果について spec と design が二重に「正本」を名乗らないようにする (drift 防止)
- 論点 / 受け入れ基準 / レビュー / 実装分割 / 決定経緯は spec に残す (ハンドオフ対象外)

### 4. spec 側のトレース更新

- 反映済の変更提案は `## 上位資料からの変更点` の該当行に「反映済: <PR/コミット予定>」と注記する
- 新規 ADR を起こした場合は ADR ID を spec の `## 上位文書整合` テーブルに追記する
- 関連 spec / ADR / context を更新した場合は `Spec Workflow Contract` の「文書メタ情報の同期」に従い、対象文書のメタ情報も更新する
- handoff 後の spec の呼称は `Spec Workflow Contract` の正本境界「用語規約」に従い書き換える

### 5. ユーザー報告

- 反映ファイル一覧 (PRD / Design Doc / feature doc / context / ADR)
- spec 側の更新箇所
- 次アクション提案 (phase: track で差分仕上げ、または phase を先に進める)

## 停止条件

- 反映先 (PRD / Design Doc / feature doc / context / ADR / spec のみ) の振り分けにユーザー承認が得られていない
- 変更提案の影響範囲が読めない (関連 app / package が不明)
- 上位文書を書き換えるべきか、新規 ADR を起こすべきかの判断がつかない
- 反映対象が `Spec Workflow Contract` の正本 path から逸脱している (path を勝手に増やさない)
