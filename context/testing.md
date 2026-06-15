# Testing Conventions

> 最終更新: 2026-06-10

テストの横断規約。feature 固有のテスト観点は各 [design/features/](../design/features/) に置く。プロジェクト固有のテストコマンドは [context/project.md](project.md)。

> 実装着手前のため test framework / コマンドは未定。ここでは **成功条件 (DesignDoc S1〜S5) から導く責務分担**を先に定める。

## テスト責務の分担

| 種別              | 配置                         | 主担当範囲                                                                      |
| ----------------- | ---------------------------- | ------------------------------------------------------------------------------- |
| Unit test         | Core 各モジュール / Analyzer | Graph/Traversal/Output のロジック、JSONL シリアライズ、探索打ち切り (Q4)        |
| Protocol contract | analyzer-protocol            | `analysisRequest`、`MethodSymbol` / `CallEdge` / `SourceLocation`、`diagnostic` / `error`、versioning、process contract の JSONL スキーマ準拠 |
| E2E (照合)        | サンプル Java/Spring repo    | 既知の caller/callee 集合と CLI 出力の一致 (S1/S2)、各出力形式のパース可否 (S3) |

## テスト runtime contract

- E2E は **サンプル Java/Spring プロジェクト**を fixture とし、既知の呼び出し関係集合と CLI 出力を照合する (DesignDoc 成功条件の測定方法)。
- Core ↔ Analyzer は別プロセスのため、JSONL 入出力を境界とした **contract test** を analyzer-protocol 側に置き、Analyzer 実装はこの契約に対してテストする。Protocol / SPI / Model schema の正本は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md)。
- 具体的な env 変数 / 対象選択 / 実行コマンドは実装スタック確定時に追記する。

## Protocol contract test

Analyzer Protocol / SPI の contract test は、実装スタック確定前でも以下の観点を正本とする。

- valid `analysisRequest` record を Analyzer が受け取れること。
- `analysisRequest.include` / `analysisRequest.exclude` が `workspaceRoot` からの相対 path glob 配列として扱われ、絶対 path、空文字、`..` を含む path が schema 不準拠として拒否されること。
- `analysisRequest.entrypoints` の method selector object が `qualifiedName` 必須、`signature` 任意として検証されること。
- `analysisRequest.entrypoints` 未指定または空配列の場合に、scope 全体の call graph 生成要求として扱われること。
- `analysisRequest.analysisMode` 未指定時に `fullGraph` として扱われること。
- Core が `analysisRequest` 送信後に stdin を close すること。
- Analyzer stdout の JSONL record が逐次 parse / validate されること。
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
