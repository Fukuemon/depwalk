# Issue Workflow

## 起票前に確認すること

- 何を決めたい Issue か、何を実装したい Issue か
- 対象ドメインが `context/project.yml` の `domains` のどれか (該当する `domain:*` ラベルを決める)
- 付与するラベル (`type:*` 必須 + `phase:*` / `domain:*` / `epic`) — 正本は `context/project.yml` の `labels`
- 既存 Issue や spec と重複していないか

## 停止条件

- 起点となる要求 Issue が曖昧
- 対象ドメイン (`context/project.yml` の `domains`) が決められない
- 完了条件を検証可能な文で書けない
