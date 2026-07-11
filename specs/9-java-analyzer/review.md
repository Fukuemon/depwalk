# spec review 記録 (#9 Java Analyzer)

`spec-review` (fresh-context evaluator = `spec-reviewer` subagent) の結果を追記する。

## Review 2026-07-11 (phase: scaffold)

Verdict: **NEEDS_WORK**

### 観点別評価

| 観点               | 結果       | 要点                                                                                                                    |
| ------------------ | ---------- | ----------------------------------------------------------------------------------------------------------------------- |
| 上位文書整合       | PASS       | DesignDoc (モジュール責務 / P2・P3 / Future Work) / analyzer-protocol feature doc / context / ADR-0001・0002 と矛盾なし |
| 未解決論点         | PASS       | D1-D10 の決定欄がすべて `未決` と明示。Q2 は決定者・期限付きで管理され上流 DesignDoc Q2 と一致。下流 phase の先行なし   |
| 実装対象明示       | NEEDS_WORK | target 名は `context/project.md` の対象ドメインと一対一だが、`core` の責務境界が spec 内で自己矛盾 (下記指摘)           |
| template 必須節    | PASS       | `hooks/spec/validate_document.sh` の必須 23 節をすべて充足。メタ情報・変更履歴も同期                                    |
| EARS acceptance    | PASS       | WHEN 2 / IF 3 / THE SYSTEM SHALL 2。protocol 正本と照合可能で曖昧動詞なし                                               |
| prompts 自己完結性 | N/A        | prompts 未生成 (phase 10 未着手)                                                                                        |
| 正本境界           | N/A        | sync 未実行。spec が作業正本でよい段階                                                                                  |

### 指摘 (blocking)

**`index.md` — `core` の責務境界が spec 内で矛盾している**

- スコープ「やること」に「Core からの起動方法の確定」を置き、論点 D2 に「Core が Analyzer をどう発見・起動するか」(候補 A: CLI flag / B: 環境変数 / D: Core に同梱) を置く一方、実装対象テーブルでは `core` を `-` とし「S5 のため差分を出さない」と宣言している。
- 実コードでは Core 側の起動コマンド解決は未実装 (`core/internal/analyzer/runner.go` は `Command{Path, Args}` を呼び出し側から受け取るのみ、`core/internal/cli/root.go` に analyze command / flag なし)。D2 を A/B/D のいずれで決めても Core (CLI) 側に差分が出るため、「差分を出さない」と両立しない。
- `design/DesignDoc.md` の S5 は「**新しい言語の Analyzer を追加するとき** Core を変更せずに済む」であり、初号機 (Java) 導入時の Core 側配線を免責する条項ではない。spec の S5 解釈は上位定義より強い。
- 対応案: ① 実装対象テーブルの `core` 行に「D2 の決定に伴う起動コマンド解決の**実装**は本 spec のスコープ外 (後続 CLI interface spec が担当)、本 spec は決定のみ」と責務境界を明示する / ② `core` を `◯` に変え、Java Analyzer 起動配線を実装対象に含める。あわせて S5 の表現を DesignDoc 定義に合わせる。

### 指摘 (advisory / non-blocking)

- D6 の決定候補 B「lambda も含める」は protocol の `symbolKind` enum (`method` / `constructor` / `function` / `initializer`) に lambda を持たないため、選ぶと契約変更 (major bump 判断) が必要。clarify 時に protocol 影響を明記する。
- 「ADR 起票要否: 判断保留」は template の `要 / 不要` から外れる値。clarify で確定させる。
- `context/project.md` の Quick Commands は Go のみで Java 側 build / test コマンドを持たない。D1 / D10 の決定は `context/project.md` と `context/engineering.md` (「Analyzer build を束ねる必要が出た時点で make-like wrapper を検討」) に波及するため、「context への影響」表で拾う。

### 対応 (完了)

- blocking: ユーザー判断により「Core 側の初回配線 (`depwalk analyze` command + Analyzer 起動コマンド解決) を本 spec の実装対象に含める」と決定。実装対象テーブルの `core` を ◯ (初回配線のみ / Java 固有の分岐は入れない) に変更し、スコープ「やること」に追加。S5 の表現を DesignDoc 定義 (2 つ目以降の Analyzer 追加時に Core 無変更) に合わせた。
- advisory: D6 に protocol の `symbolKind` enum 制約を注記。ADR 起票要否に判断条件を明示。「context への影響」表を記入。

## Review 2026-07-11 (phase: scaffold, 2 回目)

Verdict: **NEEDS_WORK**

前回 blocking (`core` の責務境界の矛盾) は解消を確認。新たな blocking が 1 件。

### 指摘 (blocking)

**S5 の再定義が「継承」扱いのまま、Design Doc へ back-propagation 登録されていない**

- 上位定義 (`design/DesignDoc.md` の S5 測定方法「Analyzer 追加で Core モジュールに差分が発生しないこと」/ P4) には「初号機は対象外」という留保がない。一方 spec は「Java 導入に伴う初回配線は S5 の対象外」と宣言し、Core への差分を実装対象に含めた。spec の成功条件が上位 S5 の測定方法を上書きしている。
- にもかかわらず上位文書整合表の Design Doc 行は `継承` のままで、「Design Doc への影響」表にも S5 の明確化行がない。このままでは Design Doc 側に旧定義が残り drift する。

### 対応 (完了)

- 上位文書整合表の Design Doc 行を分割し、「成功条件 S5 / 設計原則 P4」行を `変更提案` として登録。
- 「Design Doc 更新要否」を S5 / P4 の明確化を含む形に更新。
- 「Design Doc への影響」表に S5 / P4 の測定方法明確化の行を追加 (phase: sync で反映予定)。
- advisory: 上位文書整合表に `context: engineering` 行を追加。「context への影響」表の Quick Commands 行に `depwalk analyze` の波及を追記。

## Review 2026-07-11 (phase: scaffold, 3 回目)

Verdict: **NEEDS_WORK** (指摘はすべてメタ情報 / review 記録の追随。設計内容の変更は不要)

### 観点別評価

- 上位文書整合 / 未解決論点 / 実装対象明示 / EARS acceptance: **PASS** (S5 の back-propagation 登録を確認)
- prompts 自己完結性 / 正本境界: N/A (prompts 未生成 / sync 未実行)
- template 必須節: NEEDS_WORK (`Spec Workflow Contract`「文書メタ情報の同期」未達)

### 指摘 (blocking / 機械的修正)

1. 設計フェーズ状況の phase 3 備考が「矛盾なし」のまま。実際は S5 / P4 の齟齬を検出し変更提案として登録した状態。
2. 2 回目レビューの記録が `review.md` と index のレビュー表に未追記。phase 2 の状態が `レビュー済` だが直近 verdict は NEEDS_WORK で実態と合わない。

### 指摘 (advisory)

- 「Design Doc への影響」表の feature 設計行「(状態: 完了)」が既に反映済とも読める。phase: sync で反映する旨に書き分ける。
- S5 は `context/architecture.md` / `context/testing.md` にも再掲されている。明確化するなら phase: sync で追随確認対象に含める。

### 対応 (完了)

- phase 3 の備考を「S5 / P4 の齟齬を検出 → 変更提案として登録 (phase: sync で反映)」に更新。
- phase 2 の状態を `進行中` に戻し、PASS 到達時に `レビュー済` へ更新する運用に修正。
- `review.md` に 2 回目 / 3 回目の記録を追記し、1 回目の「対応」placeholder を埋めた。index のレビュー表にも各回の行を追加。
- advisory: feature 設計行を「phase: sync で反映」と書き分け。「context への影響」表に `context/architecture.md` 行 (S5 再掲の追随) を追加し、`context/testing.md` 行にも S5 再掲の追随確認を追記。

## Review 2026-07-11 (phase: scaffold, 4 回目)

Verdict: **PASS**

全観点で PASS (prompts 自己完結性 / 正本境界は sync・prompts 未実施のため N/A)。phase: scaffold の gate を通過。

### 参考 (非ブロッキング / 次 phase で解消)

- ADR 起票要否 (「phase: clarify で `要 / 不要` に確定」) と ADR 影響表の `(phase: clarify で判断)` は、clarify 完了時に確定値へ更新する。
- 本ファイル 1 回目の記録にある「必須 23 節」は数え違い (`hooks/spec/validate_document.sh` の必須節は 22)。現行 spec の充足性には影響しない。
