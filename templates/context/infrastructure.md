# Infrastructure & Operations

> 最終更新: YYYY-MM-DD

公開基盤・環境戦略・運用・セキュリティの契約。本書は **app 側が依存する contract** を定義する。infra 実体を別 repo で管理する場合はその境界も記す ([context/project.md](project.md) のリポジトリマップ)。

## Infrastructure / Deployment

- 公開基盤と配信モデル。app repo に置くもの / infra repo へ委譲するものの境界。

## Infrastructure Contract (app → infra)

- app 側から infra 側へ受け渡す contract (project 名 / domain / secret 名 / 参照リンク等)。実値は repo に保存しない。

## Environment Strategy

| Environment | Purpose | Role |
| ----------- | ------- | ---- |
| local |  |  |
| preview |  |  |
| production |  |  |

- 各環境の昇格トリガ (どのイベントで production へ出るか)。

## Operations / Observability

- 監視 / ログ / 分析の一次観測点と、リリース判定に含める確認。

## Security / Privacy

- secret / token の分離方針 (client へ露出させない)。
- 個人情報・認証・権限を扱う場合の方針の所在 (feature / spec)。
