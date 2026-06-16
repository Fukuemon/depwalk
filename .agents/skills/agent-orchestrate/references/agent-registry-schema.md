# Agent Registry Schema

`context/ai-agents.md` のスキーマと読み方。skill はこのスキーマに従ってレジストリを解釈する。

## ブロック構造

各エージェントは `### <id>` 見出し 1 つで定義される。フィールド:

| フィールド         | 必須 | 意味                                                                              |
| ------------------ | ---- | --------------------------------------------------------------------------------- |
| `enabled`          | yes  | `yes` のときだけ既定の対象集合に含める                                            |
| `model`            | no   | 使用モデル。`(CLI 既定)` のときは flag を付けない                                 |
| `invocation`       | yes  | `$PROMPT` を含む **非対話** コマンドテンプレート                                  |
| `verified`         | yes  | flag を実機確認済みか。`no` の初回は `<cli> --help` で点検                        |
| `limit_patterns`   | yes  | token/rate 上限を示す stdout/stderr 文字列または exit code                        |
| `auth_note`        | no   | 認証前提 (ログイン / API key)                                                     |
| `timeout`          | no   | 秒。未指定時は `context/ai-agents.md` の共通既定を参照 (skill 側に数値を持たない) |
| `max_input_tokens` | no   | プロンプト入力の目安上限。大きい差分の分割判断に使う。未指定なら共通既定          |

## `$PROMPT` の展開

- `invocation` は **空白区切りの argv テンプレート** として読む (シェルの再解釈はしない)。`$PROMPT` という独立トークンだけを、シェル変数 `PROMPT="$(cat prompt.txt)"` を `"$PROMPT"` と引用したまま 1 個の引数として差し込む。
- argv 化の規則: 各トークンをそのまま 1 引数とし、`$PROMPT` トークンは `"$PROMPT"` に置換する。トークン中のクォート (`-p "$PROMPT"` の `"`) はテンプレート表記であり、argv 構築時に剥がして 1 引数 (`$PROMPT`) として扱う。`invocation` 文字列を `eval` で再評価しない (本文中の特殊文字・改行が壊れる/誤実行する)。
- このため `invocation` には **`$PROMPT` 以外のシェル展開 (パイプ・`$(...)`・変数) を書かない**。複雑な起動が要るエージェントは wrapper スクリプトを `invocation` に指定する。
- ファイル入力 flag を持つ CLI なら、`$PROMPT` トークンを `prompt.txt` パス渡しに読み替えてよい (verified 時に確認)。

## 既定値の解決順

1. エージェントブロックの明示値
2. `context/ai-agents.md` の「共通既定」(timeout・出力先 dir・max_input_tokens 等の正本はここ)

固有値 (timeout / 出力先 / token 上限) は skill 側に hard default を持たず、すべて
`context/ai-agents.md` を正本とする。1・2 のどちらにも値が無いフィールドは
**レジストリ不備** とみなし、skill の `停止条件` (レジストリを補完してから再実行) として扱う。

## verified フラグの扱い

- `verified: no` のエージェントを初めて使うときは、`invocation` の各 flag が現行 CLI に存在するか `--help` で確認する。
- 確認できたらレジストリの `verified` を `yes` に更新するようユーザーに提案する (レジストリ更新は skill が直接行わず、変更点を報告する)。

## 用途別ルーティング

`context/ai-agents.md` の「用途別ルーティング」表で、用途 (`review` / `implement`) ごとの既定エージェント集合を引く。起動時に明示指定があればそちらを優先する。
