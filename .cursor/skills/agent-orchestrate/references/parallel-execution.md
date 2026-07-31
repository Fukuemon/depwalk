# Parallel Execution

複数エージェントを非対話・並列に起動し、結果を per-agent ファイルへ収集する正準パターン。

## 原則

- 各エージェントを background (`&`) で起動し、`wait` で全完了を待つ。
- 出力は **エージェントごとに別ファイル** へ分離する (混線を防ぐ)。
- 各エージェントに timeout を掛け、ハングで全体が止まらないようにする。
- 各エージェントの stdin は `</dev/null` で塞ぐ (stdin をパイプと誤検知して待機ハングする CLI がある)。
- 終了コードを `<id>.exit` に保存し、後段の失敗分類が参照する。

## 正準シェルパターン

`OUT` は呼び出し側 (step2 でプロンプトを固定した dir) から受け取り、同じ dir を共有する。timeout コマンド名と各エージェントの invocation は **すべて `context/ai-agents.md` から差し込む** ものとし、ここに CLI 名・モデル・flag を固定しない。

```bash
# OUT: step2 で prompt.txt を書き出した出力先 dir (呼び出し側から渡す)
# TIMEOUT: 利用可能な timeout runner (下記「timeout runner の解決」で確定)
PROMPT="$(cat "$OUT/prompt.txt")"   # step2 で固定済みのプロンプト本文

run_agent() {           # $1=id  $2=timeout秒  $3...=invocation（registry から $PROMPT 展開済み）
  local id="$1" tmo="$2"; shift 2
  # timeout runner 前提。素の起動 (fallback) は置かない — ハングを打ち切れず `wait` が
  # 無限に止まるため。runner 不在時は下記「background runner を使う場合」の方式に切り替える。
  "$TIMEOUT" "$tmo" "$@" </dev/null >"$OUT/$id.out" 2>&1
  echo $? >"$OUT/$id.exit"
}

# runner 不在のまま本パターンを走らせない (無制限起動の予防線)
[ -n "$TIMEOUT" ] || { echo "timeout runner が無い: background runner 方式に切り替える" >&2; exit 2; }

# レジストリの各 enabled エージェントについて、invocation を argv に展開して起動する。
# 下の <...> は context/ai-agents.md の invocation を展開した実引数 (CLI 名・flag をここに直書きしない)。
run_agent <id_1> <timeout_1> <invocation_1 argv...> &
run_agent <id_2> <timeout_2> <invocation_2 argv...> &
# ...enabled エージェント分くり返す

wait
ls "$OUT"   # <id>.out / <id>.exit が揃う
```

- 各 `run_agent` 行は **レジストリの invocation をそのまま展開** する。CLI 名や flag を此処に固定しない (`agent-registry-schema.md` の `$PROMPT` 展開規則に従う)。
- timeout の終了コードは 124 (タイムアウト)。失敗分類で利用する。

## timeout runner の解決

GNU `timeout` は macOS に標準搭載されず (`command not found` で全エージェントが起動失敗する)。実行前に利用可能な runner を確定し `$TIMEOUT` に入れる:

```bash
if command -v timeout >/dev/null 2>&1; then TIMEOUT=timeout
elif command -v gtimeout >/dev/null 2>&1; then TIMEOUT=gtimeout   # coreutils (brew)
else TIMEOUT=""; fi   # どれも無ければ background runner 方式に切り替える
```

- `$TIMEOUT` が空のまま正準シェルパターンを走らせてはいけない (**ハングを打ち切れず `wait` が無限に止まる** — 中核原則「timeout を各エージェントに適用」を満たせない)。上の sample は空なら `exit 2` で止まる。macOS は GNU `timeout` 非搭載が既定のためこの分岐は頻繁に通る。runner 不在時は次のいずれかを **必須** とする:
  - (推奨) 下記「background runner を使う場合」の方式に切り替え、harness 側の timeout に各エージェントの打ち切りを委ねる。打ち切られた場合は `<id>.exit` に timeout 相当 (124) を書き、失敗分類を成立させる。
  - background runner も無い実行環境では、timeout runner (coreutils の `gtimeout` 等) の導入を `停止条件` として促す (無制限起動のまま続行しない)。
- coreutils が multi-call binary (`coreutils timeout ...`) の環境では、その呼び出しを `$TIMEOUT` に読み替える。

## background runner を使う場合

- 実行している harness が background 実行を持つなら、1 エージェント = 1 バックグラウンドタスクとして起動してもよい (例: Claude Code の Bash `run_in_background`)。完了時に再通知される実装なら polling は不要。harness 名・呼び方は実行環境に依存するため、ここに特定 CLI の手順を固定しない。
- ただし失敗分類とリトライを 1 箇所に集約したい場合は、上記の自己完結シェル (1 回の起動で `wait` まで) の方が決定的で扱いやすい。
- どちらでも per-agent の `<id>.out` / `<id>.exit` を残す契約は守る。

## リトライ

- 失敗分類後に再試行するエージェントだけを、同じ `run_agent` で再度起動する (短いバックオフ `sleep` を挟む)。リトライは `failure-handling.md` の規則で **1 回まで**。
