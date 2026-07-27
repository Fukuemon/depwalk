#!/usr/bin/env bash
# closed issue の spec dir が削除されずに残っていないかを検査する (SDD の closeout 契約の機械検査)。
#
# closeout 契約: spec は issue 単位の作業文書であり、issue が閉じたら削除する
# (正本: `spec-lifecycle` skill の references/closeout.md)。手順はあっても実行の担保が無いと
# 残存するため、機械検査でゲートにする。
#
# 使い方:
#   bash scripts/check-specs-residue.sh
#   SPECS_CHECK_REPO=<owner>/<repo> bash scripts/check-specs-residue.sh   # repo を明示する
#   make -f sdd-template.mk check-specs                                   # 消費 repo での入口
#
# 終了コード: 0 = 残存なし / 1 = 残存あり (closeout 未実施) / 2 = 検査不能 (gh 認証・権限・想定外の値)
#
# 固有値は直書きしない:
#   - spec dir は context/project.yml の `paths.spec_dir` から解決する (無ければ specs/)
#   - repo は gh が解決した origin から取る (SPECS_CHECK_REPO で上書き可)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_YML="$ROOT/context/project.yml"

# paths.spec_dir (例: `specs/<issue-id>-<slug>/`) の固定部分だけを取り出す
resolve_specs_dir() {
  local value=""
  if [ -f "$PROJECT_YML" ]; then
    value="$(sed -n 's/^[[:space:]]*spec_dir:[[:space:]]*\([^#]*\).*/\1/p' "$PROJECT_YML" | head -1)"
    value="${value%%<*}"                                  # <issue-id> 以降を落とす
    value="$(printf '%s' "$value" | tr -d '"'"'"' ' | sed 's:/*$::')"
  fi
  printf '%s' "${value:-specs}"
}

SPECS_REL="$(resolve_specs_dir)"
SPECS_DIR="$ROOT/$SPECS_REL"

[ -d "$SPECS_DIR" ] || { echo "no $SPECS_REL/; skip"; exit 0; }
command -v gh >/dev/null || { echo "gh CLI が必要です (issue の state を引くため)" >&2; exit 2; }

REPO="${SPECS_CHECK_REPO:-}"
if [ -z "$REPO" ]; then
  # 直書きせず origin から解決する。取れなければ検査不能 (合格にしない)
  REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
  [ -n "$REPO" ] || {
    echo "repo を解決できません。SPECS_CHECK_REPO=<owner>/<repo> を指定してください" >&2
    exit 2
  }
fi

errfile="$(mktemp)"
trap 'rm -f "$errfile"' EXIT

residue=()
unresolved=()
for dir in "$SPECS_DIR"/*/; do
  [ -d "$dir" ] || continue
  name="$(basename "$dir")"
  issue="${name%%-*}"                                     # 先頭の連番のみを issue 番号として扱う
  case "$issue" in
    '' | *[!0-9]*)
      echo "skip: $name (issue 番号を抽出できない)"
      continue
      ;;
  esac

  # stderr を state に混ぜると gh のアップデート通知が値に混入し、CLOSED を取りこぼす
  if state="$(gh issue view "$issue" --repo "$REPO" --json state --jq .state 2>"$errfile")"; then
    case "$state" in
      CLOSED) residue+=("$name (issue #$issue: CLOSED)") ;;
      OPEN) ;;
      # 想定外の値を「OPEN でない」と黙認すると残存を見逃すため、検査不能として扱う
      *) unresolved+=("$name (issue #$issue): 想定外の state [$state]") ;;
    esac
  else
    unresolved+=("$name (issue #$issue): $(tr '\n' ' ' <"$errfile")")
  fi
done

# state を引けないまま合格させると closeout 未実施を見逃すため、検査不能として失敗させる
if [ ${#unresolved[@]} -gt 0 ]; then
  echo "issue の state を取得できませんでした (gh の認証 / ネットワーク / 権限を確認してください):" >&2
  printf '  - %s\n' "${unresolved[@]}" >&2
  exit 2
fi

if [ ${#residue[@]} -gt 0 ]; then
  echo "closed issue の spec が残存しています (closeout 未実施):"
  printf '  - %s\n' "${residue[@]}"
  echo "清算手順: spec-lifecycle skill の references/closeout.md"
  exit 1
fi

echo "OK: closed issue の spec 残存なし ($SPECS_REL/)"
