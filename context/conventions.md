---
type: context
title: コードと文書の規約
description: Go と JUnit のテストの命名、コメントに書くこと、文書の置き場と frontmatter、golangci-lint と lefthook の設定、チェックの除外。lint で検出できる規則は書かない
status: draft
governs:
  - package.json
  - lefthook.yml
  - .textlintrc.json
  - .prettierignore
  - commitlint.config.mjs
  - core/.golangci.yml
  - scripts/golangci-lint.sh
  - .github/workflows
verified_commit: unverified
---

# コードと文書の規約

## Naming

- Go の package の名前は、小文字の短い単語で、責務を表す名前にする。`core/internal/core` のように、親と同じ名前を重ねない。
- 識別子、型、API の名前は、日本語に訳さずに原語のまま使う。
- 利用者に見える文字列リテラルは、コメントや文書を日本語にするときにも変えない。CLI の出力、エラーメッセージ、JSONL の値が当たる。
  - これらは観測できる契約で、golden テストが固定している。

Go のテストは、名前で仕様と具体例を絞り込めるように分ける。

- `Test...` の関数は、仕様ごとに分ける。異なる仕様の不正なケースを、1 つの大きな table-driven テストに混ぜない。
- table-driven テストは、同じ仕様の具体例を並べるのに使う。
- `Test...` の関数の名前は検証する仕様を表し、`t.Run` のサブテストの名前は具体的な入力の条件や境界の値を表す。
- `go test -run '<TestName>/<case>'` で、仕様か具体例を絞り込める名前にする。
- Protocol と contract のテストは、失敗したときに、守られなかった契約がテストの出力から分かるように作る。fixture の検証では、`request`、`stdout`、`stderr`、`exit-code` のように、契約の境界ごとにサブテストを分ける。
- helper は、assertion と fixture の生成の重複を減らすのに使う。helper の名前や table の抽象化で仕様の名前が隠れるときは、helper にせずに分ける。

JUnit のテストには、日本語の `@DisplayName` を付ける。

- test class と、その中のすべての `@Test` に付ける。`*TestSupport` や fake のようなテストを支えるクラスには付けない。
- class の `@DisplayName` には、検証の対象と境界を書く。例: `event publish から listener への呼び出し関係 (edge) の生成`
- method の `@DisplayName` は、「〈条件〉のとき、〈観測できる結果〉になる」の 1 文にする。
- 識別子をそのまま並べず、契約を日本語で言い切る。protocol の値を引くときだけ、括弧で添える。例: `曖昧 (ambiguous) になる`
- method の名前は英語のままにする。Gradle の `--tests` で絞り込むためで、Go の `go test -run` で絞り込める名前にするのと同じ理由である。

## Comments

コードに書いたことを、コメントに書き写さない。  
情報は次のように分けて置く。

- コード: どう実現しているか
- テストのコード: 何が成り立つべきか
- コミットと pull request: なぜこの変更をしたか
- コードの中のコメント: なぜ他の方法を採らなかったか
- 関数と型の doc: 何をするものか。公開する API は godoc と javadoc の規約に従う。

コードの中のコメントには、主に「なぜ他の方法を採らなかったか」を書く。  
コードを読めば動き方は分かるが、素直な方法を採らなかった理由は分からない。  
理由を書かないと、後から単純に書き換えられて動かなくなる。

- 書くもの: 採らなかった実装とその理由、外部の仕様やライブラリの制約を避ける方法、冗長に見える処理が要る理由、破ると動かなくなる不変条件
- 書かないもの: コードを日本語に言い換えただけの行、引数と戻り値の型を繰り返すだけの doc、変更の経緯、issue の番号
- 関数と型の doc には、何をするかだけを書き、内部の手順を書き写さない。

コメントは日本語で書く。

- 設計の文書が日本語なので、同じ語で意味を書くほうが読み手の切り替えが少ない。
- 文書、コミット、pull request も日本語で書く。

コメントから、他の文書を参照しない。

- spec、issue、ADR、Design Doc、context のどれも参照しない。理由はコメントの中に平易な日本語で書き切り、リンクで説明に代えない。
- どの文書がそのコードを説明するかは、文書の frontmatter の `governs` でたどる。決定の経緯は Git の履歴と pull request でたどる。
- 名前で書けば伝わる内容を、`(S5)` のような符号だけで指さない。

## Documents

文書の置き場は次のとおり。

- プロダクトの Why と What は、ルートの `PRD.md` に書く。
- 全体の設計は `design/DesignDoc.md` に置く。
- 機能ごとの設計は、対象のコードのディレクトリに `DesignDoc.md` として置く。1 つの機能の文書を分けるときは、下位の package のディレクトリに `DesignDoc.md` を置く。
- issue ごとの作業の文書は `specs/<issue 番号>-<slug>/` に置き、issue を閉じるときに消す。

ADR は、決定した時点の記録として書き換えない。  
対応するバージョンの範囲のように、決定の後に変わる値は、context か Design Doc に書き、ADR からはリンクする。

`context/` と `design/` の文書の frontmatter は、次の規則で書く。

- `governs` には、その文書が説明する契約の実装の場所をすべて挙げる。文書を置いた package だけでなく、本文が記録する値の出所（build の定義、wrapper の properties）も含める。
- コードに直接ひも付かない文書は、説明する対象の設定ファイルを `governs` に挙げる。
- `verified_commit` には、実装と本文を読み比べた作業のコミットを書く。読み比べていない文書は `unverified` と書く。
  - すべての文書に一律で HEAD を書かない。すべての文書が実装と一致しているという、事実と違う宣言になる。

## Shared Configuration

Core の共有の設定は、Go の標準のコマンドを優先し、専用の設定ファイルを増やさない。

- golangci-lint の設定は `core/.golangci.yml` に置き、depguard だけを有効にする。
- golangci-lint は `scripts/golangci-lint.sh` からだけ実行し、lefthook と CI は同じスクリプトを呼ぶ。

リポジトリのタスクは、それぞれのディレクトリの標準のコマンドで実行する。

- Core は `core/` の中で Go の標準のコマンドを実行する。
- Java Analyzer は `analyzers/java/` の `gradlew` で完結させる。
- リポジトリ全体のラッパー（make のようなもの）は置かない。複数の module、Analyzer の build、CI の matrix、リリースのコマンドを 1 つのコマンドにまとめる必要が出たときに、入れるかを決める。

Git のフックで動かすコマンドは `lefthook.yml` にある。  
チェックの内容と直し方は [CONTRIBUTING.md](../CONTRIBUTING.md#コミットするとき) にある。

- 整形した結果は、lefthook の `stage_fixed` で staging へ戻す。`git add` を書くと、一部だけ stage したファイルの、stage していない変更までコミットに入る。
- 複数のコマンドを続けて実行するときは、`&&` でつなぐ。lefthook の `run` は `sh -c` で動き、`set -e` が働かない。複数の行に分けると最後のコマンドの終了コードだけが残り、前のコマンドの失敗を見逃す。
- depguard は、Go のコードかその設定を変えたときだけ動かす。ファイルを消しただけのコミットでは動かないため、CI の Go の job の同じチェックで補う。

## Check Exclusions

- CI の textlint は、pull request で変えた Markdown にだけ掛ける。既存の文書に textlint の指摘が残っているためである。
- `list-drift.mjs` による文書の鮮度のチェックは、CI で一覧を出すだけにし、失敗にしない。
  - `governs` の範囲が変わっても文書が正しいままのことはよくある。失敗にすると、内容を読まずに `verified_commit` だけを進めて通すことになる。
  - コミットをまたいで初めて意味を持つチェックなので、コミットの前のフックには入れない。
- prettier は、`.prettierignore` で次を除く。
  - apm が配置して書き換えるファイル。整形すると、次の `apm install` で差分が出る。
  - Gradle の fixture の build の生成物
- 文章の長さのチェックから、表と箇条書きを除くかは未定。
