---
type: context
title: "Engineering Conventions"
description: shared config / root task / repository quality gate の境界規約
keywords: [quality gate, lefthook, CI, コメント規約, shared config]
governs:
  - lefthook.yml
  - .github/workflows
  - scripts
  - hooks
verified_commit: 4455e86
---

# Engineering Conventions

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.yml](project.yml)。

Core 実装基盤を定めるのは [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## Code Comment Boundary

### 何をどこに書くか

情報の置き場を次のとおり分ける。**コードから読み取れることをコメントに書き写さない。**

| 置き場           | 書くこと                                                |
| ---------------- | ------------------------------------------------------- |
| コード           | **How** — どう実現しているか。コード自身が語る          |
| テストコード     | **What** — 何が成り立つべきか                           |
| commit / PR      | **Why** — なぜこの変更をしたか                          |
| コード内コメント | **Why not** — なぜ他の手を採らなかったか                |
| 関数・型の doc   | **What** — 何をするものか (公開 API は godoc / javadoc) |

コード内コメントの主役は **Why not** である。「この実装がどう動くか」はコードを読めば分かる。読んでも分からないのは「なぜ素直な方法を採らなかったか」であり、それを書かないと後から不用意に「単純化」されて壊れる。

書く価値があるのは次のような内容。

- 採らなかった実装とその理由 (「A にすると B が壊れるため採らない」)
- 外部仕様・ライブラリの制約による回避 (「この API は X を返さないため自前で持つ」)
- 一見冗長に見える処理が必要な理由
- 破ると壊れる不変条件

書かないもの。

- コードを日本語へ言い換えただけの行 (`// 名前を取得する` の直後に `getName()`)
- 引数・戻り値の型を繰り返すだけの doc
- 変更の経緯・issue 番号 (commit と ADR が持つ)

関数・型の doc は例外で、**What を書く**。公開 API は godoc / javadoc の規約に従う。ただし「何をするか」で足り、内部の手順を書き写さない。

### 言語

- **コード内コメントは日本語で書く。** 設計文書が日本語であり、同じ語彙で意味論を書けるほうが読み手の切り替えコストが小さい
- ドキュメント・commit・PR も日本語
- **ユーザーに見える文字列リテラル (CLI 出力・エラーメッセージ・JSONL の値) は言語を変えない。** 観測可能な契約であり golden test が固定している
- 識別子・型名・API 名は原語のまま使う

### 参照の張り方

- **コメントから他文書への参照を書かない** (spec / issue / ADR / design / context のいずれも)。理由はコメント自身に平易な日本語で完結させ、リンクで説明を代替しない。どの doc がそのコードを統べるかは design doc の `governs:` で、決定の経緯は git history と PR で辿る
- `(S5)` のような符号だけの参照はしない。読み手に伝わる言葉で書き、必要なら決まりへのリンクを添える

## Error Boundary

- **エラーメッセージの package prefix はユーザーへの到達可否で決める**。`traversal:` / `output:` / `analyze:` のような prefix は「呼び出し側の実装ミス」を表す内部不変条件の違反にだけ付ける (これらは cli が事前検証しており、利用者には到達しない)。利用者の stderr に出るメッセージには内部 package 名を出さない。
- **exit code への分類は cli が決める**。内層は「利用者の入力が原因」であることを型で表明するだけにし、exit code の値を知らない (`cli.ExitCode` が `analyze.InputError` と cli 自身の入力エラーの双方を 2 へ写す)。
- **プロセスの結果を struct フィールドの `error` で運ぶときは、判定を型のメソッドへ畳む**。`analyze.Outcome` は fatal record / 非ゼロ exit / stdout 検証エラーの 3 つを持つが、優先順位 (fatal の理由を検証エラーで上書きしない) を呼び出し側に再実装させないよう `Outcome.Err()` に集約する。
- 型で分類する必要があるエラーは struct として定義し、`errors.As` で検査できる状態を保つ (例: `protocol.ValidationError` は contract test が型として検証している)。単に文言を組み立てるだけなら `fmt.Errorf` でよい。

## Shared Config Boundary

- Core の初期 shared config は Go 標準 command を優先し、専用 config を増やさない。
- `golangci-lint` は依存方向検査 (depguard) のために導入済み。設定は `core/.golangci.yml` (有効な linter は depguard のみ)、実行の入口は `scripts/golangci-lint.sh` に一本化し、lefthook と CI は同じスクリプトを呼ぶ。バージョンはこのスクリプト内で pin する: 固定手段に `go run <pkg>@<version>` を使い、`go.mod` の tool directive にはしない (linter の依存木が production module の `go.mod` / `go.sum` へ入るのを避けるため。ADR-0002 の依存最小方針)。`govulncheck`、release automation の設定は、CI gate または release 手順が要件化した時点で追加する。
- 現状の共有契約はドキュメントの決まりの path (`context/project.yml` の Source of Truth) と AI 設定 (`.rulesync/` → 各 provider 生成)。

## Root Task Boundary

- commit 前検査は `lefthook` (pre-commit hook) が束ねる。設定は repo root の `lefthook.yml`。
- Core の初期 root task は repository-level wrapper ではなく、`core/` 配下で実行する Go 標準 command とする。
- make-like wrapper は、複数 module、Analyzer build、CI matrix、release command を 1 command に束ねる必要が出た時点で導入を検討する。Java Analyzer は `analyzers/java/` の `gradlew` で完結し、現時点で repository-level wrapper は導入しない (再評価条件は上記のまま)。

## Repository Quality Gate

- 現状の gate: Markdown 整形と文書検査 (lefthook / CI 経由)。AI 設定は sdd-template が中央で生成し symlink で配る。本リポジトリでは追跡せず、手元での編集も反映されない (次の `make sync` で戻る)。
- 文書検査は CI の Docs job が 4 種を実行する。強度は「機械的に一意導出できるものは FAIL、判定が不確実なものは通知のみ」で分ける。判定が不確実な検査を FAIL にすると、内容を読まずに通す抜け道が唯一の運用手段になり gate が形骸化するためである。

| 検査             | 対象                                        | 除外                                                                | 強度     |
| ---------------- | ------------------------------------------- | ------------------------------------------------------------------- | -------- |
| 相対リンクの切れ | 追跡ファイルの相対リンク                    | コードフェンス / コードスパンの中、`templates/`                     | FAIL     |
| 一文の長さ       | textlint + sentence-length (max 140)        | `BlockQuote` / `ListItem` / `Table` / `CodeBlock` / `Code` / `Html` | FAIL     |
| 生成物の drift   | 依存図・読み取りマップを再生成して diff     | なし                                                                | FAIL     |
| 文書本文の鮮度   | `verified_commit` から `governs` 配下の差分 | なし                                                                | 通知のみ |

- リンク検査でコードフェンスとコードスパンを外すのは、索引の出力例のようにリンクの**書式**を示すために書かれたものを切れリンクと数えないためである。`templates/` を外すのは、テンプレ内のリンクがテンプレの**配置先**からの相対 path であり、テンプレ自身の位置からは解決しないためである
- 一文の長さで表と箇条書きを外すのは、表の 1 セルや箇条書きの 1 項目を 140 文字に収めるために内容を削ると、かえって情報が落ちるためである。散文を読み下せる長さに保つのが目的であり、構造化して並べたものはその対象外とする
- 鮮度検査は pre-commit に載せない。commit を跨いで初めて意味を持つ検査であり、毎 commit で `git log` を回す価値がない
  - [README.md](README.md) の 検査の強度 — 鮮度検査の判定方法と `verified_commit` の運用を定める
- Core 実装後の最小 gate: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"`、`cd core && go mod tidy` 後の差分確認。
- 依存境界 gate: Core から `analyzers/<language>/` や特定 Analyzer runtime へ直接依存しないことを CI の Go job で検査する (`go list -deps ./...` に `analyzers/` が含まれないこと。`.github/workflows/ci.yml`)。
- 依存方向 gate (Go): `core/internal` の package 単位の禁止 import (例: `graph` / `traversal` / `analyze` / `output` → `protocol` / `cli` 等) を golangci-lint + depguard で検査し、lefthook pre-commit / CI に組み込む。ルールは `files` (package の glob) + `deny` + `desc` (違反理由) の宣言形式で書く。違反時は `desc` の理由が表示されるので、開発者・AI エージェントはメッセージだけで是正できる。依存規則を定めるのは [architecture.md](architecture.md) の Package Boundary (導入判断は [ADR-0007](../adr/0007-layered-architecture-refactor.md))。あわせて依存図の生成 (`scripts/depgraph.sh` → architecture.md の生成マーカー区間) と再生成 drift 検査を同 gate に含める。
- 依存方向 gate (Java): 外部ライブラリ隔離 (`sootup.*` は `analysis/sootup` のみ / `org.gradle.*` は `discovery` のみ / `com.github.javaparser.*` は `analysis` 配下のみ) を ArchUnit の JUnit テストで検査する (`ArchitectureTest`)。既存の `./gradlew test` (quality gate 組み込み済み) で実行され、新しい gate 配線は不要。Gradle は `org.gradle.tooling.*` ではなく `org.gradle.*` 全体を禁止する: gradle-tooling-api の jar は `org.gradle.api` / `util` / `internal` も同梱するため、tooling 配下だけの禁止では隔離をすり抜ける (#35 実測)。

## 参照

- [toolchain.md](toolchain.md): 標準 toolchain と build 構成
- [architecture.md](architecture.md) の Package Boundary: 依存方向の規則
- [README.md](README.md) の 文書メタ情報と鮮度: frontmatter の schema と鮮度検査の運用
- [project.yml](project.yml): commands / quality gate の固有値
- [ADR-0002](../adr/0002-core-implementation-foundation.md): Core 実装基盤に Go と Go modules を採用した決定
- [ADR-0007](../adr/0007-layered-architecture-refactor.md): 依存境界を ACL と機械検査で固定した決定
