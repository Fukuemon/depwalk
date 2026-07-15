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
- 更新日: 2026-07-15
- Branch: `feature/22`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                                                                                                                                                                          |
| --- | --------------------------- | ---------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-07-12 | issue #22                                                                                                                                                                                     |
| 2   | 下書き (scaffold)           | レビュー済 | 2026-07-12 |                                                                                                                                                                                               |
| 3   | 上位文書突合                | 完了       | 2026-07-12 |                                                                                                                                                                                               |
| 4   | 論点整理                    | 完了       | 2026-07-12 |                                                                                                                                                                                               |
| 5   | 論点解決                    | レビュー済 | 2026-07-15 | D11 を拡張 (graph.Symbol/output.NodeView への Metadata 透過を追加)。develop rebase 後の再検証で再オープンし再確定                                                                             |
| 6   | Interface / Routing 設計    | レビュー済 | 2026-07-15 | CLI flag 体系表・exit code 配線・Request/Response 変換・D11 拡張の Node 側 JSON スキーマ影響を記述。1周目レビュー NEEDS_WORK (feature doc analyzer-protocol との矛盾) 対応後、再レビュー PASS |
| 7   | Content / Data 設計         | 未着手     |            |                                                                                                                                                                                               |
| 8   | Performance / Security 設計 | 未着手     |            |                                                                                                                                                                                               |
| 9   | Test / Metrics 設計         | 未着手     |            |                                                                                                                                                                                               |
| 10  | 実装分割                    | 未着手     |            |                                                                                                                                                                                               |
| 11  | レビュー済                  | 未着手     |            |                                                                                                                                                                                               |

## 上位文書整合

正本 ([PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要
- Design Doc 更新要否: 不要
- ADR 起票要否: 不要 (D10 で ADR-0003 無改訂を確定)

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
  - 決定経緯 (issue 単位の作業記録): `specs/9-java-analyzer/` (D2/D4/P2_02、D11 帰属型の決定規則 — `methodSymbol.metadata` の `declaringType`/`inherited` はここが起源で feature doc へハンドオフ済み)、`specs/6-traversal/` (Request/Result API)、`specs/7-output/` (D6 `output.Write` / D7 golden test)、`specs/21-java-dispatch-spring-di/` (2026-07-14 に PR #25 で develop へマージ済み。D2 複数候補 edge の metadata 表現、D6 観測レイヤーの責務境界、D9 実装レビューで `core/internal/graph/convert.go` が `callEdge.metadata`/`methodSymbol.metadata` をコピーしていないことを確認し、`callEdge.metadata` 側は #22 D11 へ委譲・`methodSymbol.metadata` 側は当初「両 issue 対象外、将来issue」と確定していたが、2026-07-15 に #22 側の再検証 (本 spec D11 拡張) でこの切り分けを override し、`methodSymbol.metadata`/`graph.Symbol` 側も #22 D11 の実装範囲に含めることとした。#21 index.md D9 にも本 override を追記済み)
  - 関連 issue: #9 (実装済み、D11 帰属型規則が本 spec D11 の前提) / #6 / #7 / #21 (実装済み・develop マージ済み。D9 の「methodSymbol.metadata は対象外」を #22 側で override し D11 拡張に取り込み済み)

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

| #   | 論点 | 決定候補 | 決定 |
| --- | ---- | -------- | ---- |
|     |      |          |      |

(すべて解決済み。「解決済みの論点」を参照)

## 解決済みの論点

(`spec-resolve` で確定したものをここに移動する)

- #22 D1: method selector は 1 引数の統合書式 `<型の binary name>#<メソッド名>[(<引数型リスト>)]` とする (用語は feature doc `DesignDoc_java-analyzer.md` の methodId 節に合わせる)。括弧付きで signature 完全指定 (例: `com.example.UserService#findById(java.lang.Long)`)、括弧省略時はメソッド名のみで指定 (例: `com.example.UserService#findById`)。Analyzer の signature 表記 (feature doc java-analyzer の methodId 節) と同一の表記体系。flag 名 / 位置引数かどうかは D2・D3 で確定する。オーバーロード曖昧性は、signature 省略時に同名メソッドが複数一致した場合、候補の完全 signature を一覧表示してエラー終了する (自動選択しない。exit code は D8 で確定)。一致 1 件ならそれを採用する。graph node との照合は node が保持する symbol 情報 (qualifiedName / signature) を走査して行い、Core は methodId の文字列形式 (`java:` prefix 等) に依存しない (Decision Priority 2: 言語非依存)。graph.Node に必要フィールドが不足していれば `graph` package の convert で保持を追加する (core ドメイン内の軽微変更)。理由: methodId 文字列形式への非依存は言語非依存原則と整合し、曖昧時の自動選択をしないことで CI 向けの予測可能性を確保できるため。
- #22 D2: 探索クエリは `analyze` コマンドへの flag 追加で提供する (サブコマンド分割しない)。形は `depwalk analyze <path> --language java --analyzer-cmd ... --method <selector> --direction <dir> --max-depth <n> --format <fmt>` (各 flag の名前・既定値は D3-D5 で確定)。後方互換: `--method` 省略時は現行のサマリ動作 (件数 1 行 + diagnostics) を維持し、既存 flag (`--analyzer-cmd` / `--language` / `--analyzer-meta`) は変更しない。拡張余地の宣言: 将来の新出力形式は `--format` の値追加で、新しいクエリ種別 (例: パス探索) が必要になった場合はサブコマンド新設でも flag 追加でも拡張できる構造とする (issue 完了条件「後方互換の拡張余地が宣言されている」に対応)。理由: 実装最小・既存動作の後方互換維持・issue の「flag 体系」の表現と一致し、analyzer 起動系 flag の重複定義を避けられるため。
- #22 D3: 探索方向 flag は `--direction`、値は `caller` / `callee` (traversal の `graph.Direction` に対応)。任意 flag で既定値は `caller` — 影響調査の主用途 (S1: このメソッドを変えたら誰に影響するか) を既定にする。不正値は許容値一覧を添えてエラーとする。理由: 主ユースケースの入力を最短にでき、既定の意味が直感的なため。
- #22 D4: 深さ上限 flag は `--max-depth`、非負整数。任意 flag で既定は無制限 (未指定時は traversal の `MaxDepth` に nil を渡す)。0 は「起点のみ」(traversal の意味論をそのまま継承)。負値はエラーとする。指定時に深さ超過で打ち切られた場合は traversal の depthLimit cutoff 注釈が出力に反映される (出力表現は #7 output の View 仕様に従う)。理由: traversal の意味論と 1:1 対応で最も素直であり、循環は traversal が処理済みで結果は有限のため。
- #22 D5: 出力形式 flag は `--format`、任意 flag で既定値は `console`。値域は output registry に formatter 実装が登録されているもののみ (現時点: console / json)。未登録値は登録済み一覧を添えてエラーとする。dot / mermaid は Format 定数の予約のみで CLI には露出しない。将来は formatter 実装 + registry 登録だけで CLI に自動露出する (拡張余地の宣言の一部としてこの機構を明記)。理由: 人間が読む console を既定にでき (CI は `--format json` を明示)、CLI 層に許可値をハードコードしないことで Phase 4 の形式追加時に CLI 変更を不要にできるため。
- #22 D6: 探索方向に関わらず Core は常に fullGraph で解析する。実装位置は analyze use case (`core/internal/analyze`) — AnalysisRequest 組み立て時に AnalysisMode を明示的に fullGraph に設定する (protocol の暗黙既定に依存しない)。analysisMode は CLI flag として露出しない。spec #9 D4 の「caller 方向で Core が fullGraph を選ぶ責務」は本決定 (常時 fullGraph) により自明に満たされる。callee 方向の reachableFromEntrypoints による部分解析は将来の性能最適化として拡張余地に送る (今回スコープ外)。理由: 方向による挙動分岐を排して Phase1 スコープを最小化でき、D1 の「graph node 走査による曖昧性検出」と完全整合する (部分解析だと曖昧性解決が Analyzer 依存になる)。mode 設定を use case に置くのは architecture.md の use case orchestration 責務と整合するため。
- #22 D7: method selector を `AnalysisRequest.Entrypoints` には渡さない (Entrypoints は空のまま)。selector の照合は graph 構築後に Core が node 走査で行う (D1 の決定を踏襲)。entrypoints も CLI flag として露出しない。理由: D6 で常時 fullGraph のため Analyzer 側に entrypoint 情報は不要であり、露出面を最小化して将来の部分解析導入時に改めて設計するため。
- #22 D8: エラー / exit code 体系は 3 区分とする。exit 0: 探索成功 — 結果が空 (到達 node なし) や depthLimit cutoff 注釈付きも成功扱いとし、結果は stdout へ。exit 1: 実行時エラー — Analyzer 起動失敗、protocol 違反、出力書き込み失敗など Core 内部・外部プロセス起因の失敗。exit 2: 入力エラー — 不正な flag 値 (`--direction` / `--format` / `--max-depth` の値域外)、method selector のオーバーロード曖昧 (D1: 候補一覧を stderr へ)、対象メソッドが graph に存在しない (traversal の startNotFound)。エラーメッセージ・候補一覧・diagnostics は stderr、探索結果のみ stdout (S3 の機械パース性を保護)。startNotFound を exit 2 に割り当てる理由: CI が typo や消滅メソッドを exit code だけで検知できる。traversal 層では正常 status だが、CLI 層では「利用者の指定が graph と不一致」という入力問題として扱う。補足: Cobra 既定の exit 1 に依存せず、エラー種別を判別して os.Exit を制御する実装が必要になる。
- #22 D9: E2E の CLI 出力照合は os/exec によるバイナリ起動で行う。TestMain 等で `go build` した depwalk バイナリを実プロセスとして起動し、stdout / stderr / exit code を検証する真の E2E とする (flag パースや D8 の exit code 制御 (0/1/2) も検証範囲に含む)。照合粒度は console / json とも golden file との完全一致とし、json は加えて Unmarshal 成功を検証して S3 (機械的パース可能) を直接担保する。golden file の置き場所は既存の fixture 規約 (testdata/ 配下) に合わせ、具体 path は既存 E2E fixture の配置を踏襲する。既存のグラフレベル E2E (analyze.Run 直接呼び出し) は残し、CLI プロセス E2E を追加する 2 層構成とする (context/testing.md の E2E 2 層構造の宣言と対応)。理由: issue 完了条件「E2E が CLI 出力レベルで期待値と照合される」を文字通り満たし、JDK + fat jar の重いセットアップは既存 E2E が既に持つため追加コストが小さく、golden 方式は spec #7 と一貫するため。
- #22 D10: 規約 path による Analyzer 既定解決は #22 では導入を見送る。現行の解決順序 (`--analyzer-cmd` → `DEPWALK_ANALYZER_CMD` → 拒否) を維持し、ADR-0003 は無改訂とする。理由: 主利用者 (開発者 / CI) は明示指定で既に機能している。規約 path の設計 (置き場所・バージョン整合) は Analyzer の配布方式が固まってからの方が安全。ADR-0003 は「必要になった時点で前段に追加できる形」を既に宣言しており、本決定はその要否判断 (今は不要) の記録で issue のスコープを満たす。
- #22 D11: call edge metadata (`resolution` / `provenance` / `dispatch` 等、#21 の D6 から引き継ぎ) は JSON のみ透過 (passthrough) とする。`graph.Edge` と `output.EdgeView` に Metadata (protocol.Metadata = map[string]any) を非破壊で追加し、graph convert で破棄をやめて保持、JSON formatter は omitempty で edge にそのまま載せる。スキーマ非依存 (#21 が確定させる resolution / provenance 等のキー名に依存しない)。console への人間向け表現は見送り、#21 のスキーマ確定後に設計する。背景: #21 は call site 単位の複数候補 edge を出力し、確定/曖昧の区別と解決根拠を `callEdge.metadata` に持たせる (spec: `specs/21-java-dispatch-spring-di/index.md` の D2/D6)。現状 Core は取り込み時に metadata を破棄する (graph.Edge / output.EdgeView に Metadata なし) ため、CLI 出力への表出には Core graph / output への非破壊的な metadata 通過が必要。前提: #21 の clarify (D2/D6) は別ブランチ系列で進行中で feature/22 には未マージ。本決定は #21 のスキーマ詳細に依存しない透過方式を選ぶことでブランチ乖離の影響を回避している。理由: CI の機械処理用途 (確定 edge のみ利用等) が #21 実装時に Core 無変更で成立する (Decision Priority 1: 将来拡張性)。map 透過なので #21 の未確定スキーマにギャンブルしない。なお JSON 出力の edge に metadata フィールドが追加される点は後続 phase (diagram / 機能仕様記述) で反映する。
  - **拡張 (2026-07-15、develop rebase 後の再検証・D9 override)**: `core/internal/graph/convert.go` の `NodeFromMethodSymbol` も `EdgeFromCallEdge` と同様に `protocol.MethodSymbol.Metadata` をコピーしておらず、`graph.Symbol` (Node が保持) に `Metadata` フィールドが存在しない。この gap は理論上の懸念ではなく実際に使われている情報を失っている: `methodSymbol.metadata.declaringType`/`inherited` (継承元が scope 外のときの引き上げ node 標識) は spec #9 D11 (帰属型の決定規則、feature doc へハンドオフ済み) が起源で、Analyzer は既に出力している。`specs/21-java-dispatch-spring-di/index.md` D9 (2026-07-14、fresh-context レビュー PASS 済み) はこの同じ gap を認識した上で「`methodSymbol.metadata` 側は #21/#22 のどちらの実装範囲にも含めず、将来の method symbol metadata 利用 issue に送る」と確定していたが、本 spec (#22) はその切り分けを override し、Node 側の Metadata 透過も D11 の実装範囲に含めることを決定する (#21 index.md D9 にも本 override の追記を反映済み)。
    - 実装内容: `graph.Symbol` に `Metadata` (protocol.Metadata) を非破壊で追加し、`NodeFromMethodSymbol` で保持する。`output.NodeView` にも `Metadata` を追加し、JSON formatter は omitempty で node にそのまま載せる。Edge 側 (D11 本文) と同じ方針 (JSON のみ透過、スキーマ非依存、console 表現は見送り) を Node 側にも適用する。
    - 理由: D11 本文がスキーマ非依存の透過方式を選んだ理由 (CI の機械処理用途が Core 無変更で成立する) は Node 側にもそのまま当てはまる。Edge/Node で扱いを分ける理由がなく、分けるとかえって「dispatch 系は見えるが帰属型系は見えない」という非対称な CLI 出力になり CI 利用者にとって驚きが大きい。
    - スコープの境界: `graph.Symbol`/`output.NodeView` への Metadata 透過 (map をそのまま運ぶこと) のみが対象。`declaringType`/`inherited` キーの意味解釈や console への人間向け表現、他のキーを新設することは対象外 (Edge 側と同様、スキーマの意味解釈は行わない)。

## 未確定事項

(決定できない項目を理由とともに残す。1 件でも残っていれば下流 phase は止める)

- なし (D1-D11 全論点解決済み)。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                                                                                              |
| ------------------- | :------: | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core`              |    ◯     | CLI entrypoint / analyze use case / E2E / graph convert (`graph.Edge`/`graph.Symbol` 双方) の Metadata 保持 (D11 で決定、2026-07-15 に Node 側へ拡張) |
| `traversal`         |    -     | 既存 API を利用、変更なし想定。公開 API 変更が必要になった場合は論点に戻す                                                                            |
| `output`            |    ◯     | EdgeView / NodeView / JSON formatter に Metadata 透過追加 (「変更なし想定」から論点経由で正式に変更、D11 で決定、2026-07-15 に NodeView へ拡張)       |
| `analyzer-protocol` |    -     | `AnalysisRequest` の Entrypoints / AnalysisMode は定義済みで利用のみ                                                                                  |
| `java-analyzer`     |    -     | 変更しない                                                                                                                                            |

## 機能仕様

### User Flow

(clarify 以降で記述)

### Reuse Policy

(clarify 以降で記述)

### Performance

(clarify 以降で記述)

### Routing / URL State

CLI ツールのため URL routing は存在しない。相当する概念は「コマンド構造」であり、D2 で確定済み: 探索クエリはサブコマンドを新設せず `analyze` コマンドへの flag 追加で提供する (`depwalk analyze <path> --language java --analyzer-cmd ... --method <selector> --direction <dir> --max-depth <n> --format <fmt>`)。`--method` 省略時は現行のサマリ動作 (件数 1 行 + diagnostics) を維持し既存 flag は変更しない。将来の新しいクエリ種別 (例: パス探索) はサブコマンド新設でも flag 追加でも拡張できる (D2 の拡張余地宣言)。

### Content / Assets

(clarify 以降で記述)

### UI Reuse

(clarify 以降で記述)

### Testing

(clarify 以降で記述)

## Interface 設計

### UI / API / Event Interface

**CLI flag 体系 (`depwalk analyze [path]` への追加、D2)**:

| flag          | 型                         | 既定値                       | 説明                                                                                    | 決定 |
| ------------- | -------------------------- | ---------------------------- | --------------------------------------------------------------------------------------- | ---- |
| `--method`    | string                     | (未指定なら現行のサマリ動作) | method selector `<型の binary name>#<メソッド名>[(<引数型リスト>)]`                     | D1   |
| `--direction` | string (`caller`/`callee`) | `caller`                     | 探索方向 (`graph.Direction` に対応)                                                     | D3   |
| `--max-depth` | int (非負)                 | 無制限 (`nil`)               | 深さ上限 (`traversal.Request.MaxDepth`)。0 = 起点のみ                                   | D4   |
| `--format`    | string                     | `console`                    | 出力形式。値域は output registry に登録済みの formatter のみ (現時点: `console`/`json`) | D5   |

既存 flag (`--analyzer-cmd` / `--language` / `--analyzer-meta`、ADR-0003 正本) は変更しない。

**`--format` の許容値検証 (D5 拡張、本 phase で確定)**: CLI 層は許可値をハードコードせず、`output` package に既存の (現状 unexported) `registeredFormats() []string` を **`output.RegisteredFormats() []string` として公開 API 化** し、CLI がこれを参照して (a) 検証、(b) 未登録値のエラーメッセージでの一覧表示、の両方に使う。これにより Phase 4 で formatter 実装 + registry 登録を追加するだけで CLI 側は無変更のまま新形式が有効になる (D5 の「拡張余地の宣言」を実装レベルで担保する)。備考にあった引き継ぎメモ (output の登録済み Format 列挙 API の公開) はこの決定で解消する。

**exit code (D8 の実装配線)**: `os.Exit` を Cobra の既定 (`RunE` のエラーを常に exit 1 にする挙動) に委ねず、CLI 層でエラー種別を判別して 0/1/2 を返す。判別対象は traversal の `Status`(`StatusStartNotFound` → exit 2)、flag 値域エラー・method selector 曖昧性 (D1) → exit 2、Analyzer 起動失敗・protocol 違反・出力書き込み失敗 → exit 1、それ以外の探索成功 (結果空・depthLimit cutoff 含む) → exit 0。

**E2E からの観測点 (D9)**: 上記 flag パースと exit code 制御は `os/exec` によるバイナリ起動 E2E (D9) の直接の検証対象になる。

### Props / Request / Response

**CLI → Core 内部変換の配線**:

1. method selector (`--method`) の照合: graph 構築後、`graph.Graph` の全 `Node.Symbol` (`QualifiedName`/`Signature`) を走査してマッチする node を探す (D1・D7)。一致 0 件は traversal の `StatusStartNotFound` 相当として exit 2、複数一致 (signature 省略時のオーバーロード) は候補一覧を stderr に出し exit 2、1 件一致ならその `Node.ID` を `traversal.Request.StartID` に使う。
2. `traversal.Request` の組み立て: `StartID` (上記照合結果) / `Direction` (`--direction` を `graph.Direction` にマップ) / `MaxDepth` (`--max-depth`、未指定は `nil`)。`Order` は指定せず既定 (`OrderBFS`) のまま (観測可能な `Result` に影響しないため CLI に露出しない)。
3. `traversal.Traverse(graph, request)` → `traversal.Result` (Status/Nodes/Depths/Edges/Cycles)。
4. `output.Write(w, format, output.Input{Graph: graph, Result: result, Request: request})` で Console/JSON へ出力する。`output.Input`/`View`/`NodeView`/`EdgeView`/`CutoffView` は既存 (#7) の型をそのまま使う。

**D11 拡張に伴う Response スキーマへの影響 (Node 側公開 API、本 phase で明示)**: `output.NodeView` に `Metadata protocol.Metadata`(`map[string]any`、`omitempty`) を追加する (Edge 側の `EdgeView.Metadata` と対称)。これにより JSON 出力の `nodes[]` 各要素に、Analyzer が `methodSymbol.metadata` へ設定した値 (例: `declaringType`/`inherited`、spec #9 D11 起源) が omitempty で透過される。これは JSON スキーマの後方互換な追加 (新規 optional フィールド) であり、既存の `nodes[].id`/`qualifiedName`/`signature`/`source`/`minDepth` は変更しない。console フォーマッタは D11 本文の方針を踏襲し、metadata の人間向け表現は見送る (将来 phase で検討)。備考にあった引き継ぎメモ (D11 拡張の Node 側公開 API への影響明示) はこの記述で解消する。

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

| 対象 doc / 節                                                                                       | 変更内容                                                                                                                                                                                                                 | 理由                                                                                                              |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| (反映先は sync phase で確定)                                                                        | method selector の CLI 書式・曖昧性解決規則を CLI interface の設計として design 側へ反映予定。状態=未反映 (source: clarify D1)                                                                                           | D1 決定の durable な設計成果のため                                                                                |
| (反映先は sync phase で確定)                                                                        | graph.Edge / output.EdgeView への Metadata 透過追加と JSON edge の metadata フィールドを design 側へ反映予定。状態=未反映 (source: clarify D11)                                                                          | D11 決定の durable な設計成果のため                                                                               |
| `design/features/output/DesignDoc_output.md` (`NodeView` 節)                                        | graph.Symbol / output.NodeView への Metadata 透過追加と JSON node の `metadata` フィールドを design 側へ反映予定。状態=未反映 (source: clarify D11 拡張、2026-07-15)                                                     | D11 拡張決定の durable な設計成果のため                                                                           |
| `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` (`methodSymbol.metadata` 境界節) | `specs/21-java-dispatch-spring-di/index.md` D9 の「methodSymbol.metadata は両 issue 対象外」を override した経緯を境界記述に反映。状態=反映済 (2026-07-15、phase 6 レビュー指摘対応で先行実施。source: clarify D11 拡張) | フレッシュコンテキストレビューで #21 由来の durable 記述との矛盾が検出されたため、sync phase を待たず先行して解消 |

### context への影響

| 対象 doc / 節                                  | 変更内容                                                | 理由                                        |
| ---------------------------------------------- | ------------------------------------------------------- | ------------------------------------------- |
| `context/project.md` Quick Commands 開発起動欄 | flag 体系確定後に更新。状態=未反映 (source: clarify D1) | CLI 起動例が method selector 書式を含むため |

### ADR の新規 / 更新

| ADR ID   | 変更内容                                                                      | 理由                                                                                            |
| -------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| ADR-0003 | 無改訂 (規約 path 前段の導入見送り判断は本 spec D10 に記録) (source: clarify) | ADR-0003 は「必要になった時点で前段に追加できる形」を宣言済みで、要否判断の記録のみで足りるため |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                                                                                                                                                                                                                                                                                                                                                                                                          | 対応                                                                                                                    |
| ---------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 2026-07-12 | NEEDS_WORK               | (scaffold phase) EARS 要件記述が未記入 (S1-S3 から 3 件追加要)、フェーズ表の突合状態未同期                                                                                                                                                                                                                                                                                                                                                                        | 対応済 (EARS 追加・フェーズ表同期)                                                                                      |
| 2026-07-12 | PASS                     | (scaffold 再) 指摘なし (前回指摘対応を確認)                                                                                                                                                                                                                                                                                                                                                                                                                       | —                                                                                                                       |
| 2026-07-12 | NEEDS_WORK               | (clarify phase) メタ情報同期 3 件 + 用語揺れ等 2 件                                                                                                                                                                                                                                                                                                                                                                                                               | 対応済 (同 commit)                                                                                                      |
| 2026-07-12 | PASS                     | (clarify 再) 指摘なし (前回 5 件の解消を確認)。非ブロッキング補足 2 件は後続 phase で扱う                                                                                                                                                                                                                                                                                                                                                                         | —                                                                                                                       |
| 2026-07-15 | NEEDS_WORK               | (D11 拡張分 clarify 再オープン) 内容面 (D11 拡張の事実整合性・cross-spec override・実装対象・EARS・上位文書整合) に矛盾なし。ただしメタ情報同期 2 件: (1) 本表に 2026-07-15 分のレビュー記録がないままフェーズ表が「レビュー済」を自称、(2) #21 index.md フェーズ5 備考が D9 override 前の要約のまま                                                                                                                                                              | 対応済 (フェーズ表・#21 フェーズ表 同期)                                                                                |
| 2026-07-15 | PASS                     | (D11 拡張分 再レビュー) 前回指摘 2 件の解消を確認、新たな不整合なし                                                                                                                                                                                                                                                                                                                                                                                               | —                                                                                                                       |
| 2026-07-15 | NEEDS_WORK               | (phase 6 Interface/Routing 設計) CLI flag 体系・exit code・Request/Response 変換・`output.RegisteredFormats()` 提案は D1-D11 と実装コードに整合。ただし `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md:113,231` (durable 正本、2026-07-14 sync 済み) が「methodSymbol.metadata は #21/#22 双方の対象外」という override 前の記述のままで、本 spec の D11 拡張と矛盾。フェーズ6を「完了」としているが備考は「レビュー待ち」で状態と実態が不一致 | 対応済 (feature doc 2 箇所を override 後の内容へ先行更新、#21 index.md:470 の反映済み行も同期、フェーズ6を進行中に修正) |
| 2026-07-15 | PASS                     | (phase 6 再レビュー) 前回指摘 3 件 (feature doc override 反映・フェーズ6状態不一致・#21 反映済み行同期) すべて解消を確認、新たな不整合なし。#21 index.md の変更履歴に今回の再同期を追記すべきという非ブロッキング補足あり                                                                                                                                                                                                                                         | 対応済 (#21 変更履歴に追記)                                                                                             |

## 変更履歴

| 日付       | 変更者         | 変更内容                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ---------- | -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-12 | spec-lifecycle | scaffold 作成                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 2026-07-12 | spec-lifecycle | scaffold レビュー指摘対応 (EARS 記述追加・フェーズ表同期)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| 2026-07-12 | spec-lifecycle | scaffold 再レビュー PASS                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 2026-07-12 | clarify        | D1 (method selector 書式) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 2026-07-12 | clarify        | D2 (CLI 構造: analyze への flag 追加) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 2026-07-12 | clarify        | D3 (探索方向 flag: --direction 既定 caller) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 2026-07-12 | clarify        | D4 (深さ上限 flag: --max-depth 既定無制限) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 2026-07-12 | clarify        | D5 (出力形式 flag: --format 既定 console) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 2026-07-12 | clarify        | D6 (解析モード: 常時 fullGraph・use case 設定) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 2026-07-12 | clarify        | D7 (Entrypoints: 非使用・CLI 非露出) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 2026-07-12 | Claude         | #21 の D6 から論点引き継ぎ。D11 (call edge metadata の CLI 出力表出方法) を新規追加                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 2026-07-12 | clarify        | D8 (exit code: 0/1/2 の 3 区分) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 2026-07-12 | clarify        | D9 (E2E: os/exec バイナリ起動 + golden 照合) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 2026-07-12 | clarify        | D10 (規約 path による既定解決: 見送り、ADR-0003 無改訂) を確定                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| 2026-07-12 | clarify        | D11 (call edge metadata: JSON のみ透過) を確定。全論点解決、論点整理 phase 完了                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 2026-07-12 | clarify        | clarify レビュー指摘対応 (メタ情報同期・用語統一)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 2026-07-12 | clarify        | clarify 再レビュー PASS (論点整理 phase 完了)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 2026-07-14 | Claude         | #21 (`specs/21-java-dispatch-spring-di/`) の D9 で D11 の前提 (Core が metadata を破棄する) を code level (`core/internal/graph/convert.go`) で独立に裏付け。D11 に追記注記を追加: `graph.Symbol`/`methodSymbol.metadata` 側は D11 の対象外であることを明記し、次 phase (Interface/Routing 設計) での検討事項として記録。関連資料の #21 参照を merge 状況の最新化に合わせて更新                                                                                                                                                                                                                                                                                                                    |
| 2026-07-15 | Claude         | `feature/22` を `origin/develop` (#21 PR #25 マージ済み) へ rebase。rebase 後の再検証で 2026-07-14 の追記注記が誤りだったと判明: `methodSymbol.metadata.declaringType`/`inherited` は spec #9 D11 (帰属型の決定規則) 起源で Analyzer が既に出力しており、`specs/21-java-dispatch-spring-di/index.md` D9 (2026-07-14、レビュー PASS 済み) は「`methodSymbol.metadata` 側は #21/#22 双方の対象外、将来の別 issue へ」と既に確定していた。ユーザー判断によりこの切り分けを override し、D11 を拡張して `graph.Symbol`/`output.NodeView` への Metadata 透過も本 spec のスコープに含めることを確定 (#21 index.md D9 にも override の追記を反映)。実装対象表・feature doc 影響・関連資料・メタ情報を同期 |
| 2026-07-15 | Claude         | D11 拡張分 clarify 再オープンの spec-review 指摘対応 (NEEDS_WORK 2 件): 本表の 2026-07-15 レビュー記録追加、`specs/21-java-dispatch-spring-di/index.md` フェーズ5備考を D9 override 後の内容に同期                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 2026-07-15 | Claude         | phase 6 (Interface / Routing 設計): CLI flag 体系表 (D1-D5)・`--format` 検証用の `output.RegisteredFormats()` 公開 API 化 (D5 拡張)・exit code 配線 (D8)・CLI→traversal.Request→output.Write の変換手順・D11 拡張に伴う `output.NodeView.Metadata` の後方互換追加を記述。機能仕様の Routing/URL State (D2 のコマンド構造) も記入。備考の引き継ぎメモ 2 件を解消。レビュー待ち                                                                                                                                                                                                                                                                                                                      |
| 2026-07-15 | Claude         | phase 6 spec-review 指摘対応 (NEEDS_WORK 1件): `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` (durable 正本、L3/L113/L231) が override 前の「methodSymbol.metadata は #21/#22 双方の対象外」のままだった矛盾を解消 (override 後の内容へ更新)。`specs/21-java-dispatch-spring-di/index.md` の対応する反映済み行も同期。フェーズ6の状態を「完了」から「進行中」に修正 (レビュー未了のため)                                                                                                                                                                                                                                                                                       |
| 2026-07-15 | Claude         | phase 6 再レビュー PASS。フェーズ6を「レビュー済」に更新、`specs/21-java-dispatch-spring-di/index.md` の変更履歴に今回の再同期を追記 (非ブロッキング補足対応)。論点整理〜Interface/Routing 設計 phase 完了                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |

## 備考

appendix は取り込まない (CLI ツールであり API / DB / 認可 / 画面 / data-testid のいずれにも該当しない)。

後続 phase への引き継ぎメモ:

- ~~Interface 設計時に output の登録済み Format 列挙 API の公開を明示する (clarify 再レビュー補足)。~~ → phase 6 (Interface / Routing 設計) で `output.RegisteredFormats()` の公開 API 化として解消済み (2026-07-15)。
- specs/21 の参照は #21 merge 後に解決する (clarify 再レビュー補足) → 2026-07-15 develop rebase で解消済み (#21 は PR #25 でマージ済み)。
- ~~Interface 設計時に `graph.Symbol`/`output.NodeView` の Metadata フィールド追加が Node 側の公開 API (JSON スキーマ) に与える影響も合わせて明示する (D11 拡張、2026-07-15)。~~ → phase 6 の「Props / Request / Response」節で `output.NodeView.Metadata` の後方互換な追加として明示済み (2026-07-15)。

phase 7 (Content/Data 設計) への引き継ぎメモ:

- Content / Data 設計節では「CLI のみで永続ストアなし」の該当なし判断を明示する想定 (#21 index.md の同種判断を踏襲)。
