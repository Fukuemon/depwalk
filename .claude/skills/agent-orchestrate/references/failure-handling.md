# Failure Handling

エージェント実行結果の分類と、リトライ/スキップ方針。方針は「**1 回リトライ後スキップ・部分成功許容**」。

## 分類規則

各エージェントの `<id>.exit` と `<id>.out` を見て分類する。複数条件に一致しうるため、**上から順に最初に一致した status を採る**。下表は **評価順に並んでおり、この順序が唯一の正** (例: exit=0 でも出力に認証エラー文字列を含めば、`ok` より先に `auth` が一致する)。例: 出力に `429` と `unauthorized` の両方を含む場合は `auth` を優先する。

| status (評価順) | 判定                                                                           |
| --------------- | ------------------------------------------------------------------------------ |
| `timeout`       | exit=124 (`timeout` コマンドによる打ち切り)                                    |
| `auth`          | 出力に認証エラー (`unauthorized` / `not logged in` / `401`)                    |
| `limit`         | `limit_patterns` の文字列に一致、または exit が rate/quota を示す (例: 429 系) |
| `error`         | 上記以外の非 0 exit、または exit=0 でも出力が空/壊れている                     |
| `ok`            | exit=0 かつ出力が空でなく、上記いずれにも一致しない                            |

- `limit_patterns` は `context/ai-agents.md` の各エージェント定義から読む。

## リトライ方針

- **対象**: `limit` と、一過性が疑われる `error`。一過性とみなすのは出力が次のようなネットワーク系パターンを含むときに限る: `connection reset` / `connection refused` / `timed out` / `temporary failure` / `EAI_AGAIN` / `503` / `502`。これらに一致しない `error` (構文・flag 不正・パース不能など) はリトライせずスキップする。
- **回数**: 1 回のみ。短いバックオフ (`sleep 5` 程度) を挟んで同一 invocation を再実行する。
- **非対象**: `auth` はリトライしない (設定不備のため即スキップし、ユーザーへ認証を促す)。`timeout` は invocation/モデルの問題が多いので原則リトライせずスキップ (必要時のみユーザー判断)。

## スキップと続行

- リトライ後もダメなエージェントは当該 status のまま **スキップ** し、残りエージェントの結果で続行する。
- 全エージェントが非 `ok` の場合のみ全体を停止する (skill の `停止条件`)。

## status テーブル様式

報告は次の形式で出す:

```text
| agent        | status | retries | output                             |
| ------------ | ------ | ------- | ---------------------------------- |
| <agent_id_1> | ok     | 0       | .ai-out/agent-runs/<ts>/<id_1>.out |
| <agent_id_2> | limit  | 1       | (skipped: usage limit)             |
| <agent_id_3> | ok     | 0       | .ai-out/agent-runs/<ts>/<id_3>.out |
```

- `auth` のときは認証手順 (`auth_note`) を併記する。
- token 浪費を避けるため、`auth` / 設定不備が原因のものは再試行ループに入れない。
