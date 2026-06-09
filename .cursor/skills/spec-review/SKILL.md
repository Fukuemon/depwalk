---
name: spec-review
description: Runs an independent reviewer agent with a fresh context over a spec or its generated prompts, returning PASS or NEEDS_WORK with specific findings. Use as a phase gate after spec-draft, spec-resolve, spec-prompts, or when the user asks for spec-review.
---
# Spec Review

cwc-long-running-agents の "Fresh-Context Evaluator" パターンに沿った skill。
親 agent の会話履歴を引き継がない別 subagent を立ち上げて spec / prompts を独立評価し、`PASS` または `NEEDS_WORK` を返す。

## いつ使うか

- phase gate (`spec-draft` / `spec-resolve` / `spec-prompts` 完了時)
- ユーザーが「spec のレビュー」「review」「PASS / NEEDS_WORK」を要求した
- `spec-lifecycle` からの呼び出し

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- `references/review-rubric.md`

## 入力

- `$ARGUMENTS` から対象 spec path を特定
- 引数なし → 会話コンテキストから対象 spec を推定
- レビュー対象は spec の `index.md` または `prompts/` 配下

## 実行フロー

### 1. レビュー材料の特定

- spec path: `<spec dir>/index.md`
- PRD path / Design Doc path / feature doc dir / context dir / ADR dir: `Spec Workflow Contract` から取得
- review rubric path: `references/review-rubric.md`
- (任意) `prompts/` を含めるかを判定

### 2. fresh-context subagent の起動

`Agent` tool で別 subagent を起動する。subagent には **チャット履歴を渡さない**。
渡す情報は path だけ:

```text
subagent_type: Plan (または general-purpose)
prompt:
  以下のファイルを読んで spec をレビューしてください。
  - spec: <spec path>
  - PRD: <PRD path>
  - Design Doc: <Design Doc path>
  - feature doc: <feature doc dir / 関連 feature doc>
  - context: <context dir / 関連 topic>
  - ADR dir: <ADR dir>
  - rubric: <rubric path>
  - (任意) prompts: <prompts dir>

  rubric の各観点を評価し、最終行に `PASS` または `NEEDS_WORK` を出力してください。
  NEEDS_WORK の場合は、観点ごとに具体的な指摘 (file:line または section 名) を返してください。
```

### 3. 結果の保存

subagent の出力を `<spec dir>/review.md` に追記する。

```md
## Review YYYY-MM-DD HH:MM

Verdict: PASS / NEEDS_WORK
Reviewer: spec-review (fresh-context, subagent=<type>)

### 観点別評価

- 上位文書整合: ...
- 未解決論点: ...
- 実装対象明示: ...
- template 必須節: ...
- EARS acceptance: ...
- prompts 自己完結性 (該当時): ...
- 正本境界 (sync 済時。未 sync は N/A): ...

### 指摘 (NEEDS_WORK の場合のみ)

- <file:line / section> — <内容>
```

### 4. spec の `## レビュー` テーブル更新

spec の `## レビュー` テーブルに 1 行追加 (`日付 / 結果 / 指摘要点 / 対応`)。

### 5. ユーザー報告

- 結果 (`PASS` / `NEEDS_WORK`)
- 指摘の要約
- 次アクション提案
  - `PASS` → 次 phase
  - `NEEDS_WORK` → 指摘対応 → 再 review

## 停止条件

- 対象 spec / PRD / Design Doc / rubric のいずれかが読めない
- subagent 起動に失敗した
- rubric の評価観点をすべて評価できなかった (未評価のまま PASS を出さない)
- 同一 spec 内容を直前にレビューしており、内容に変化がない (重複レビューを避ける)
