---
type: context
title: 技術スタック
description: Go と Java のツールの役割とバージョンの所在、依存を足す条件、主なコマンド、Gradle、SootUp、JDK を上げるときの確認、Analyzer の作り始め。バージョンそのものと選んだ理由は書かない
status: stable
governs:
  - mise.toml
  - package.json
  - core/go.mod
  - scripts/golangci-lint.sh
  - analyzers/java/build.gradle.kts
  - analyzers/java/settings.gradle.kts
  - analyzers/java/model-provider/build.gradle.kts
  - analyzers/java/gradle/wrapper/gradle-wrapper.properties
verified_commit: unverified
---

# 技術スタック

## Toolchain

ツールのバージョンは次のファイルにある。

- `mise.toml`: Go、Java（JDK）、Node.js、pnpm、uv、apm
- `core/go.mod`: Core の Go のモジュールと依存
- `analyzers/java/build.gradle.kts`: Java Analyzer の依存のライブラリ、Shadow のプラグイン、Analyzer が動く JDK の toolchain
- `analyzers/java/model-provider/build.gradle.kts`: Gradle の custom model provider が compile に使う Gradle API と Java の release
- `analyzers/java/gradle/wrapper/gradle-wrapper.properties`: Java Analyzer を build する Gradle
- `scripts/golangci-lint.sh`: golangci-lint
- `package.json`: 文書とコミットのメッセージのチェックのツール

Core の実装の基盤は ADR が持つ。  
ADR-0002: [Core 実装基盤](../adr/0002-core-implementation-foundation.md)

Core（`core/`）のツールの役割は次のとおり。

- Go: Core の CLI、graph、traversal、output、protocol を実装する。
- Go modules: `core/go.mod` を module の manifest にする。
- Go の標準のコマンド: build、test、vet、fmt をタスクとして実行する。make のようなラッパーは置かない。置く条件は [conventions.md](conventions.md#shared-configuration) にある。
- `github.com/spf13/cobra`: CLI のコマンドと flag を組み立てる。
- `go vet`: Go の静的なチェックを行う。
- golangci-lint: depguard だけを有効にし、package の依存の向きをチェックする。規則は [codebase.md](codebase.md#dependency-rules) にある。
- `gofmt`: Go のコードを整形する。Go の標準の formatter を正とする。
- Go の標準の `testing`: unit テスト、contract テスト、E2E を実行する。E2E は `core/e2e` にある。

Java Analyzer（`analyzers/java/`）のツールの役割は次のとおり。

- Java: Java Analyzer を実装する。Kotlin は使わない。
  - Kotlin を使わない理由は [Java Analyzer の Design Doc](../analyzers/java/DesignDoc.md#実装基盤) にある。
- Gradle（Kotlin DSL）と `gradlew`: Java Analyzer を build し、テストする。
- Shadow のプラグイン: Java Analyzer を 1 つの fat jar にする。Core は `java -jar` の 1 つのコマンドで起動する。
- JavaParser: ソースを構文木にする。
- SymbolSolver: 呼び出しの型を解決する。
- SootUp: 型の階層と、override と interface の実装の候補を引く索引にだけ使う。call graph の生成には使わない。
  - ADR-0005: [SootUp と Spring DI の解決](../adr/0005-adopt-sootup-and-spring-di-resolution.md)
- Gradle Tooling API: source root を自動で discovery するときに、対象の build の model を取得する。
  - ADR-0006: [Gradle Tooling API による discovery](../adr/0006-adopt-gradle-tooling-api-discovery.md)
- custom model provider（`analyzers/java/model-provider`）: Gradle の daemon の上で読み込まれ、build の model を返す。古い Gradle の daemon でも読み込めるよう、Java 8 の classfile にする。
- JUnit: Java Analyzer の unit テストを実行する。
- ArchUnit: Java Analyzer の package が外部のライブラリを import してよい範囲をチェックする。

リポジトリのルートのツールの役割は次のとおり。

- mise: ランタイムとツールのバージョンを `mise.toml` で固定する。
- pnpm: ルートの `package.json` のツールを入れる。ルートの `package.json` は文書とコミットのメッセージのチェックのツールだけを持つ。
- lefthook: Git のフックからチェックを実行する。
- prettier、textlint、commitlint: Markdown と設定ファイルを整形し、文書とコミットのメッセージをチェックする。
- apm: agent-harness のスキルとフックを配置する。

依存を足すときは、次の条件に従う。

- Core は Go の標準のライブラリと内部の package を優先する。JSONL、外部のプロセスの実行、graph、Console と JSON の出力、テストは、標準のライブラリで書く。
- Core の実行時の依存は Cobra だけにする。設定ファイルと環境変数の binding が要件になるまで、`viper` を入れない。
- JSONL の parse と validate は `encoding/json` で書く。
  - `encoding/json` の v1 は、重複したキーを受け入れ、不正な UTF-8 を置き換え、フィールドの名前の大文字と小文字を区別しない。この 3 つの挙動を Protocol の契約にしない。
  - `encoding/json/v2` と `encoding/json/jsontext` は使わない。使ってよくなる条件は [Upgrade Checks](#upgrade-checks) にある。
- golangci-lint のバージョンは、`scripts/golangci-lint.sh` の中で `go run <pkg>@<version>` の形で固定する。`go.mod` の tool directive にしない。
  - tool directive にすると、linter の依存が Core の `go.mod` と `go.sum` に入る。
- `govulncheck` と、リリースを自動化する設定は入れていない。CI のチェックかリリースの手順が要件にした時点で入れる。
- テストのライブラリを足す条件は [testing.md](testing.md#mocking-and-test-data) にある。

主なコマンドは次のとおり。  
テストのコマンドは [testing.md](testing.md#test-types-and-guarantees) にある。

- `cd core && go build ./...`: Core を build する。
- `cd core && go vet ./...`: Core に `go vet` を掛ける。
- `cd core && go fmt ./...`: Core を整形する。整形済みかは `cd core && test -z "$(gofmt -l .)"` で確かめる。
- `cd core && go mod tidy`: Core の依存を整理する。
- `cd core && go get <module>@<version>`: Core に依存を足す。
- `scripts/golangci-lint.sh ./...`: depguard で Core の依存の向きを確かめる。
- `cd analyzers/java && ./gradlew shadowJar`: Java Analyzer の fat jar を `analyzers/java/build/libs/java-analyzer.jar` に作る。
- `lefthook run pre-commit`: コミットの前のチェックをまとめて実行する。

手元で Java のプロジェクトを解析するコマンドは次のとおり。  
flag の体系は [CLI の Design Doc](../core/internal/cli/DesignDoc.md) にある。

```sh
# source root を Gradle の build model から自動で discovery する
depwalk analyze <workspace-root> --language java --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar"

# source root を明示する。--source-root は繰り返せる。preview の機能は --analyzer-meta javaPreview=true で有効にする
depwalk analyze <workspace-root> --language java --source-root <relative-root> --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" --analyzer-meta classpath= --analyzer-meta javaLanguageLevel=21

# 1 つのメソッドから caller を探索し、JSON で出す
depwalk analyze <workspace-root> --language java --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" --method "com.example.UserService#findById(java.lang.Long)" --direction caller --format json
```

## Upgrade Checks

Gradle の対応範囲、Tooling API、custom model provider のバージョンを変えるときは、次を確かめる。

- 対象の Gradle の範囲は、[discovery の Design Doc](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/DesignDoc.md) と `analyzers/java/build.gradle.kts` の `gradleCompatibilityTest` の anchor を同時に直す。ADR-0006 は直さない。
  - ADR は決定した時点の記録で、現在の対象の範囲は Design Doc が持つ。
- `cd analyzers/java && ./gradlew gradleCompatibilityTest` で、anchor ごとの Gradle と daemon の JVM の組み合わせを確かめる。
  - 確かめる観点は、provider が読み込めること、model のフィールド、task を実行しないこと、出力の隔離、固定した Graph である。
- provider が呼んでよい Gradle API は、対応範囲の下限の Gradle に存在し、上限までの Gradle で削除されていないものに限る。
  - provider は下限の Gradle API で compile するが、実行するのは対象の build の daemon である。compile が通っても、実行時に `NoSuchMethodError` になる。
  - 例: `ProjectDependency.getDependencyProject()` は Gradle 9.0 で削除され、代わりの `ProjectDependency.getPath()` は 8.11 で追加されたため 7.6 にない。project の依存は、両方の系列にある `ResolutionResult` の `ProjectComponentIdentifier#getProjectPath()` から集める。
  - provider に API の呼び出しを足すときは、下限と上限の両方の Gradle の Javadoc で存在を確かめる。

SootUp か、Analyzer が動く JDK のバージョンを変えるときは、次を確かめる。

- SootUp が読める classfile の major は、SootUp が同梱する ASM のバージョンで決まる。Analyzer の JDK の classfile の major を、同梱の ASM が読めるかを確かめる。
  - 読めない major に当たると、`guardQuery` が `unavailable` を返す。bytecode による型の階層の補完と、bytecode にだけある member の救済が、例外を出さずに無効になる。
  - 失敗が diagnostic に出ないため、原因の分からない `JAVA_INCOMPLETE_ANALYSIS` や候補の edge の欠落を見たら、まず classes output の classfile のバージョンを確かめる。
  - この回帰をテストで検出する設定は [testing.md](testing.md#mocking-and-test-data) にある。
- SootUp を上げるときは、型の階層の API の挙動を先に確かめる。
  - 解析の結果の edge の数が変わるため、E2E の fixture で上げる前と後の edge の数を比べる。
- JDK を上げたときは、JVM が Analyzer の stderr に警告を出さないかを確かめる。
  - JDK 24 以降の JVM は、Tooling API が native-platform を読み込むときに、stderr に警告を出す。
    - 警告の文は `WARNING: A restricted method in java.lang.System has been called` 系である。
  - fat jar の manifest に `Enable-Native-Access: ALL-UNNAMED` を設定して警告を抑えている。設定は `analyzers/java/build.gradle.kts` の `shadowJar` にある。
  - 実 CLI の E2E の `TestGradleMultiProjectCLI` が、stderr が depwalk の固定の行だけであることを確かめ、この回帰を検出する。

Go を上げるときは、`encoding/json/v2` と `encoding/json/jsontext` を使えるかを見直す。

- `GOEXPERIMENT=jsonv2` が要らなくなったら、strict な JSONL の parser の候補として使うかを決める。

## Scaffolding

- 新しい Analyzer は、Analyzer Protocol の SPI と JSONL のスキーマに合わせて作り始める。
  - Protocol、SPI、Model のスキーマは [Analyzer Protocol の Design Doc](../core/internal/protocol/DesignDoc.md) にある。
- 対象の言語の公式のツール（パーサーなど）があれば、それを使う。
- 作った後に、このリポジトリの命名と Protocol の契約に合わせる。
- Core の新しいコードは `core/` の中に作る。置き場の規則は [codebase.md](codebase.md#placement-rules) にある。
