# Core implementation foundation technology selection spec

> Core 実装言語、package manager、task runner、test framework、初期 directory / package 構成を決めるための issue 単位の作業文書。最終的な durable な判断は ADR と context に handoff する。

## メタ情報

- Issue: `#11`
- ステータス: `Draft`
- 作成日: 2026-06-15
- 更新日: 2026-06-21
- Branch: `feature/11`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態   | 最終更新   | 備考 |
| --- | --------------------------- | ------ | ---------- | ---- |
| 1   | 起票                        | 完了   | 2026-06-15 | GitHub issue #11 を確認済み |
| 2   | 下書き                      | 完了   | 2026-06-15 | 本 spec を scaffold |
| 3   | 上位文書突合                | 完了   | 2026-06-15 | Design Doc / feature doc / context / ADR を確認 |
| 4   | 論点整理                    | 完了   | 2026-06-15 | D1-D7 を初期論点として列挙 |
| 5   | 論点解決                    | 進行中 | 2026-06-21 | D1-D4 を解決済み。D5-D7 は継続 |
| 6   | Interface / Routing 設計    | 未着手 |            | 非 UI / CLI package boundary として扱う |
| 7   | Content / Data 設計         | 未着手 |            | 初期 module / package 構成を扱う |
| 8   | Performance / Security 設計 | 未着手 |            | CLI 配布、外部送信なし、read-only 解析を確認 |
| 9   | Test / Metrics 設計         | 未着手 |            | test framework と quality gate を決める |
| 10  | 実装分割                    | 未着手 |            | Spec8 の実装 prompt 生成前に確定する |
| 11  | レビュー済                  | 未着手 |            | `spec-review` 未実施 |

## 上位文書整合

正本 ([Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。本プロダクトは統合モードのため独立した `PRD.md` は持たず、Why / What は Design Doc に統合されている。

- PRD 更新要否: 不要 (統合モードのため `design/DesignDoc.md` の Why / What を参照)
- Design Doc 更新要否: 未定 (Core 実装基盤の決定が Design Doc の Alternatives / Open Questions に影響する場合は要)
- ADR 起票要否: 要 (Core 実装言語 / toolchain / 初期 module 構成の判断を ADR 化する)

| 上位文書    | 節 / 該当箇所 | 整合方針 (継承 / 補足 / 変更提案) |
| ----------- | ------------- | --------------------------------- |
| PRD         | 統合モードのため `design/DesignDoc.md` の Why / What | 継承 |
| Design Doc  | 設計原則 P1-P4 / Alternatives Considered A1 / Communication Protocol / Future Work | 補足 |
| feature doc | `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` の「やらないこと」(Core 実装言語、package manager、test framework は定義しない) | 補足 |
| context     | `context/architecture.md` Package Boundary / Runtime Boundary | 継承 |
| context     | `context/toolchain.md` 標準スタック / 採用方針 | 変更提案 |
| context     | `context/testing.md` テスト責務の分担 / テスト runtime contract | 変更提案 |
| context     | `context/engineering.md` Shared Config Boundary / Root Task Boundary / Repository Quality Gate | 変更提案 |
| context     | `context/project.md` Repository Map / Quick Commands / 対象ドメイン | 変更提案 |
| context     | `context/infrastructure.md` CLI バイナリ / パッケージ配布、外部送信なし | 補足 |
| ADR         | `adr/0001-analyzer-protocol-jsonl-spi.md` | 継承 |
| ADR         | Core 実装基盤 ADR (新規) | 変更提案 |

> `変更提案` は Issue 11 の完了条件そのもの。論点解決後、下流 phase に進む前に `spec-sync` で ADR / context へ handoff する。

## 関連資料

- `design/DesignDoc.md`: Core 言語非依存、Analyzer 独立プロセス、CLI 限定、Alternatives A1、Future Work
- `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`: Analyzer Protocol / SPI / Model schema の正本。Core 実装言語・package manager・test framework は対象外
- `adr/0001-analyzer-protocol-jsonl-spi.md`: JSONL over STDIN/STDOUT の process SPI 判断
- `context/project.md`: 対象ドメイン、Issue Tracker、Source of Truth、Branch pattern、Quick Commands
- `context/architecture.md`: Core -> Analyzer は Protocol 境界のみ、Core は Analyzer 内部を知らない
- `context/toolchain.md`: Core 実装言語 / package manager / task runner / test framework は未定
- `context/testing.md`: Unit / Protocol contract / E2E の責務分担
- `context/engineering.md`: 実装スタック確定後に shared config / root task / quality gate を定義する
- `context/infrastructure.md`: CLI バイナリ / パッケージ配布、local / CI 実行、外部送信なし
- `specs/8-analyzer-protocol/`: Protocol / SPI / Model schema は決定済み。Core 実装基盤は未確定事項として残っている
- 関連 issue / ticket: [#11](https://github.com/Fukuemon/depwalk/issues/11)

## 背景

Spec8 で Analyzer Protocol / SPI / Model schema の契約設計は完了した。次に Core 実装へ進むには、Core 実装言語、package manager、task runner、test framework、初期 directory / package 構成を確定する必要がある。

この spec は、Core を言語非依存に保つという Design Doc の設計原則 P1-P4 と、Analyzer Protocol を JSONL process SPI とする ADR-0001 を前提に、実装基盤の技術選定を issue #11 の作業正本として整理する。決定後は ADR と context に durable な判断を handoff し、Spec8 の実装 prompt 生成へ進める状態にする。

## スコープ

### やること

- Core 実装言語の候補を比較し、1 つを選定する。
- package manager、task runner、test framework、formatter / linter の初期方針を決める。
- `analyzer-protocol` 実装の配置方針を決める。
- 初期 module / package / directory 構成案を決める。
- 決定理由と却下案を ADR に昇格する。
- `context/project.md`、`context/toolchain.md`、`context/testing.md`、`context/engineering.md` の更新方針を決める。

### やらないこと

- Analyzer Protocol / SPI / Model schema を再設計しない。正本は feature doc と ADR-0001。
- Java Analyzer の AST 解析、型解決、DI 解決方式を決めない。
- Traversal / Output の feature 詳細仕様を決めない。
- Runtime Trace、APM、Reflection、AspectJ Runtime、実行時 Proxy 解析を扱わない。
- IDE Plugin / Web UI / サーバ常駐の提供形態を扱わない。

## 要件の解釈

### 実現したいユーザー価値

Core 開発者は、追加質問なしに最初の実装 scaffold、Protocol contract test、Graph / Traversal / Output の実装へ着手できる。Analyzer 実装者は、Core と共有する `analyzer-protocol` の配置と検証コマンドを一意に参照できる。

### 成功条件

- Core 実装言語が決まっている。
- package manager、task runner、test framework が決まっている。
- `analyzer-protocol` 実装の配置方針が決まっている。
- 初期 module / package / directory 構成案が決まっている。
- 技術選定 ADR が作成されている。
- `context/project.md` / `context/toolchain.md` / `context/testing.md` / `context/engineering.md` が更新されている。
- Spec8 の実装 prompt 生成に進める状態になっている。

### 対象ユーザー / 操作主体

- Core 開発者
- Analyzer 実装者
- depwalk CLI を local / CI で実行する開発者
- spec / ADR / context を保守する設計者

EARS 風で振る舞いを記述する。

- WHEN Core 実装を scaffold する時、開発者は ADR で承認された Core 実装言語と package manager を使用する。
- WHEN Core と Analyzer の境界を実装する時、システムは ADR-0001 と Analyzer Protocol feature doc に定義された JSONL process SPI を変更せずに実装する。
- WHEN 新しい Analyzer を追加する時、Core は Analyzer の内部 runtime / library に直接依存しない。
- IF 選定候補が Core に特定 Analyzer runtime への直接依存を要求する時、その候補は Design Doc P1-P4 と矛盾するため採用しない。
- IF 選定候補の CLI 配布が重い時、採否判断では local / CI での導入負荷を明示的に評価する。
- THE SYSTEM SHALL keep Core independent from Analyzer implementation language and runtime.

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点 | 決定候補 | 決定 |
| --- | ---- | -------- | ---- |
| D1  | Core 実装言語を何にするか | Rust / Go / TypeScript(Node.js) / Kotlin 以外の JVM 言語 / その他 | Go を採用する |
| D2  | package manager と dependency 管理を何にするか | D1 の言語に従属。例: Cargo / Go modules / npm 系 | Go modules を採用し、初期の runtime dependency は `github.com/spf13/cobra` のみに抑える |
| D3  | task runner と root command をどう定義するか | 言語標準 task / make-like wrapper / package manager scripts | 初期は Go 標準 command (`go test` / `go vet` / `go fmt` / `go mod tidy`) を root command とし、make-like wrapper は後続で必要になった時に検討する |
| D4  | test framework と contract test の配置をどうするか | 言語標準 test / dedicated test runner / golden fixture | `testing` を採用し、手書き fake / golden fixture / Protocol contract test で開始する。`testify` / mock generator / `go-cmp` は初期導入しない |
| D5  | `analyzer-protocol` の実装配置をどうするか | Core 内 package / 独立 package / schema + generated types | 未決 |
| D6  | 初期 directory / package 構成をどう切るか | CLI / Core / Model / Analyzer SPI / Traversal / Output / fixtures の分割案 | 未決 |
| D7  | ADR / context へどの順序で handoff するか | ADR 作成後に context 更新 / spec-sync で同時反映 | 未決 |

## 解決済みの論点

- D1: Core 実装言語は Go を採用する。single binary 配布、local / CI での導入容易性、JSONL streaming と外部プロセス制御の標準ライブラリ対応、Core を Analyzer runtime から独立させる設計原則 P1-P4 との相性を重視した。
- D2: dependency 管理は Go modules を採用する。初期の runtime dependency は CLI framework の `github.com/spf13/cobra` のみに抑え、Analyzer Protocol / JSONL / process 実行 / graph / output / test は標準ライブラリと内部実装で開始する。
- D3: task runner は初期導入しない。root command は Go 標準 command (`go test ./...`、`go vet ./...`、`go fmt ./...`、`go mod tidy`) とし、repository-level の wrapper は command 数や CI matrix が増えた時に再検討する。
- D4: test framework は Go 標準の `testing` を採用する。mock は手書き fake / interface stub を標準方針とし、`testify`、`go.uber.org/mock`、`github.com/google/go-cmp/cmp` は初期導入しない。`go-cmp` は graph / Protocol record の deep diff が読みにくくなった時、mock generator は同一 interface の fake が複数 test package に重複した時に検討する。

## Go 側ライブラリ選定

### 結論

Core は Go 標準ライブラリを中心に実装する。初期導入する runtime dependency は `github.com/spf13/cobra` のみに抑える。

Cobra は CLI の subcommand、help、completion、POSIX flags を担う。JSONL、外部プロセス実行、graph 表現、text / JSON / Mermaid 出力、unit test / contract test は標準ライブラリと小さな内部実装で開始する。Java Analyzer の AST 解析、型解決、DI 解決に使う library は Go 側 Core に含めない。

### 採用するライブラリ

| 分類 | ライブラリ | 採用度 | 理由 |
| --- | --- | ---: | --- |
| CLI フレームワーク | `github.com/spf13/cobra` | 5 | `depwalk analyze ...` のような subcommand、help、completion、POSIX flags を表現しやすい。CLI ツールとして必要な機能に限定して採用する |
| 設定・flags 管理 | Cobra 内蔵の `pflag` | 4 | 初期要件は CLI flags で足りる。設定ファイル / env binding は要件化されていないため `viper` は採用しない |
| lint | `golangci-lint` | 4 | CI の品質 gate として利用する。runtime dependency ではなく開発ツールとして扱う |
| セキュリティ補助 | `golang.org/x/vuln/cmd/govulncheck` | 3 | 依存脆弱性チェック用。CI への追加候補とし、runtime dependency にはしない |

### 標準ライブラリで対応するもの

| 分類 | 標準ライブラリ | 理由 |
| --- | --- | --- |
| ロギング | `log/slog` | CLI の verbose / debug logging と構造化 diagnostics には標準で足りる |
| JSON 入出力 | `encoding/json`, `bufio`, `io` | Analyzer Protocol は JSONL streaming 前提のため、逐次読み込みと struct validation で扱う |
| 外部プロセス実行 | `os/exec`, `context`, `io`, `bytes` | Analyzer process の起動、stdin close、stdout streaming、stderr summary、exit code、timeout を扱える |
| グラフ表現 | `map`, `slice`, 内部 struct | `MethodSymbol` / `CallEdge` を node / edge として保持し、caller / callee BFS / DFS を実装する範囲では専用 graph library は不要 |
| 出力フォーマット | `fmt`, `strings`, `text/template`, `encoding/json` | text / JSON / Mermaid は renderer ではなく文字列 / JSON 出力の責務であり、標準で足りる |
| テスト | `testing`, `testing/fstest`, `os`, `path/filepath` | unit / golden / fixture / contract test を表現できる |
| mock | 手書き fake / interface stub | 初期は Analyzer runner や filesystem 境界に小さい interface を切り、手書き fake で検証する |
| format | `gofmt`, `go fmt` | Go 標準 formatter を正とする |
| 静的確認 | `go test`, `go vet`, `go mod tidy` | 最小の local / CI quality gate として成立する |

### 検討するもの

| タイミング | 候補 | 理由 |
| --- | --- | --- |
| CLI command 数、completion、docs 生成が増えた時 | `cobra-cli` | Cobra scaffold 補助。初期は手書きで足りる |
| graph / Protocol record の deep diff が読みにくくなった時 | `github.com/google/go-cmp/cmp` | golden / fixture test の失敗差分を読みやすくする。初期 dependency にはしない |
| 同一 interface の fake が複数 test package に重複した時 | `go.uber.org/mock` | mock generator の導入余地。初期は手書き fake を優先する |
| Graph algorithm が SCC、最短経路、複雑な到達解析へ広がった時 | `gonum.org/v1/gonum/graph` | 初期の caller / callee traversal には過剰 |
| 設定ファイル / env / precedence が要件化した時 | `github.com/spf13/viper` | 現時点では CLI flags で足りる |
| multi-platform binary 配布を自動化する時 | `goreleaser` | release automation 用。初期 scaffold では不要 |
| dependency license / supply chain gate が必要になった時 | `go-licenses`, `osv-scanner` | 依存が増えてから導入判断する |

### go get が必要なもの

```bash
go get github.com/spf13/cobra@latest
```

開発ツールは `go get` ではなく、CI / tools 管理で version を固定する。

```bash
go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@latest
go install golang.org/x/vuln/cmd/govulncheck@latest
```

### 導入しない方針のもの

| 候補 | 方針 | 理由 |
| --- | --- | --- |
| `viper` | 初期導入しない | 設定ファイル / env binding が要件化されていない |
| `zap`, `zerolog` | 導入しない | `log/slog` で足りる |
| `testify` | 初期導入しない | 標準 `testing` と小さな helper で開始し、assertion DSL への依存を避ける |
| `go.uber.org/mock` | 初期導入しない | 手書き fake で開始し、fake の重複が実害になった時に検討する |
| `github.com/google/go-cmp/cmp` | 初期導入しない | deep diff の可読性が問題になった時に検討する |
| graph 専用 library | 初期導入しない | graph model は Protocol model と密接に結びつくため、内部 struct の方が制御しやすい |
| JSON schema validator | 初期導入しない | Go struct + `Validate()` で開始し、外部 Analyzer 互換性の問題が増えた時に再検討する |
| Java 解析 library | Go 側には導入しない | Java 解析は Analyzer 独立プロセス側の責務。Core は JSONL Protocol のみを扱う |

## 未確定事項

| 未確定事項 | 決定者 | 期限 | 下流への影響 |
| ---------- | ------ | ---- | ------------ |
| `analyzer-protocol` 実装配置 | Fukuemon | Core scaffold 前 | Core / Analyzer の依存境界をコード上に落とせない |
| 技術選定 ADR の内容 | Fukuemon | `spec-sync` 前 | context の標準スタックを正本化できない |

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務 |
| ------------------- | :------: | -------- |
| `traversal`         |    ◯     | Core 実装基盤の対象。探索 engine の配置先と test 方針を決める |
| `output`            |    ◯     | Core 実装基盤の対象。出力 engine の配置先と test 方針を決める |
| `analyzer-protocol` |    ◯     | Protocol schema / parser / validator / contract test の配置方針を決める |
| `java-analyzer`     |    -     | 本 spec では Core 側との境界だけ参照し、Java 固有実装は後続 spec で扱う |

## 機能仕様

### User Flow

1. 設計者は Issue 11 と上位文書を確認し、Core 実装基盤の評価軸を確定する。
2. 設計者は候補技術を CLI 配布、Core 言語非依存性、Analyzer 追加時の Core 無変更、JSONL process SPI との相性、test / build 運用の明確さで比較する。
3. 設計者は採用案と却下案を決め、ADR に判断理由を記録する。
4. 設計者は `context/toolchain.md`、`context/testing.md`、`context/engineering.md`、`context/project.md` を更新する。
5. 実装者は更新後の context を参照して、Spec8 の実装 prompt 生成と初期 scaffold へ進む。

### Reuse Policy

- Protocol / SPI / Model schema の正本は `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` とし、本 spec では再定義しない。
- Core 内 package は `context/architecture.md` の依存方向に従う。
- Java Analyzer 固有の build / runtime 設定を Core 共通 package に持ち込まない。
- `analyzer-protocol` に置く共有実装は、Core と Analyzer の双方が使っても Design Doc P1-P4 を破らない範囲に限定する。

### Performance

- 採用候補は local / CI での cold start、install size、single binary または package 配布の容易さを評価する。
- Core ↔ Analyzer は streaming JSONL を扱うため、全 Analyzer stdout の一括読み込みを前提にしない runtime / library を選ぶ。
- 具体的な runtime budget は実装後の測定で確定し、必要なら context / ADR に追記する。

### Routing / URL State

- 非該当。depwalk は CLI ツールであり、Web routing / URL state を持たない。

### Content / Assets

- 非該当。静的 asset やコンテンツ配信は扱わない。

### UI Reuse

- 非該当。IDE Plugin / Web UI は Non Goals。

### Testing

- Core の unit test は Graph / Traversal / Output / Protocol parser / validator の責務ごとに配置する。
- Protocol contract test は `analyzer-protocol` 境界に置き、Core と Analyzer 実装が同じ contract を参照できるようにする。
- E2E はサンプル Java/Spring fixture を使い、既知 caller / callee 集合と CLI 出力を照合する。
- 初期 test framework は Go 標準の `testing` とし、assertion library / mock generator は導入しない。
- mock は手書き fake / interface stub を使う。`go-cmp` は graph / Protocol record の deep diff が読みにくくなった時に検討する。
- test command は `context/project.md` の Quick Commands と `context/testing.md` に反映する。

## Interface 設計

### UI / API / Event Interface

- UI: 非該当。
- Web API endpoint: 非該当。
- CLI command interface: 初期 scaffold 後に `depwalk` CLI の引数設計 spec で扱う。本 spec では build / test / quality gate の開発者向け command を決める。
- Process interface: ADR-0001 と Analyzer Protocol feature doc の JSONL over STDIN/STDOUT を継承する。

### Props / Request / Response

- Protocol request / response schema は本 spec では再定義しない。
- Core 実装基盤の決定は、Protocol schema の必須 field や record type を変更してはならない。
- `analyzer-protocol` 実装配置を決める際は、Core と Java Analyzer の双方が schema / fixture / contract test を参照できる依存方向にする。

## Content / Data 設計

### 保存・管理するデータ

- 永続データストアは持たない。
- 管理対象は source code、package manifest、test fixture、contract test fixture、ADR、context の文書である。
- 解析対象 repository は read-only とし、depwalk は対象 repository を書き換えない。

### コンテンツ配置 / package / route

- 初期 directory / package 構成は D6 で決める。
- 現時点の候補分割は `cli`、`core`、`analyzer-protocol`、`traversal`、`output`、`fixtures`。
- Web route / asset route は非該当。

## Performance / Security 設計

### Performance

- 技術選定では CLI 配布の軽さ、CI 上の導入時間、JSONL streaming 処理の実装容易性を比較軸に含める。
- Analyzer process 起動 overhead は ADR-0001 の既知トレードオフとして受け入れる。Core 実装基盤は timeout / stderr 上限 / record size 上限を後続 runtime config で扱える余地を残す。
- runtime dependency は初期状態で `github.com/spf13/cobra` のみに抑え、dependency restore と supply chain risk を小さく保つ。

### Security / Privacy

- depwalk は利用者自身の source code を local / CI 上で read-only に解析する。
- 外部送信、常駐サーバ、secret / token は扱わない。
- dependency supply chain risk は package manager 選定時に評価する。

## Error / Fallback 設計

### エラーケース

| #   | ケース | ユーザーへの見せ方 | リカバリ |
| --- | ------ | ------------------ | -------- |
| 1   | 選定候補が Core から Analyzer 実装への直接依存を要求する | Design Doc P1-P4 との不整合として却下理由を ADR に記録する | Protocol 境界を保てる候補へ切り替える |
| 2   | 選定候補の配布方式が local / CI に重すぎる | CLI 配布の軽さの評価で不利として記録する | single binary / 軽量 runtime / package 配布の代替を評価する |
| 3   | test framework が Protocol contract test を表現しづらい | test 方針の不適合として記録する | fixture / golden test を扱える framework を選ぶ |
| 4   | context 更新が ADR の決定内容とずれる | `spec-sync` / review で差分を指摘する | ADR を正として context を同期する |

### Fallback

- `analyzer-protocol` 実装配置が決まらない場合、Spec8 の実装 prompt 生成には進まない。
- 初期 directory / package 構成が決まらない場合、Core scaffold は行わず、D6 の未確定事項として残す。
- context 更新が広がる場合、ADR を先に確定し、context は ADR へのリンクを正本として追従させる。

## テスト / 評価方針

### テスト観点

- 候補技術が unit test、Protocol contract test、E2E fixture 照合を無理なく表現できること。
- Core -> Analyzer の JSONL parser / validator を単体で検証できること。
- Analyzer process 起動、stdin close、stdout streaming、stderr handling、exit code handling を integration / contract test で検証できること。
- `testing` と手書き fake だけで失敗原因が読めること。graph / Protocol record の deep diff が読みにくい場合は `go-cmp` を追加検討すること。
- `context/project.md` の Quick Commands に、実装者が迷わず実行できる build / test / quality gate command を記載できること。

### 計測指標

- CLI install / setup 手順の少なさ。
- CI cold start と dependency restore の見通し。
- single binary または package 配布の実現性。
- Protocol contract test の fixture 作成コスト。
- Analyzer 追加時に Core の内部実装差分が不要であること。

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

| Phase | 対象 | 概要 | 依存 |
| ----- | ---- | ---- | ---- |
| P1 | spec | D5-D7 を `spec-resolve` で解決する | D1-D4 |
| P2 | ADR | Core 実装基盤の技術選定 ADR を作成する | D1-D7 |
| P3 | context | `toolchain` / `testing` / `engineering` / `project` を更新する | ADR |
| P4 | prompts | Spec8 の実装 prompt 生成へ進める | ADR / context handoff |

### prompts 生成方針

- `analyzer-protocol` の prompt は Protocol schema / parser / validator / contract test を中心に切る。
- `traversal` と `output` の prompt は Core package 構成確定後に別 spec / prompt で切る。
- `java-analyzer` は本 spec の直接実装対象にしない。Analyzer Protocol に準拠する後続実装として扱う。

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。`spec-track` / `spec-sync` で更新する。

### PRD への影響

| 対象節 | 変更内容 | 理由 |
| ------ | -------- | ---- |
| なし | 独立 PRD はなく、Design Doc の Why / What を継承する | 統合モードのため |

### Design Doc への影響

| 対象節 | 変更内容 | 理由 |
| ------ | -------- | ---- |
| Alternatives Considered / Open Questions | Go 採用判断と Kotlin 不採用理由を反映する可能性がある | Design Doc の A1 / toolchain 未定状態を解消するため |

### feature doc への影響

| 対象 doc / 節 | 変更内容 | 理由 |
| ------------- | -------- | ---- |
| `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` | 原則不要。Protocol schema は変更しない | Core 実装基盤は Protocol の実装配置を決めるだけで、schema 正本を変更しないため |

### context への影響

| 対象 doc / 節 | 変更内容 | 理由 |
| ------------- | -------- | ---- |
| `context/project.md` Repository Map / Quick Commands | 初期 directory 構成と build / test / quality gate command を追記する | 実装着手前の未定項目を解消するため |
| `context/toolchain.md` 標準スタック / 採用方針 | Core 実装言語、package manager、task runner、formatter / linter、test framework を確定する | 技術選定結果を横断正本へ反映するため |
| `context/toolchain.md` Go 側 dependency 方針 | 初期 runtime dependency は `github.com/spf13/cobra` のみ、開発ツールは `golangci-lint` / `govulncheck` を候補として追記する | 依存最小化と CLI 品質 gate を両立するため。source: spec-resolve D1-D4 |
| `context/testing.md` テスト runtime contract | 採用 test framework と実行 command、contract test 配置を追記する | Spec8 実装 prompt 生成に必要なため |
| `context/testing.md` mock / assertion 方針 | 初期は `testing`、手書き fake、golden fixture を採用し、`testify` / mock generator / `go-cmp` は初期導入しない方針を追記する | 依存を増やさずに contract test を開始するため。source: spec-resolve D4 |
| `context/engineering.md` Shared Config / Root Task / Quality Gate | shared config と root task、依存境界検査の初期方針を追記する | 実装 repo としての quality gate を定義するため |

### ADR の新規 / 更新

| ADR ID | 変更内容 | 理由 |
| ------ | -------- | ---- |
| 新規 | Core 実装言語、package manager、task runner、test framework、初期 module 構成の採用判断を記録する | 長期判断として issue 終了後も残るため |
| ADR-0001 | 原則更新なし。必要なら Core 実装基盤から見た影響範囲へのリンクを追加する | JSONL process SPI 判断は既に承認済みであり、再判断しないため |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付 | 結果 (PASS / NEEDS_WORK) | 指摘要点 | 対応 |
| ---- | ------------------------ | -------- | ---- |
|      |                          |          |      |

## 変更履歴

| 日付 | 変更者 | 変更内容 |
| ---- | ------ | -------- |
| 2026-06-21 | Codex | Go 側 Core ライブラリ選定を追加し、D1-D4 を解決済みに更新 |
| 2026-06-15 | Codex | Issue #11 の spec draft を作成 |

## 備考

- 本 spec は API endpoint、永続 DB、認可、画面、data-testid を扱わないため、`templates/specs/appendices/` の追加 appendix は取り込まない。
