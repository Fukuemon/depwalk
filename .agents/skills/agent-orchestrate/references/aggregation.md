# Aggregation

収集した per-agent 出力を、上位 skill / ユーザーへ提示する汎用フォーマット。用途特化のマージ (例: レビュー指摘の統合) は呼び出し側 skill が行う。

## 提示フォーマット

```text
## Agent run summary (<timestamp>)

<status テーブル — failure-handling.md の様式>

### <agent_id_1>
<出力の要約 or 全文。長い場合は path を案内し要点のみ>

### <agent_id_2>
<同上>

(skipped: <agent_id_3> — usage limit, retried 1x)
```

## 規則

- **成功エージェントのみ** 本文を提示し、スキップは末尾に理由付きで列挙する。
- 出力が長い場合は全文を貼らず、`<id>.out` の path を案内して要点だけ要約する。
- エージェント間で結論が割れている場合は、その相違を明示する (どちらが正しいかの断定は呼び出し側 / ユーザーに委ねる)。

## 上位 skill への返却

- 上位 skill から呼ばれた場合は、最小限として **成功エージェントの `<id>.out` path 一覧** と status テーブルを返す。
- 用途別の合成 (指摘マージ、実装結果の検証) は呼び出し側の責務とする。
