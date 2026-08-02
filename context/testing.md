---
type: context
title: "Testing Conventions"
description: test の責務分担と test runtime contract
keywords: [testing, unit test, E2E, golden, fixture]
governs:
  - core/e2e
  - testdata
  - analyzers/java/src/test
verified_commit: 9654928
---

# Testing Conventions

テストの横断規約。feature 固有のテスト観点は各 [design/features/](../design/features/) に置く。プロジェクト固有のテストコマンドは [context/project.yml](project.yml)。

Core の test framework は Go 標準の `testing` とする。
判断根拠は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## テスト責務の分担

| 種別              | 配置                                                              | 主担当範囲                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| ----------------- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Unit test         | `core/internal/...` / Analyzer                                    | Graph / Traversal / Output のロジック、JSONL parse / validate、探索打ち切り (Q4)。Java Analyzer 側は `analyzers/java/` で JUnit を用いる (三層構成は下記「Java Analyzer 三層」参照)                                                                                                                                                                                                                                                                                                                                         |
| Protocol contract | `testdata/analyzer-protocol/` と Core / Analyzer の contract test | `analysisRequest`、`MethodSymbol` / `CallEdge` / `SourceLocation`、`diagnostic` / `error`、versioning、process contract の JSONL スキーマ準拠                                                                                                                                                                                                                                                                                                                                                                               |
| E2E (照合)        | `testdata/fixtures/` のサンプル Java/Spring repo                  | 既知の caller/callee 集合と CLI 出力の一致 (S1/S2)、各出力形式のパース可否 (S3)。S1/S2 は Traversal Engine 層の到達集合照合 ([feature doc](../design/features/traversal/DesignDoc_traversal.md) が定める) と CLI 出力照合の 2 層からなり、CLI 出力照合の設計は確定済み ([CLI feature doc](../design/features/cli/DesignDoc_cli.md) が定める)・完成は CLI 層が担う。S3 も同様に Output Engine 層の照合 ([feature doc](../design/features/output/DesignDoc_output.md) が定める。unit / golden) と CLI 出力照合の 2 層からなる |

## テスト runtime contract

- E2E は 2 層からなる: Traversal 層は `testdata/fixtures/traversal/` の graph 入力 + 期待集合 JSON fixture で到達 node / edge 集合を照合し (実装済み)、CLI 層は **サンプル Java/Spring プロジェクト**を fixture として既知の呼び出し関係集合と CLI 出力を照合する (DesignDoc 成功条件の測定方法。設計は確定済みで [CLI feature doc](../design/features/cli/DesignDoc_cli.md) が定め、完成は #22 実装フェーズが担う)。
- Core ↔ Analyzer は別プロセスのため、JSONL 入出力を境界とした **contract test** を analyzer-protocol 側に置き、Analyzer 実装はこの契約に対してテストする。Protocol / SPI / Model schema を定めるのは [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) と [ADR-0001](../adr/0001-analyzer-protocol-jsonl-spi.md)。横断的な contract test 観点は本書が定める。
- Core の unit test / contract test は `cd core && go test ./...` で実行できる状態を保つ。
- Mock は手書き fake / interface stub で開始する。
- Golden fixture は `testdata/` 配下に置く。repo root の `testdata/` に加え、Go 慣習の **package-local `testdata/`** (例: `core/internal/output/testdata/golden/`) も可とする (単一 package に閉じる golden は package-local を優先する)。
- `testify`、mock generator、`github.com/google/go-cmp/cmp` は初期導入しない。`go-cmp` は graph / Protocol record の deep diff が読みにくくなった時、mock generator は同一 interface の fake が複数 test package に重複した時に検討する。
- E2E の具体 CLI 引数・対象選択は確定済み (flag 体系・exit code を定めるのは [CLI feature doc](../design/features/cli/DesignDoc_cli.md))。CLI 出力照合 (S1-S3) の完成は #22 の実装フェーズが担う。

### Java Analyzer 三層

Java Analyzer (`analyzers/java/`) は Java unit test / Go process contract / 実 jar E2E の三層でテストする。判断根拠と feature 固有観点を定めるのは [Java Analyzer feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md)。

| 層                     | 配置                                                          | JVM 要否               |
| ---------------------- | ------------------------------------------------------------- | ---------------------- |
| Java unit test (JUnit) | `analyzers/java/`                                             | 要 (Java job)          |
| Go process contract    | `core/internal/analyzer` + fake analyzer                      | **不要** (fake で代替) |
| E2E (実 jar)           | `testdata/fixtures/java/` のサンプル Java/Spring プロジェクト | 要 (JDK 25 + jar)      |

- Go 側の unit / contract test は fake analyzer (任意の実行可能ファイル) で回せるため、CI の Go job に JVM を要求しない。
- E2E だけが JDK 25 + build 済み fat jar を要求する。CI は「Go job (JVM 不要)」と「Java + E2E job (JDK 25 / Gradle build)」に分ける。

## path 比較は real path 基準 (macOS symlink)

macOS では `/tmp` と `/var/folders` が `/private` 配下への symlink である。JUnit の `@TempDir` / `Files.createTempDirectory` / Go の `t.TempDir()` はいずれも symlink 側の path を返す。一方 Gradle model や `toRealPath()` 済みの source root は `/private/...` を返す。片側だけ real 化して `relativize` すると `../../private/...` のような壊れた相対 path になる。これが record path の破損や glob 不一致として現れる。

- production 契約: Analyzer は `workspaceRoot` と source root を **両方 real path 化してから** 相対化する。
- test 側: 一時 directory を workspace として使うときは作成直後に `.toRealPath()` してから request / 期待値の基準にする。JavaParser の CU storage path も real path の file を parse に渡して揃える。

## テスト構造

- テストは仕様単位の `Test...` 関数に分ける。1 つの巨大な table-driven test に、異なる仕様の invalid case を混在させない。
- table-driven test は、同じ仕様に対する具体例の列挙に使う。`Test...` 関数名は検証する仕様、`t.Run` の subtest 名は具体的な入力条件や境界値を表す。
- Protocol / contract test は、失敗時に壊れた契約が test output から分かる構造にする。fixture 検証では `request` / `stdout` / `stderr` / `exit-code` など、契約境界ごとに subtest を分ける。
- helper は assertion や fixture 生成の重複削減に使う。ただし、helper 名や table の抽象化で仕様名が隠れる場合は分割を優先する。
- `go test -run '<TestName>/<case>'` で仕様または具体例を絞り込める命名にする。
- **公開 API だけを検証するテストは black-box (`package <pkg>_test`) にする** (Go 公式 style guide)。非公開の関数・seam を触る必要があるときだけ同一パッケージに置く。black-box テストは自パッケージを import するため、依存方向 gate (depguard) の deny は前方一致の一括指定を避け完全一致 (`$`) で列挙する — さもないと外部テストパッケージが自分自身を import できない (#34 で検出)。
- **複数パッケージのテストから使う fixture builder は、対象パッケージ配下のテスト支援 sub-package に置く** (`core/internal/graph/graphtest`。Go 標準の `net/http/httptest` と同じ配置)。本番パッケージの公開面をテスト都合で広げないための分離であり、本番コードから import しない (depguard で検査)。

## Protocol contract test

Analyzer Protocol / SPI の contract test は、実装スタック確定前でも以下の観点が定める。

- valid `analysisRequest` record を Analyzer が受け取れること。
- `analysisRequest.include` / `analysisRequest.exclude` が `workspaceRoot` からの相対 path glob 配列として扱われ、絶対 path、空文字、`..` を含む path が schema 不準拠として拒否されること。
- `analysisRequest.entrypoints` の method selector object が `qualifiedName` 必須、`signature` 任意として検証されること。
- `analysisRequest.entrypoints` 未指定または空配列の場合に、scope 全体の call graph 生成要求として扱われること。
- `analysisRequest.analysisMode` 未指定時に `fullGraph` として扱われること。
- Core が `analysisRequest` 送信後に stdin を close すること。
- Analyzer stdout の JSONL record が逐次 parse / validate されること。
- Analyzer stdout に invalid UTF-8 を含む JSONL record が出た場合、Core が invalid record として拒否すること。
- Analyzer stdout に duplicate key を含む JSON object が出た場合、Core が invalid record として拒否すること。
- Protocol field 名の大小文字違いを別 field として扱い、`schemaVersion` の代わりに `schemaversion` が出た場合は必須 field 欠落として拒否すること。
- Protocol 上 array / object と定義する field を Core が出力する場合、nil slice / nil map 由来の `null` を出力しないこと。
- Analyzer stderr が protocol record として parse されないこと。
- exit code `0` を成功、非ゼロを fatal failure として扱うこと。
- 複数 request が必要な場合、Core が request ごとに Analyzer process を起動すること。
- `methodSymbol` / `callEdge` が 0 件の正常解析を success として扱えること。
- `methodId` / `edgeId` が同一 Analyzer 実装 version、同一 request、同一 source content で決定的に再生成されること。
- `schemaVersion` が protocol 全体 version として全 record に必須であること。
- Analyzer が `analysisRequest` の未知 field を無視できること。
- Core が Analyzer response record の未知 field を無視できること。
- 未対応 major version の record を Core が schema version mismatch として拒否できること。
- 必須 field の削除、型変更、意味変更を非互換変更として検出できること。
- valid `diagnostic` record を Core が利用者へ伝播し、`diagnostic` だけを理由に fatal failure としないこと。
- valid `error` record を Core が fatal failure として扱うこと。
- Analyzer が `error` record 出力後に非ゼロ exit code で終了すること。
- valid `error`、非ゼロ exit、parse / schema error が先行 graph record と diagnostic をすべて無効化し、staging Graph を公開しないこと。正常 stream だけが参照完全性を満たすこと。
- 未解決 symbol が `diagnostic` として表現され、未解決 callee を参照する `callEdge` が valid edge として扱われないこと。
- valid `methodSymbol` / `callEdge` record と embedded `SourceLocation` value object を Core が parse / validate できること。
- Java 固有情報を `metadata` に含む record でも、Core が意味を解釈せず Graph の Symbol / Edge へ nested value を deep copy できること。Traversal は表出せず、Output は JSON の `nodes[].metadata` / `edges[].metadata` (optional、omitempty) として意味解釈なしに透過表出すること (表出を定めるのは [output feature doc](../design/features/output/DesignDoc_output.md))。
- `error.details` の共通 fieldを決定順で保持し、Analyzer 固有 code に分岐せず CLI が汎用表示できること。

## Gradle multi-project / 完全性の横断テスト

- Protocol contract: optional `sourceRoots`、workspace 相対 path、`.`、empty / absolute / `..` rejection、fatal request の原子性、共通 `FailureDetail`、optional symbol location / opaque metadata。
- Java unit / integration: Tooling API discovery、custom model、`main` source set、root normalize / realpath / dedup / nesting、project dependency context、language level / preview、parse pre-flight、resolver allowlist、source attribution、call-site driven bytecode member index、initializer caller 展開、call inventory / ledger、`JAVA_INCOMPLETE_ANALYSIS` details。
- Go process / Graph: valid record の staging Graph への 1-pass 変換、wire DTO 非保持、metadata deep copy、成功時だけ公開、fatal 時の Graph / diagnostic 破棄、正常 stream の参照完全性、generic CLI failure 表示。
- Required E2E: app / service / repository の3 project、変更 `projectDir`、custom source dir、project 間 call / DI を含む fixture。実 Core CLI と実 Analyzer jar を test-only 透過 proxy で接続し、自動 discovery と明示 override の graph、request、exit を照合する。
- Compatibility / security: Gradle `7.6.5`〜`9.6.x` と daemon JVM anchor matrix、Gradle stdout / stderr の隔離、credential / URL / absolute path を含む negative fixture の非漏洩を検証する。
- Performance: 明示 single-root、自動 single-project、自動 multi-project の初回と warm 3回中央値、および discovery phase 別時間を記録する。数値 SLO は別 issue の契約に従う。

## 横断テスト方針

- リリース判定には最低限 S1 (caller) / S2 (callee) / S3 (各出力形式) の E2E 照合を含める。
- **2 つ目以降**の Analyzer 追加時は Protocol contract test の通過を必須とする (S5: Core を変えずに済むことの確認。初号機導入時の言語非依存な初回配線は対象外)。
