# ADR-0001: Analyzer Protocol を JSONL over STDIN/STDOUT の process SPI とする

## 状態

承認

## 決定日

2026-06-15

## 背景

depwalk は Core を言語非依存に保ち、言語ごとの差異を独立プロセスの Analyzer に閉じ込める。Phase1 は Java Analyzer を対象にするが、将来 Kotlin / TypeScript / Vue / Go Analyzer を追加しても Core を変更しないことを成功条件にする。

Core が Analyzer の内部ライブラリや言語ランタイムに依存すると、Analyzer 追加時に Core の build / runtime / release 境界が膨らむ。Core と Analyzer の結合点を protocol に限定する必要がある。

## 決定

Core と Analyzer は別プロセスとし、Analyzer SPI は JSONL over STDIN/STDOUT を共通 protocol とする。

- Core は 1 `analysisRequest` ごとに Analyzer process を 1 つ起動する。
- Core は Analyzer の stdin に `analysisRequest` record を 1 件送信し、その後 stdin を close する。
- Analyzer は stdout に `methodSymbol` / `callEdge` / `diagnostic` / `error` record を JSONL で逐次出力する。
- stderr は人間向け diagnostics とし、Core は protocol record として parse しない。
- exit code `0` は成功、非ゼロは fatal failure とする。
- 全 record の `schemaVersion` は protocol 全体の major version を表す。Phase1 は `"1"` とする。
- Core は対応済み major version の未知 field を無視し、未対応 major version を拒否する。
- 任意 field の追加は互換変更、必須 field の追加・削除、field 型変更、field 意味論変更、record type 削除は breaking change とする。

Protocol / Model の具体 schema は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) を正本とする。決定経緯は [spec #8](../specs/8-analyzer-protocol/) に残す。

## 代替案

- Analyzer を Core にライブラリとして組み込む。
  - 却下理由: Analyzer ごとの言語ランタイム依存が Core に混在し、将来の Analyzer 追加で Core 変更が必要になる。
- Core と Analyzer を同一プロセス化する。
  - 却下理由: 初期実装は単純になるが、TypeScript / Vue / Go など異なる runtime の Analyzer を追加しづらい。
- capability handshake / session reuse / incremental analysis を Phase1 protocol に含める。
  - 却下理由: Analyzer 実装と contract test が複雑になる。Phase1 は 1 request = 1 process に限定し、必要性が測定された後に拡張する。

## 影響

### 良い影響

- Core は Analyzer の内部ライブラリや言語ランタイムを知らずに済む。
- 新しい Analyzer を追加しても Core の内部実装差分を避けやすい。
- JSONL はテキストで観測でき、debug と contract test の入力に使いやすい。
- stdout streaming により、大規模解析結果を一括読み込みせず処理できる。

### 悪い影響 / トレードオフ

- process 起動と IPC の overhead が発生する。
- Analyzer process の timeout、stderr 上限、record サイズ上限などの runtime config が必要になる。
- session reuse や incremental analysis を初期 protocol に含めないため、短時間に多数 request を投げる用途では後続拡張が必要になる可能性がある。

### 影響範囲

- 対象モジュール / package: `analyzer-protocol`, `java-analyzer`, `traversal`, `output`

## 実装・運用への反映

- spec 更新要否: 要。spec #8 の durable 成果を feature doc / ADR 正本へハンドオフし、spec 側は決定時スナップショットへ降格する。
- context / AI 向け設定更新要否: 要。`context/testing.md` に protocol contract test の正本観点を追記する。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): Core 言語非依存、Analyzer 独立プロセス、Communication Protocol
- [design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md): Protocol / SPI / Model schema の正本
- [specs/8-analyzer-protocol](../specs/8-analyzer-protocol/): 決定経緯と issue 単位の作業記録
