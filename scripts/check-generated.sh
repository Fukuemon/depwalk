#!/usr/bin/env bash
# 生成物 drift 検査: .rulesync/ (正本) から再生成し、コミット済み生成物と差分がないことを
# 確認する。生成物がズレたまま放置されると、各 provider に古い設定が silent に届く。
#
# 本 repo は sdd-template の消費側であり、.rulesync/ は sdd-template への symlink かつ
# .gitignore 対象 (contract の「消費 repo への .rulesync/ の commit 禁止」)。したがって
# checkout しただけの環境 (CI 等) では正本が存在せず、検査自体が成立しない。
# その場合は「合格」ではなく「検査不能」として区別する。
#
# 引数:
#   (なし)   コミット済みの状態が正本と同期しているかを見る (手動実行 / make 用)
#   --staged ステージ済みの内容が正本と同期しているかを見る (pre-commit 用)
#
# pre-commit では commit がまだ存在しないため HEAD と比べてはならない。再生成した
# 生成物を stage した直後でも「HEAD と違う」ので必ず FAIL してしまう。ステージ内容が
# 再生成結果と一致しているか (= working tree と index の差) を見るのが正しい。
#
# 終了コード: 0 検査 OK / 1 drift あり / 2 検査不能 (正本が無い・生成に失敗)
#
# 判断の正本は adr/0008-doc-freshness-and-reading-map.md。
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GENERATED_PATHS=(AGENTS.md CLAUDE.md .agents .claude .codex .cursor .mcp.json)

# 空配列の展開は bash 3.2 (macOS 既定) の set -u で unbound variable になるため、
# ${arr[@]+...} の形で「要素があるときだけ展開する」書き方にする。
DIFF_BASE=(HEAD)
if [ "${1:-}" = "--staged" ]; then
  DIFF_BASE=()
fi

# 正本の実体があるか。symlink が切れている場合も -r は false になる。
if [ ! -r .rulesync/rules/CLAUDE.md ]; then
  echo "SKIP: .rulesync/ の実体がありません (sdd-template が link されていない環境)" >&2
  echo "→ 生成物の drift は検査できません。合格として扱わないでください" >&2
  exit 2
fi

if ! bash scripts/generate.sh >/dev/null 2>&1; then
  echo "NG: scripts/generate.sh が失敗しました (npx / ネットワーク / 正本の状態を確認)" >&2
  exit 2
fi

drift=0
if ! git diff --quiet ${DIFF_BASE[@]+"${DIFF_BASE[@]}"} -- "${GENERATED_PATHS[@]}"; then
  echo "NG: 生成物が .rulesync/ と drift しています (再生成で差分が出ました):" >&2
  git --no-pager diff --stat ${DIFF_BASE[@]+"${DIFF_BASE[@]}"} -- "${GENERATED_PATHS[@]}" >&2
  drift=1
fi

untracked="$(git ls-files --others --exclude-standard -- "${GENERATED_PATHS[@]}")"
if [ -n "$untracked" ]; then
  echo "NG: 未コミットの生成物があります:" >&2
  echo "$untracked" >&2
  drift=1
fi

if [ "$drift" -ne 0 ]; then
  echo "→ 再生成の結果をコミットしてください (make -f sdd-template.mk generate)" >&2
  exit 1
fi

echo "check-generated: OK"
