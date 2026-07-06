# Testing Conventions

> 最終更新: 2026-06-27

テストの横断規約。feature 固有のテスト観点は各 [design/features/](../design/features/) に置く。プロジェクト固有のテストコマンドは [context/project.md](project.md)。

Core の test framework は Go 標準の `testing` とする。
判断根拠は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## テスト責務の分担

| 種別              | 配置                                                              | 主担当範囲                                                                                                                                    |
| ----------------- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Unit test         | `core/internal/...` / Analyzer                                    | Graph / Traversal / Output のロジック、JSONL parse / validate、探索打ち切り (Q4)                                                              |
| Protocol contract | `testdata/analyzer-protocol/` と Core / Analyzer の contract test | `analysisRequest`、`MethodSymbol` / `CallEdge` / `SourceLocation`、`diagnostic` / `error`、versioning、process contract の JSONL スキーマ準拠 |
| E2E (照合)        | `testdata/fixtures/` のサンプル Java/Spring repo                  | 既知の caller/callee 集合と CLI 出力の一致 (S1/S2)、各出力形式のパース可否 (S3)                                                               |

## テスト runtime contract

- E2E は **サンプル Java/Spring プロジェクト**を fixture とし、既知の呼び出し関係集合と CLI 出力を照合する (DesignDoc 成功条件の測定方法)。
- Core ↔ Analyzer は別プロセスのため、JSONL 入出力を境界とした **contract test** を analyzer-protocol 側に置き、Analyzer 実装はこの契約に対してテストする。Protocol / SPI / Model schema の正本は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) と [ADR-0001](../adr/0001-analyzer-protocol-jsonl-spi.md)。spec #8 は issue 単位の決定記録であり、横断的な contract test 観点は本書を正本とする。
- Core の unit test / contract test は `cd core && go test ./...` で実行できる状態を保つ。
- Mock は手書き fake / interface stub で開始する。
- Golden fixture は `testdata/` 配下に置く。
- `testify`、mock generator、`github.com/google/go-cmp/cmp` は初期導入しない。`go-cmp` は graph / Protocol record の deep diff が読みにくくなった時、mock generator は同一 interface の fake が複数 test package に重複した時に検討する。
- E2E の具体 CLI 引数、env 変数、対象選択は後続の CLI interface spec で確定する。

## テスト構造

- テストは仕様単位の `Test...` 関数に分ける。1 つの巨大な table-driven test に、異なる仕様の invalid case を混在させない。
- table-driven test は、同じ仕様に対する具体例の列挙に使う。`Test...` 関数名は検証する仕様、`t.Run` の subtest 名は具体的な入力条件や境界値を表す。
- Protocol / contract test は、失敗時に壊れた契約が test output から分かる構造にする。fixture 検証では `request` / `stdout` / `stderr` / `exit-code` など、契約境界ごとに subtest を分ける。
- helper は assertion や fixture 生成の重複削減に使う。ただし、helper 名や table の抽象化で仕様名が隠れる場合は分割を優先する。
- `go test -run '<TestName>/<case>'` で仕様または具体例を絞り込める命名にする。

## Protocol contract test

Analyzer Protocol / SPI の contract test は、実装スタック確定前でも以下の観点を正本とする。

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
- 未解決 symbol が `diagnostic` として表現され、未解決 callee を参照する `callEdge` が valid edge として扱われないこと。
- valid `methodSymbol` / `callEdge` record と embedded `SourceLocation` value object を Core が parse / validate できること。
- Java 固有情報を `metadata` に含む record でも、Core が共通必須 field のみで graph を構築できること。

## 横断テスト方針

- リリース判定には最低限 S1 (caller) / S2 (callee) / S3 (各出力形式) の E2E 照合を含める。
- 新 Analyzer 追加時は Protocol contract test の通過を必須とする (S5: Core 無変更の担保)。
