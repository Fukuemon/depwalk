---
type: context
title: "Infrastructure & Operations"
description: 公開基盤・環境・運用・セキュリティの契約
keywords: [infrastructure, CI, secret, 運用, Gradle daemon]
governs:
  - .github/workflows
  - analyzers/java/build.gradle.kts
verified_commit: unverified
---

# Infrastructure & Operations

公開基盤・環境戦略・運用・セキュリティの契約。本書は **app 側が依存する contract** を定義する。infra 実体を別 repo で管理する場合はその境界も記す ([context/project.yml](project.yml) のリポジトリマップ)。

> depwalk は **CLI ツール**であり、サーバ / Web UI を持たない (Non Goals)。一般的な Web app 向けの環境戦略の多くは非該当。Core 実装基盤は [ADR-0002](../adr/0002-core-implementation-foundation.md) を正本とする。

## Infrastructure / Deployment

- 配信モデル: **CLI バイナリ / パッケージ配布**。サーバ常駐や hosting は持たない。
- 主な実行環境: 開発者ローカル、および **CI パイプライン** (プルリク時の影響範囲レポート — DesignDoc System Context)。
- 具体的な配布チャネル (バイナリ / パッケージレジストリ等) は release 設計時に定める。infra repo はなし。

## Infrastructure Contract (app → infra)

- hosted service / depwalk 専用の外部インフラ依存は持たない。明示 `sourceRoots` 経路では Analyzer が対象 source / classpath を read-only で扱う。
- Java の自動 discovery では条件付き runtime として Gradle build logic を利用者権限で評価する。Gradle repository、credential provider、network、cache、daemon JVM を利用し得るが、credential を depwalk の入力として受領・保存しない。明示 `sourceRoots` は Gradle runtime を完全 bypass する。
- CLI helpは上記副作用と明示bypassを常時説明する。自動discoveryの各runではbuild評価前にAnalyzer stderrへ、settings / build script / plugin評価、artifact repository、既存credential resolution、network、Gradle user cacheを利用し得ることを安定した定型文で通知する。

## Environment Strategy

| Environment | Purpose                | Role                                                        |
| ----------- | ---------------------- | ----------------------------------------------------------- |
| local       | 開発者の手元実行・開発 | CLI を直接実行し caller/callee を調査                       |
| CI          | 影響範囲の自動レポート | バッチ実行でグラフ/レポートを出力 (preview/production 相当) |

- production 相当の「公開」は CLI のリリース配布を指す。昇格トリガは設計フェーズで確定。

## Operations / Observability

- 一次観測点は **CLI の標準出力 / 終了コード**。Core ↔ Analyzer の JSONL はテキストで観測可能 (デバッグ容易性 — Communication Protocol)。
- Gradle の stdout / stderr は Protocol / CLI output へ転送せず破棄する。failure は分類済み code と sanitize 済み message / detail に変換し、raw exception、credential、URL query、絶対 path を出さない。
- discoveryの開始・終了、使用Gradle version、検出project / root件数、安定failure categoryはAnalyzer自身のstderr観測情報として出力できる。Gradle由来の自由文とは区別する。
- 監視基盤は持たない (常駐サービスがないため)。

## Security / Privacy

- 解析対象は利用者自身のソースコード。depwalk 自身は解析内容を外部サービスへ送信しない。ただし自動 discovery 中の dependency resolution による network 通信は Gradle の設定と repository 契約に従う。
- 個人情報・認証・権限は扱わない。将来扱う場合の方針は該当 feature / spec に置く。
- 自動 discovery は **trusted build** 前提であり、任意 build logic の副作用を sandbox しない。Analyzer 自身の read-only 契約と Gradle runtime 全体の副作用を区別する。
- 非漏洩保証は depwalk が生成・転送する Protocol、CLI、log、test artifact に限定する。Gradle 自身や利用者 build logic が生成する output / cache / file / network side effect は保証範囲外とする。詳細判断は [ADR-0006](../adr/0006-adopt-gradle-tooling-api-discovery.md)。
- Analyzer stderr の出力隔離 (depwalk 生成の固定行のみ) は Gradle 由来 output だけでなく **JVM 自身の警告にも破られる**。JDK 24+ は Tooling API の native-platform load で `WARNING: A restricted method in java.lang.System has been called` 系の警告を stderr へ出すため、fat jar の manifest に `Enable-Native-Access: ALL-UNNAMED` を設定して抑止する (`analyzers/java/build.gradle.kts` の shadowJar manifest)。実 CLI E2E (`TestGradleMultiProjectCLI`) の「stderr は depwalk 固定行のみ」検証がこの回帰を検出する。
