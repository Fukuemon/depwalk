---
type: context
title: 基盤と運用
description: local と CI の環境、自動 discovery の副作用の前提、実際のプロジェクトを解析するときの設定、標準出力と stderr の観測点、depwalk が漏らさない範囲
status: draft
governs:
  - .github/workflows
  - analyzers/java/build.gradle.kts
verified_commit: unverified
---

# 基盤と運用

## Environments

depwalk は CLI のツールで、サーバーと Web UI を持たない。  
配る形は CLI のバイナリかパッケージで、常駐するサーバーとホスティングを持たない。  
基盤のリポジトリはない。  
サーバーと Web UI を持たないことは [Design Doc の Non-Goals](../design/DesignDoc.md#non-goals) にある。

環境の種類と用途は次のとおり。

- local: 開発者の手元で CLI を直接実行し、caller と callee を調べる。
- CI: ビルド、テスト、文書のチェックを実行する。pull request ごとに影響範囲を報告する使い方は、まだない。[PRD の Lower-Priority Goals](../PRD.md#lower-priority-goals) にある。
- production に当たる公開は、CLI のリリースの配布を指す。公開へ進む条件は未定。

depwalk は、hosted service と depwalk 専用の外部の基盤に依存しない。  
Java の source root を自動で discovery するときは、利用者の環境で次の前提で動く。

- source root を明示したときは、Analyzer は対象のソースと classpath を読み取り専用で扱い、Gradle のランタイムを起動しない。
- 自動 discovery のときだけ、Gradle Tooling API、対象の build の Gradle daemon、workspace の外に置く一時の custom model provider が動く。
- 自動 discovery では、Gradle の build logic を利用者の権限で評価する。Gradle の repository、credential provider、network、cache、daemon の JVM を使いうる。
- 自動 discovery は、信頼できる build を前提にし、任意の build logic の副作用を sandbox に閉じ込めない。Analyzer 自身の読み取り専用の契約と、Gradle のランタイム全体の副作用を分けて扱う。
  - ADR-0006: [Gradle Tooling API による discovery](../adr/0006-adopt-gradle-tooling-api-discovery.md)
- CLI の help は、自動 discovery の副作用と、source root の明示で Gradle を通らずに済むことを、常に説明する。
- 自動 discovery を実行するたびに、build を評価する前に、Analyzer の stderr へ決まった文言で通知する。
  - 通知する内容は、次を使いうることである。
    - settings、build script、plugin の評価
    - artifact の repository と、既存の credential の解決
    - network と Gradle の user cache

実際の環境のプロジェクトを解析するときは、次を設定する。

- 中規模の multi-project（目安は call site が 5 万件）では、既定の heap だと Analyzer が `OutOfMemoryError` になりうる。`--analyzer-cmd`（または `DEPWALK_ANALYZER_CMD`）の java の起動に `-Xmx` を明示する。
  - 測った例では、`-Xmx8g` で 7 つの project、52,411 件の call site を解析できた。
  - OOM を検知したときに Core が返すエラーと終了コードは [CLI の Design Doc](../core/internal/cli/DesignDoc.md) にある。
- discovery が `JAVA_GRADLE_MODEL_ERROR`（`daemon-jvm-incompatible`）で失敗したら、daemon の JVM を指定して避ける。
  - 指定は `--analyzer-meta gradleJavaHome=<互換のある JDK の path>` で行う。
  - 規則は [discovery の Design Doc](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/DesignDoc.md#daemon-の-jvm-の明示の指定) にある。

## Deployment Conditions

配るチャネル（バイナリの配布、パッケージのレジストリなど）と、リリースを配ってよい条件は未定。  
リリースの設計のときに決める。  
リリースの前に通すテストは [testing.md](testing.md#required-tests) にある。

## Health and Monitoring

常駐するサービスがないため、監視の基盤は持たない。  
一次の観測点は、CLI の標準出力と終了コードである。  
Core と Analyzer の間の JSONL はテキストなので、そのまま読める。

- Gradle の stdout と stderr は、Protocol と CLI の出力へ渡さずに捨てる。
- 失敗は、分類した code と、sanitize した message と detail に変える。raw の例外、credential、URL の query、絶対 path を出さない。
- Analyzer は、自分の stderr に観測の情報を出してよい。discovery の開始と終了、使う Gradle のバージョン、見つけた project と root の件数、失敗の種類が当たる。Gradle から来た自由な文とは分けて出す。
- Analyzer の stderr には、depwalk が作る決まった行だけを出す。JVM の警告もこの隔離を破るため、JDK を上げるときの確認が要る。確認の方法は [tech-stack.md](tech-stack.md#upgrade-checks) にある。

## Rollback

リリースを戻す手順と、戻せない変更の扱いは未定。

## Secrets

depwalk は、秘密情報を入力として受け取らず、保存しない。

- 自動 discovery では、Gradle が既存の credential provider を使うことがある。depwalk は、その credential を入力として受け取らず、保存しない。
- 解析の対象は利用者のソースコードで、depwalk は解析した内容を外部のサービスへ送らない。
  - 自動 discovery の依存の解決による network の通信は、Gradle の設定と repository の契約に従う。
- 個人情報、認証、権限は扱わない。扱うときの方針は、その機能の Design Doc に書く。
- depwalk が漏らさないことを保証する範囲は、depwalk が作って渡す Protocol、CLI の出力、ログ、テストの成果物に限る。
  - Gradle と利用者の build logic が作る出力、cache、ファイル、network の副作用は、保証の範囲の外にする。
