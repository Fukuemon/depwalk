# CLI interface 結合 — analyze / traversal / output を配線し depwalk として機能させる

> spec 本体テンプレート。
> 機能固有の追加節 (API endpoint / ER 図 / 認可マトリクス / data-testid 等) は `templates/specs/appendices/<topic>.md` から該当 appendix を取り込む。
> 必須節・必須サブ節は `hooks/spec/validate_document.sh` が検査し、レビュー観点は `.rulesync/skills/spec-review/references/review-rubric.md` が評価する。
>
> 本 spec は CLI ツールであり API / DB / 認可 / 画面 / data-testid のいずれにも該当しないため、appendix は取り込まない。

## メタ情報

- Issue: `#22`
- ステータス: `In Progress`
- 作成日: 2026-07-12
- 更新日: 2026-07-12
- Branch: `feature/22`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考      |
| --- | --------------------------- | ---------- | ---------- | --------- |
| 1   | 起票                        | 完了       | 2026-07-12 | issue #22 |
| 2   | 下書き (scaffold)           | レビュー済 | 2026-07-12 |           |
| 3   | 上位文書突合                | 完了       | 2026-07-12 |           |
| 4   | 論点整理                    | 進行中     | 2026-07-12 |           |
| 5   | 論点解決                    | 未着手     |            |           |
| 6   | Interface / Routing 設計    | 未着手     |            |           |
| 7   | Content / Data 設計         | 未着手     |            |           |
| 8   | Performance / Security 設計 | 未着手     |            |           |
| 9   | Test / Metrics 設計         | 未着手     |            |           |
| 10  | 実装分割                    | 未着手     |            |           |
| 11  | レビュー済                  | 未着手     |            |           |

## 上位文書整合

正本 ([PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要
- Design Doc 更新要否: 不要
- ADR 起票要否: 要 (論点 #10 の結論次第で新規 ADR または既存 ADR-0003 改訂の可能性あり)

| 上位文書      | 節 / 該当箇所                                                                                       | 整合方針 (継承 / 補足 / 変更提案)                                           |
| ------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| PRD           | -                                                                                                   | -                                                                           |
| Design Doc    | `design/DesignDoc.md` S1-S3 成功条件 (L39-46)、モジュール責務 CLI (L132-140)、Phase 計画 (L233-238) | 継承                                                                        |
| feature doc   | `design/features/java-analyzer/DesignDoc_java-analyzer.md` analysisMode 意味論 (L91-102)            | 継承。caller 方向で Core が fullGraph を選ぶ責務は #22 へ引き継ぎと明記済み |
| context       | `context/architecture.md` package boundary (L19-30)                                                 | 継承                                                                        |
| context       | `context/testing.md` E2E 2 層構造 (L16, L20)                                                        | 補足。CLI 層照合は本 spec の完成をもって E2E 2 層構造が完成する             |
| ADR (なら ID) | ADR-0002 (Cobra / Go 標準 command)                                                                  | 継承                                                                        |
| ADR (なら ID) | ADR-0003 (Analyzer 起動契約の解決順序・規約 path 前段、L19-24, L32-33)                              | 補足。規約 path 前段の要否判断は本 spec の論点 (#10)                        |

> 矛盾を検出した場合は `spec-sync` で PRD / Design Doc / feature doc / context / ADR への back-propagation を提案する。

## 関連資料

- `PRD.md`: 該当なし
- `design/DesignDoc.md`: S1-S3 成功条件 / モジュール責務 CLI / Phase 計画
- 関連 issue / ticket:
  - issue: https://github.com/Fukuemon/depwalk/issues/22
  - 決定経緯 (issue 単位の作業記録): `specs/9-java-analyzer/` (D2/D4/P2_02)、`specs/6-traversal/` (Request/Result API)、`specs/7-output/` (D6 `output.Write` / D7 golden test)
  - 関連 issue: #9 (実装済み) / #6 / #7 / #21 (独立)

## 背景

- Phase1 (#9) で解析パイプライン (depwalk analyze → Java Analyzer → graph 構築)、#6 で traversal、#7 で output が実装済みである。
- しかし現状の `depwalk analyze` は graph 構築後に件数サマリ 1 行 (`analyzed %d method(s), %d call edge(s)`) と diagnostics を出すのみで、traversal / output は CLI から一切呼ばれていない (`core/internal/cli/analyze.go`)。
- 本 issue は残る最後のピースである CLI interface (flag 体系と analyze → traversal → output の結合) を設計・実装し、depwalk を「メソッドの caller / callee を探索し、選択した形式で出力する」影響調査ツールとして端から端まで機能させる。
- DesignDoc の S1 (caller 探索) / S2 (callee 探索) / S3 (出力形式のパース可否) の CLI 層照合を完成させる責務を持つ (`design/DesignDoc.md` S1-S3 節)。

## スコープ

### やること

- CLI flag 体系の確定: 対象メソッド指定 (method selector)、探索方向 (caller / callee)、深さ上限、出力形式選択 (Console / JSON)、既存最小 flag (`--analyzer-cmd` / `--language` / `--analyzer-meta`、ADR-0003 正本) との整合
- analyze use case から traversal (`traversal.Traverse` / `graph.Direction` / `MaxDepth`) / output (`output.Write` / Format registry) への結合
- caller 方向の問い合わせで Core が fullGraph を選ぶ責務 (spec #9 D4 の宣言、feature doc `design/features/java-analyzer/DesignDoc_java-analyzer.md` analysisMode 意味論節) の実装
- E2E の CLI 出力レベル照合の完成 (spec #9 P2_02 で保留した部分。現 E2E `core/e2e/java_fixture_test.go` は Go 関数直接呼び出しでグラフレベル照合のみ)
- 規約 path による Analyzer 既定解決の要否判断 (ADR-0003 が「必要になった時点で③の前段として追加できる形にしておく」とした前段)

### やらないこと

- Interface Dispatch / Spring DI 解決 (→ issue #21)
- 新規出力形式 (DOT / Mermaid 等) の追加 (output registry に format 定数は存在するが CLI へは露出しない — 露出範囲は論点)
- Analyzer 側 (`analyzers/java/`) の変更

## 要件の解釈

### 実現したいユーザー価値

- 開発者が、変更対象メソッドの影響範囲 (caller / callee) を CLI 一発で調査でき、CI では機械的にパース可能な出力で影響調査を自動化できる。(DesignDoc の利用者像: 開発者 / CI に基づく)

### 成功条件

issue #22 の完了条件をそのまま採用する:

- 実プロジェクト相当の fixture に対し、CLI だけで caller / callee 影響調査が完結する (S1 / S2)
- 出力形式が機械的にパース可能である (S3)
- 探索方向・深さ・出力形式の flag 体系が確定し、後方互換の拡張余地が宣言されている
- E2E が CLI 出力レベルで期待値と照合される

### 対象ユーザー / 操作主体

- 開発者 — ローカルで depwalk CLI を実行し、影響調査結果を Console 形式で読む
- CI — depwalk CLI を実行し、JSON 形式の出力を機械的にパースして後続処理へ渡す

EARS 風で振る舞いを記述する (`<who>` `<trigger>` 時、システムは `<expected behavior>` する)。

- [S1] WHEN 開発者が CLI で対象メソッド (method selector) と caller 方向・深さ上限を指定して実行したとき、システムは到達した caller 集合を指定した出力形式で出力する
- [S2] WHEN 開発者が CLI で対象メソッドと callee 方向・深さ上限を指定して実行したとき、システムは到達した callee 集合を指定した出力形式で出力する
- [S3] WHEN 利用者または CI が JSON 出力形式を指定して実行したとき、システムは機械的にパース可能な構造化出力を stdout に返す

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                                                                                                            | 決定候補 | 決定 |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ---- |
| D3  | 探索方向 flag の設計 — 名前・値 (caller/callee)・既定値の有無・必須か任意か (探索なし=現行サマリ動作を残すか)                                                                   |          | 未決 |
| D4  | 深さ上限 flag — 名前・既定値 (無制限 or 有限)・traversal の `MaxDepth *int` / depthLimit cutoff との対応                                                                        |          | 未決 |
| D5  | 出力形式 flag — 名前・値 (console/json)・既定値。output registry の dot/mermaid を露出するか隠すか                                                                              |          | 未決 |
| D6  | caller 方向で fullGraph を選ぶ責務の実装位置 — cli 層か analyze use case か。analysisMode / Entrypoints を CLI に露出するか (spec #9 D4 は「露出は CLI interface spec で確定」) |          | 未決 |
| D7  | Entrypoints の扱い — method selector を `AnalysisRequest.Entrypoints` に渡すか、callee 方向で reachableFromEntrypoints を使うか                                                 |          | 未決 |
| D8  | エラー / exit code 体系 — `traversal.Status` (startNotFound 等)・depthLimit cutoff の CLI 上の表現、exit code 規約                                                              |          | 未決 |
| D9  | E2E の CLI 出力照合方式 — os/exec で depwalk バイナリを起動するか、cli package を関数呼び出しするか。golden file の置き場所・照合粒度                                           |          | 未決 |
| D10 | 規約 path による Analyzer 既定解決の要否 — ADR-0003 前段を今回導入するか見送るか。導入時は ADR 改訂 or 新 ADR                                                                   |          | 未決 |

## 解決済みの論点

(`spec-resolve` で確定したものをここに移動する)

- #22 D1: method selector は 1 引数の統合書式 `<qualifiedName>#<メソッド名>[(<引数型リスト>)]` とする。括弧付きで signature 完全指定 (例: `com.example.UserService#findById(java.lang.Long)`)、括弧省略時はメソッド名のみで指定 (例: `com.example.UserService#findById`)。Analyzer の signature 表記 (feature doc java-analyzer の methodId 節) と同一の表記体系。flag 名 / 位置引数かどうかは D2・D3 で確定する。オーバーロード曖昧性は、signature 省略時に同名メソッドが複数一致した場合、候補の完全 signature を一覧表示してエラー終了する (自動選択しない。exit code は D8 で確定)。一致 1 件ならそれを採用する。graph node との照合は node が保持する symbol 情報 (qualifiedName / signature) を走査して行い、Core は methodId の文字列形式 (`java:` prefix 等) に依存しない (Decision Priority 2: 言語非依存)。graph.Node に必要フィールドが不足していれば `graph` package の convert で保持を追加する (core ドメイン内の軽微変更)。理由: methodId 文字列形式への非依存は言語非依存原則と整合し、曖昧時の自動選択をしないことで CI 向けの予測可能性を確保できるため。
- #22 D2: 探索クエリは `analyze` コマンドへの flag 追加で提供する (サブコマンド分割しない)。形は `depwalk analyze <path> --language java --analyzer-cmd ... --method <selector> --direction <dir> --max-depth <n> --format <fmt>` (各 flag の名前・既定値は D3-D5 で確定)。後方互換: `--method` 省略時は現行のサマリ動作 (件数 1 行 + diagnostics) を維持し、既存 flag (`--analyzer-cmd` / `--language` / `--analyzer-meta`) は変更しない。拡張余地の宣言: 将来の新出力形式は `--format` の値追加で、新しいクエリ種別 (例: パス探索) が必要になった場合はサブコマンド新設でも flag 追加でも拡張できる構造とする (issue 完了条件「後方互換の拡張余地が宣言されている」に対応)。理由: 実装最小・既存動作の後方互換維持・issue の「flag 体系」の表現と一致し、analyzer 起動系 flag の重複定義を避けられるため。

## 未確定事項

(決定できない項目を理由とともに残す。1 件でも残っていれば下流 phase は止める)

- 設計時の論点 D3-D10 が未解決 (clarify 進行中) であり、clarify phase で解消する。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                   |
| ------------------- | :------: | -------------------------------------------------------------------------- |
| `core`              |    ◯     | CLI entrypoint / analyze use case / E2E                                    |
| `traversal`         |    -     | 既存 API を利用、変更なし想定。公開 API 変更が必要になった場合は論点に戻す |
| `output`            |    -     | 既存 API を利用、変更なし想定。公開 API 変更が必要になった場合は論点に戻す |
| `analyzer-protocol` |    -     | `AnalysisRequest` の Entrypoints / AnalysisMode は定義済みで利用のみ       |
| `java-analyzer`     |    -     | 変更しない                                                                 |

## 機能仕様

### User Flow

(clarify 以降で記述)

### Reuse Policy

(clarify 以降で記述)

### Performance

(clarify 以降で記述)

### Routing / URL State

(clarify 以降で記述)

### Content / Assets

(clarify 以降で記述)

### UI Reuse

(clarify 以降で記述)

### Testing

(clarify 以降で記述)

## Interface 設計

### UI / API / Event Interface

(clarify 以降で記述)

### Props / Request / Response

(clarify 以降で記述)

## Content / Data 設計

### 保存・管理するデータ

(clarify 以降で記述)

### コンテンツ配置 / package / route

(clarify 以降で記述)

## Performance / Security 設計

### Performance

(clarify 以降で記述)

### Security / Privacy

(clarify 以降で記述)

## Error / Fallback 設計

### エラーケース

| #   | ケース               | ユーザーへの見せ方 | リカバリ |
| --- | -------------------- | ------------------ | -------- |
| 1   | (clarify 以降で記述) |                    |          |

### Fallback

(clarify 以降で記述)

## テスト / 評価方針

### テスト観点

(clarify 以降で記述)

### 計測指標

(clarify 以降で記述)

## フロー / シーケンス

(`spec-diagrams` で生成。spec の主要操作を Mermaid 図に落とす)

### Flowchart (ユーザー操作起点)

```mermaid
flowchart TD
```

### Sequence

```mermaid
sequenceDiagram
```

## 実装分割

### 実装タスク案

| Phase | 対象                 | 概要 | 依存 |
| ----- | -------------------- | ---- | ---- |
| P1    | (clarify 以降で記述) |      |      |

### prompts 生成方針

(clarify 以降で記述)

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。`spec-track` / `spec-sync` で更新する。

scaffold 時点では変更なし。clarify / track phase で論点が解決した際に追記する。

### PRD への影響

| 対象節 | 変更内容 | 理由 |
| ------ | -------- | ---- |
|        |          |      |

### Design Doc への影響

| 対象節 | 変更内容 | 理由 |
| ------ | -------- | ---- |
|        |          |      |

### feature doc への影響

| 対象 doc / 節                | 変更内容                                                                                                                       | 理由                               |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------- |
| (反映先は sync phase で確定) | method selector の CLI 書式・曖昧性解決規則を CLI interface の設計として design 側へ反映予定。状態=未反映 (source: clarify D1) | D1 決定の durable な設計成果のため |

### context への影響

| 対象 doc / 節                                  | 変更内容                                                | 理由                                        |
| ---------------------------------------------- | ------------------------------------------------------- | ------------------------------------------- |
| `context/project.md` Quick Commands 開発起動欄 | flag 体系確定後に更新。状態=未反映 (source: clarify D1) | CLI 起動例が method selector 書式を含むため |

### ADR の新規 / 更新

| ADR ID | 変更内容 | 理由 |
| ------ | -------- | ---- |
|        |          |      |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                                   | 対応                               |
| ---------- | ------------------------ | ------------------------------------------------------------------------------------------ | ---------------------------------- |
| 2026-07-12 | NEEDS_WORK               | (scaffold phase) EARS 要件記述が未記入 (S1-S3 から 3 件追加要)、フェーズ表の突合状態未同期 | 対応済 (EARS 追加・フェーズ表同期) |
| 2026-07-12 | PASS                     | (scaffold 再) 指摘なし (前回指摘対応を確認)                                                | —                                  |

## 変更履歴

| 日付       | 変更者         | 変更内容                                                  |
| ---------- | -------------- | --------------------------------------------------------- |
| 2026-07-12 | spec-lifecycle | scaffold 作成                                             |
| 2026-07-12 | spec-lifecycle | scaffold レビュー指摘対応 (EARS 記述追加・フェーズ表同期) |
| 2026-07-12 | spec-lifecycle | scaffold 再レビュー PASS                                  |
| 2026-07-12 | clarify        | D1 (method selector 書式) を確定                          |
| 2026-07-12 | clarify        | D2 (CLI 構造: analyze への flag 追加) を確定              |

## 備考

appendix は取り込まない (CLI ツールであり API / DB / 認可 / 画面 / data-testid のいずれにも該当しない)。
