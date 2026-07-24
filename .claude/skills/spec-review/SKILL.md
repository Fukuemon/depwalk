---
name: spec-review
description: >-
  spec / 生成 prompts を fresh context の評価専用 subagent (spec-reviewer) に委譲し、PASS /
  NEEDS_WORK と具体指摘を集約して返す。spec-lifecycle の phase gate として、またはユーザーが "spec-review"
  / "spec のレビュー" / "PASS か見て" を求めたときに起動する。
---

# Spec Review

cwc-long-running-agents の "Fresh-Context Evaluator" パターンに沿った skill。
親 agent の会話履歴を引き継がない評価専用 subagent `spec-reviewer` に spec / prompts を独立評価させ、`PASS` または `NEEDS_WORK` を集約して返す。
**レビュー観点の正本は subagent 定義 (`.rulesync/subagents/spec-reviewer.md`)**。本 skill は委譲と集約のみを担い、観点を再記述しない。

## いつ使うか

- phase gate (`spec-lifecycle` の gate phase 完了時。gate の正本は phase レジストリ)
- ユーザーが「spec のレビュー」「review」「PASS / NEEDS_WORK」を要求した
- `spec-lifecycle` からの呼び出し

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- subagent 定義 `spec-reviewer` (観点の正本。fallback 時にのみ本文を Read する)

## 入力

- `$ARGUMENTS` から対象 spec path を特定
- 引数なし → 会話コンテキストから対象 spec を推定
- レビュー対象は spec の `index.md` または `prompts/` 配下

## 実行フロー

### 1. レビュー材料の特定

- spec path: `<spec dir>/index.md`
- PRD path / Design Doc path / feature doc dir / context dir / ADR dir: `Spec Workflow Contract` から取得
- (任意) `prompts/` を含めるかを判定

### 2. spec-reviewer subagent への委譲

`spec-reviewer` subagent を起動する。subagent には **チャット履歴を渡さない**。
渡す情報は path だけ:

```text
subagent: spec-reviewer
prompt:
  以下のファイルを読んで spec をレビューしてください。
  - spec: <spec path>
  - PRD: <PRD path>
  - Design Doc: <Design Doc path>
  - feature doc: <feature doc dir / 関連 feature doc>
  - context: <context dir / 関連 topic>
  - ADR dir: <ADR dir>
  - (任意) prompts: <prompts dir>
```

**fallback**: `spec-reviewer` subagent を起動できない環境では、汎用の read-only subagent (Plan / general-purpose 等) を起動し、subagent 定義ファイル (`.rulesync/subagents/spec-reviewer.md`、生成環境では各 provider の agents ディレクトリ) を観点として Read させる。観点をこの skill 内に転記しない。

### 3. 結果の保存

subagent の出力 (定義の「出力フォーマット」に従う) を `<spec dir>/review.md` に追記する。

### 4. spec の `## レビュー` テーブル更新

spec の `## レビュー` テーブルに 1 行追加 (`日付 / 結果 / 指摘要点 / 対応`)。

### 5. ユーザー報告

- 結果 (`PASS` / `NEEDS_WORK`)
- 指摘の要約
- 次アクション提案
  - `PASS` → 次 phase
  - `NEEDS_WORK` → 指摘対応 → 再 review

## 停止条件

- 対象 spec / PRD / Design Doc / subagent 定義のいずれかが読めない
- subagent 起動に失敗し、fallback も起動できなかった
- 観点をすべて評価できなかった (未評価のまま PASS を出さない)
- 同一 spec 内容を直前にレビューしており、内容に変化がない (重複レビューを避ける)
