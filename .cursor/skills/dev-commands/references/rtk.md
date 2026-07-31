# RTK (Rust Token Killer) — bash 出力の token 削減

エージェントが読む **bash 出力を圧縮する CLI プロキシ**の導入と扱い。`git status` / `git diff` / test / lint の出力を
そのまま context に流すと token を食うため、`rtk <command>` 経由で要約・フィルタしてから読む。

- 正本 (上流): <https://github.com/rtk-ai/rtk>
- 位置づけ: **各開発者のマシン単位 (global) の設定**。本リポジトリ / 消費 repo の `.rulesync/hooks.json` には登録しない (理由は `decisions.md`)。未導入でも全 workflow は成立する (任意の高速化)。

## 導入 (開発者ごと 1 回)

```bash
brew install rtk                    # または: curl -fsSL https://raw.githubusercontent.com/rtk-ai/rtk/refs/heads/master/install.sh | sh
rtk --version && rtk gain           # 導入確認 (別パッケージ "Rust Type Kit" を掴んでいると gain が失敗する)

rtk init -g                         # Claude Code / Copilot 向け: ~/.claude に hook + RTK.md を入れる
rtk init -g --codex                 # Codex CLI 向け
rtk init -g --agent cursor          # Cursor 向け
```

`rtk init -g` は `~/.claude/hooks/rtk-rewrite.sh` と `~/.claude/RTK.md` (約 10 行) を作り、`~/.claude/settings.json` に
PreToolUse hook を登録する (`.bak` を作ってから追記)。以後 Bash tool の `git status` は `rtk git status` に書き換えられ、
出力が圧縮されてからエージェントに渡る。設定後は CLI を再起動する。

- hook を入れず手で使うだけでもよい: `rtk git diff` / `rtk test` のように前置する。
- `rtk init -g --hook-only` は RTK.md を置かない (context 追加ゼロ)。
- 撤去は `rtk init -g --uninstall`。

## 使うときの注意

- **Read / Grep / Glob tool は hook を通らない**。エージェント側で圧縮したいときは `rtk cat` / `rtk grep` を bash から呼ぶ。
- 圧縮は **出力の要約**。エラー全文・スタックトレース・厳密な差分が必要な検証では素の命令を使う (`git diff` / test の生ログ)。
- `rtk gain` で削減量を確認できる。表示 token 数は `bytes / 4` の概算。
- 破壊的操作 (`rtk git push` 等) は素の命令と同じ副作用を持つ。`rtk` を前置しても承認規約と protected branch の制約は変わらない (`hooks/protected-branch/pre_tool_use.py` は `rtk` 前置を剥がして判定する)。
- 本リポジトリの permissions は **読み取り系の `rtk` サブコマンドのみ allow**。書き込み系は素の命令と同じく都度承認 (`.rulesync/permissions.json`)。

## hook の auto-allow (0.44.0 実測)

RTK の hook は書き換えたコマンドを `permissionDecision: "allow"` で返す。**書き換え対象は承認プロンプトを飛ばす**ため、
permissions の allow / ask 設定より hook の判断が先に効く。

| 入力                                          | hook の応答                                                                |
| --------------------------------------------- | -------------------------------------------------------------------------- |
| `git status` / `git diff` / `git commit -m x` | `rtk ...` に書き換え + auto-allow                                          |
| `cat <path>`                                  | `rtk read <path>` に書き換え + auto-allow (秘密鍵の読み出しも無確認で通る) |
| `git push` / `curl ...`                       | 書き換えのみ (auto-allow なし。通常の承認フロー)                           |
| `rm -rf ...` / `npm test`                     | 書き換え対象外                                                             |

- 承認ゲートを効かせたい操作は permissions ではなく **hook (deny を返す PreToolUse) で塞ぐ**。同一イベントに複数 hook が登録されている場合、**deny が auto-allow より優先される** — protected branch (`main`) で `git commit --allow-empty` を実行し、RTK が auto-allow を返しても `hooks/protected-branch/pre_tool_use.py` の deny で止まる (commit されない) ことを実測で確認済み。
- 秘密情報の読み出しを止めたい場合、`cat` は auto-allow されるため permissions の `ask` では止まらない。パス単位で塞ぐなら hook 側で判定する。
- hook に登録されるコマンドは素の `rtk`。version manager (mise / asdf 等) の shim で入れている場合、PATH が最小な環境から CLI を起動すると `rtk: command not found` になる。`settings.json` / `hooks.json` 側を shim の絶対 path にすると安定する。
