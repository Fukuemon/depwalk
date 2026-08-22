---
type: context
title: "Infrastructure & Operations"
description: 公開基盤・環境・運用・セキュリティの契約
keywords: [infrastructure, CI, secret, 運用, Gradle daemon]
governs:
  - .github/workflows
  - analyzers/java/build.gradle.kts
verified_commit: 7a29a6b
---

# Infrastructure & Operations

公開基盤・環境戦略・運用・セキュリティの契約。本書は app 側が依存する contract を定義する。
infra 実体を別リポジトリで管理する場合は、その境界も本書に記す。

- [project.yml](project.yml) のリポジトリマップ — リポジトリの分割と役割を定める

depwalk は CLI ツールであり、サーバや Web UI を持たない。
そのため、一般的な Web アプリ向けの環境戦略はほとんど当てはまらない。
Core の実装基盤は本書では定めず、ADR が正本となる。

- [DesignDoc](../design/DesignDoc.md) の Non Goals — サーバや Web UI を持たないと定める
- [ADR-0002](../adr/0002-core-implementation-foundation.md) — Core 実装基盤に Go と Go modules を採用した決定

## Infrastructure / Deployment

- 配信モデルは CLI バイナリ / パッケージ配布とする。サーバ常駐や hosting は持たない。
- 主な実行環境は開発者ローカルと CI パイプラインである。CI ではプルリク時の影響範囲レポートに使う。
- 具体的な配布チャネル (バイナリ / パッケージレジストリ等) は release 設計時に定める。infra repo はなし。

## Infrastructure Contract (app → infra)

- hosted service や depwalk 専用の外部インフラへの依存は持たない。明示 `sourceRoots` 経路では、Analyzer が対象 source / classpath を read-only で扱う。
- Java の自動 discovery では、条件付き runtime として Gradle build logic を利用者権限で評価する。Gradle repository、credential provider、network、cache、daemon JVM を利用し得るが、credential を depwalk の入力として受領・保存しない。明示 `sourceRoots` は Gradle runtime を完全に bypass する。
- CLI help は上記の副作用と、明示 `sourceRoots` による bypass を常時説明する。自動 discovery の各 run では、build 評価前に Analyzer stderr へ安定した定型文で通知する。通知する内容は、settings / build script / plugin の評価、artifact repository、既存 credential の resolution、network、Gradle user cache を利用し得ることである。

## Environment Strategy

| Environment | Purpose                | Role                                                        |
| ----------- | ---------------------- | ----------------------------------------------------------- |
| local       | 開発者の手元実行・開発 | CLI を直接実行し caller/callee を調査                       |
| CI          | 影響範囲の自動レポート | バッチ実行でグラフ/レポートを出力 (preview/production 相当) |

- production 相当の「公開」は CLI のリリース配布を指す。昇格トリガは未確定である。

## Operations / Observability

- 一次観測点は CLI の標準出力と終了コードである。Core と Analyzer の間の JSONL はテキストのため、そのまま観測できる。デバッグ容易性を優先した設計による。
- Gradle の stdout / stderr は Protocol / CLI output へ転送せず破棄する。failure は分類済み code と sanitize 済みの message / detail に変換し、raw exception、credential、URL query、絶対 path を出さない。
- discovery の開始と終了、使用する Gradle version、検出した project / root の件数、安定した failure category は、Analyzer 自身の stderr 観測情報として出力できる。Gradle 由来の自由文とは区別する。
- 監視基盤は持たない。常駐サービスがないためである。

## Security / Privacy

- 解析対象は利用者自身のソースコードであり、depwalk 自身は解析内容を外部サービスへ送信しない。ただし自動 discovery 中の dependency resolution による network 通信は、Gradle の設定と repository 契約に従う。
- 個人情報・認証・権限は扱わない。将来扱う場合の方針は該当 feature / spec に置く。
- 自動 discovery は trusted build を前提とし、任意 build logic の副作用を sandbox しない。Analyzer 自身の read-only 契約と、Gradle runtime 全体の副作用は区別する。
- 非漏洩保証は depwalk が生成・転送する Protocol、CLI、log、test artifact に限定する。Gradle 自身や利用者 build logic が生成する output / cache / file / network の副作用は保証範囲外とする。
  - [ADR-0006](../adr/0006-adopt-gradle-tooling-api-discovery.md) — Gradle Tooling API による source root 自動 discovery を採用した決定
- Analyzer stderr の出力隔離 (depwalk 生成の固定行のみ) は、Gradle 由来 output だけでなく JVM 自身の警告にも破られる。JDK 24 以降は Tooling API の native-platform load で `WARNING: A restricted method in java.lang.System has been called` 系の警告を stderr へ出すため、fat jar の manifest に `Enable-Native-Access: ALL-UNNAMED` を設定して抑止する (`analyzers/java/build.gradle.kts` の shadowJar manifest)。実 CLI E2E (`TestGradleMultiProjectCLI`) の「stderr は depwalk 固定行のみ」検証が、この回帰を検出する。

## 参照

- [project.yml](project.yml): リポジトリマップと運用の固有値
- [DesignDoc](../design/DesignDoc.md) の Non Goals: サーバ / Web UI を持たない境界
- [testing.md](testing.md): stderr 隔離を検出する E2E の置き場と実行条件
- [toolchain.md](toolchain.md) の Gradle discovery compatibility matrix: 対応する Gradle と JVM の範囲
- [ADR-0002](../adr/0002-core-implementation-foundation.md): Core 実装基盤に Go と Go modules を採用した決定
- [ADR-0006](../adr/0006-adopt-gradle-tooling-api-discovery.md): Gradle Tooling API による source root 自動 discovery を採用した決定
