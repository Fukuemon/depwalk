---
name: skill-feedback
description: >-
  利用中に見つけた skill / rule / subagent の不具合・改善点を正本 (.rulesync/) に書き戻し、rulesync-sync
  で再生成して直す。"skill がおかしい" / "skill 直して" / "この手順ずれてる" / "skill-feedback" で起動する。
---

# Skill Feedback

skill / rule / subagent を **使っている最中に見つけた** 不具合 (手順のズレ / 古い前提 / 誤誘導 / 抜け) や改善点を、その場の回避で終わらせず正本 `.rulesync/` に書き戻す skill。
消費 repo (link.sh で symlink 接続) から実行しても、symlink は live なのでテンプレ側の正本がそのまま直る。
ただし **変更の commit は sdd-template repo 側で行う** (消費 repo の `.rulesync/*` は未追跡。`AGENTS.md` の「共有プロセス層の正本」)。

## いつ使うか

- skill の手順どおりに進めたら実態と合わなかった
- skill が古い path / コマンド / 前提を参照していた
- 同じ補足説明を毎回口頭で足している (= skill に書き戻すべきサイン)
- レビューや振り返りで skill / rule の改善点が挙がった

## 先に読むもの

- `AGENTS.md` の `Skill 共通契約` (行数 / 必須セクション / description 規約)
- 対象 skill の `.rulesync/skills/<name>/SKILL.md` (生成物ではなく正本)
- `decisions.md` (過去の判断と衝突しないか。逆戻しの前に必読)

## 実行フロー

1. **事象の特定**: 何が・どの skill のどの記述と食い違ったかを 1〜2 行で言語化する (file:line まで特定する)
2. **原因の分類**: (a) 記述が古い / 誤り → 修正、(b) project 固有値の直書き → `context/project.md` 参照へ置換、(c) 説明不足 → SKILL.md か references に追記、(d) 設計判断の変更が必要 → ユーザーに提案して停止
3. **decisions.md との突合**: 変更が過去の判断 (`背景 / 判断 / 理由 / 今後`) の逆戻しに当たる場合、該当セクションを提示してユーザーに確認する
4. **正本の修正**: `rulesync-sync` skill の手順で `.rulesync/` を編集し、生成・検証 (`make generate` → `make check`) まで行う
5. **連鎖影響の確認**: skill の呼び出し関係 / phase gate / 状態遷移を変えた場合は `architecture.md` のシーケンスを同時に更新する
6. **記録**: 非自明な判断 (スコープの線引き / ツール制約による割り切り) を含む場合は `decisions.md` に 1 セクション追記する

## 停止条件

- 修正が skill の設計判断の変更 (フローの組み替え / 責務の移動) に及ぶ — ユーザー承認なしに進めない
- `decisions.md` の既存判断と衝突する — 逆戻しの可否をユーザーに確認する
- 事象が skill 起因か project 固有値 (`context/project.md`) の未記入かを切り分けられない
- `make check` が通らない状態で終わろうとしている
