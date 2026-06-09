# Testing Conventions

> 最終更新: 2026-06-10

テストの横断規約。feature 固有のテスト観点は各 [design/features/](../design/features/) に置く。プロジェクト固有のテストコマンドは [context/project.md](project.md)。

> 実装着手前のため test framework / コマンドは未定。ここでは **成功条件 (DesignDoc S1〜S5) から導く責務分担**を先に定める。

## テスト責務の分担

| 種別              | 配置                         | 主担当範囲                                                                      |
| ----------------- | ---------------------------- | ------------------------------------------------------------------------------- |
| Unit test         | Core 各モジュール / Analyzer | Graph/Traversal/Output のロジック、JSONL シリアライズ、探索打ち切り (Q4)        |
| Protocol contract | analyzer-protocol            | `MethodSymbol` / `CallEdge` / `SourceLocation` の JSONL スキーマ準拠 (Q1)       |
| E2E (照合)        | サンプル Java/Spring repo    | 既知の caller/callee 集合と CLI 出力の一致 (S1/S2)、各出力形式のパース可否 (S3) |

## テスト runtime contract

- E2E は **サンプル Java/Spring プロジェクト**を fixture とし、既知の呼び出し関係集合と CLI 出力を照合する (DesignDoc 成功条件の測定方法)。
- Core ↔ Analyzer は別プロセスのため、JSONL 入出力を境界とした **contract test** を analyzer-protocol 側に置き、Analyzer 実装はこの契約に対してテストする。
- 具体的な env 変数 / 対象選択 / 実行コマンドは実装スタック確定時に追記する。

## 横断テスト方針

- リリース判定には最低限 S1 (caller) / S2 (callee) / S3 (各出力形式) の E2E 照合を含める。
- 新 Analyzer 追加時は Protocol contract test の通過を必須とする (S5: Core 無変更の担保)。
