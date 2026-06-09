# Infrastructure & Operations

> 最終更新: 2026-06-10

公開基盤・環境戦略・運用・セキュリティの契約。本書は **app 側が依存する contract** を定義する。infra 実体を別 repo で管理する場合はその境界も記す ([context/project.md](project.md) のリポジトリマップ)。

> depwalk は **CLI ツール**であり、サーバ / Web UI を持たない (Non Goals)。一般的な Web app 向けの環境戦略の多くは非該当。配布形態は設計フェーズで確定する。

## Infrastructure / Deployment

- 配信モデル: **CLI バイナリ / パッケージ配布**。サーバ常駐や hosting は持たない。
- 主な実行環境: 開発者ローカル、および **CI パイプライン** (プルリク時の影響範囲レポート — DesignDoc System Context)。
- 具体的な配布チャネル (バイナリ / パッケージレジストリ等) は Core 実装言語確定後に定める。infra repo はなし。

## Infrastructure Contract (app → infra)

- 外部インフラ依存は持たない。対象ソースリポジトリへの **read-only アクセス**のみが前提 ([architecture.md](architecture.md) State Boundary)。
- secret / token は不要 (外部サービス連携なし)。将来発生する場合は repo に実値を保存しない。

## Environment Strategy

| Environment | Purpose                | Role                                                        |
| ----------- | ---------------------- | ----------------------------------------------------------- |
| local       | 開発者の手元実行・開発 | CLI を直接実行し caller/callee を調査                       |
| CI          | 影響範囲の自動レポート | バッチ実行でグラフ/レポートを出力 (preview/production 相当) |

- production 相当の「公開」は CLI のリリース配布を指す。昇格トリガは設計フェーズで確定。

## Operations / Observability

- 一次観測点は **CLI の標準出力 / 終了コード**。Core ↔ Analyzer の JSONL はテキストで観測可能 (デバッグ容易性 — Communication Protocol)。
- 監視基盤は持たない (常駐サービスがないため)。

## Security / Privacy

- 解析対象は利用者自身のソースコード。外部送信は行わない。
- 個人情報・認証・権限は扱わない。将来扱う場合の方針は該当 feature / spec に置く。
