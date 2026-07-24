---
name: multi-agent-review
description: 現在の branch diff / PR / spec を複数の登録 CLI エージェント (実セットは context/ai-agents.md) に並列レビューさせ、指摘を dedup・重大度順に統合した 1 本のレポートを返す。PR 対象時は統合レポートを PR コメントとして投稿できる。"複数エージェントでレビュー" / "Rv を並列で" / "マージ前にクロスチェック" / "PR にレビューコメント" / "multi-agent-review" で起動する。
---

# Multi-Agent Review

レビュー対象 (現ブランチ diff / PR / spec) を複数の CLI エージェントへ並列に投げ、各エージェントの指摘を 1 つのレポートに統合・重大度付けする skill。実行エンジンは `agent-orchestrate`、上限/失敗時の挙動 (1 回リトライ後スキップ・部分成功許容) もそれに従う。

## いつ使うか

- 1 つの変更を複数エージェントで相互チェックしてからマージしたい
- 「複数エージェントでレビュー」「Rv を並列で」「クロスレビュー」を要求された
- PR / ブランチ diff / spec を独立した視点で同時にレビューさせたい

## 先に読むもの

- `agent-orchestrate` skill — 並列実行 / 失敗処理 / 集約の基盤 (本 skill の実行エンジン)
- `context/ai-agents.md` — `review` 用途のルーティングとエージェント定義
- `references/review-routing.md` — コード差分で回す観点の選択と degrade 規則
- `references/review-prompt.md` — レビュープロンプトの組み立て方
- `references/finding-merge.md` — エージェント横断の指摘マージ規則
- 観点の正本: コード差分 = 観点別 subagent 定義 (`.rulesync/subagents/review-{architecture,quality,security,performance}.md`)、spec / 文書 = `spec-reviewer` subagent (`spec-review` skill が利用可能なら補助参照)。skill 側で観点を再記述しない
- `references/pr-comment.md` — PR へのコメント投稿と指摘への対応記録 (PR 対象で投稿するときのみ)
- `styleguide-documents` skill — 出力レポートの文書品質基準

## 入力

- レビュー対象の指定:
  - 引数なし → 現ブランチ diff (`git diff <base>...HEAD`)
  - PR 番号 / URL → `gh pr diff`
  - spec path → 当該 spec の内容
- 対象エージェント集合 (省略時は `context/ai-agents.md` の `review` ルーティング)
- 出力先 (既定: 標準出力。spec 対象時は任意で `context/project.yml` の spec review report 契約 `specs/<issue-id>-<slug>/review.md`)
- `--comment` (任意・PR 対象時のみ): 統合レポートを PR コメントとして投稿する。「PR にコメントして」等の指示も同義

## 実行フロー

### 1. レビュー対象の解決

- 引数から対象種別 (diff / PR / spec) を確定する (`references/review-prompt.md` の取得表)。
  - diff / PR: 差分を取得する (`git diff` または `gh pr diff`)。差分が空なら `停止条件`。
  - spec: 当該 `specs/<issue-id>-<slug>/index.md` (+ 関連 design) を読む。spec 本体が見つからなければ `停止条件` (空 diff 判定は適用しない)。

### 2. 観点の選択とレビュープロンプトの組み立て

- コード差分: `references/review-routing.md` の表で回す観点を選び (小さい差分で全観点を回さない)、
  選んだ `review-*` subagent 定義の「レビュー姿勢」「検証観点」節を `references/review-prompt.md` の雛形に注入する。
- spec / 文書: 従来どおり spec 観点の rubric を使う (routing 表は適用しない)。
- 全エージェントへ **同一プロンプト** を渡す (公平な比較のため)。各指摘は `file:line + 重大度 + 根拠 + 提案` で返すよう指示する。

### 3. 並列レビュー実行 (外部 CLI が使えない場合は degrade)

- `agent-orchestrate` の並列実行パターンで `review` ルーティングのエージェントへプロンプトを投げる。
- 出力収集・失敗分類・リトライ/スキップは `agent-orchestrate` に委譲する (本 skill で挙動を再定義しない)。返ってきた status テーブルと成功エージェントの出力 path を受け取る。
- **外部 CLI が 0 台 / 全滅の場合**: `references/review-routing.md` の degrade 規則に従い、
  Claude の `review-*` subagent へ Task で委譲して観点別レビューだけは回す
  (差分を `.ai-out/code-review.diff` に保存して path を渡す。複数観点は並列起動)。
  degrade したこと (クロスチェックでない旨) をレポートに注記する。

### 4. 指摘のマージ

- `references/finding-merge.md` の規則で、エージェント横断の指摘を重複排除・統合し、合意数と重大度でランク付けする。
- 単一エージェントのみの指摘は「未合意」として区別する。

### 5. レポート出力

- `references/finding-merge.md` の出力レポート構成に従って提示する: status テーブル → 統合指摘 (重大度順) → 単一エージェントのみ (未合意) → エージェント間の相違 → 注記。
- spec 対象かつ出力先指定時は `context/project.yml` の契約に従い `specs/<issue-id>-<slug>/review.md` に保存する (複数回実行時は追記でなく上書き)。
- スキップされたエージェントを明示し、部分結果である旨を断る。

### 6. PR へのコメント投稿 (`--comment` 指定時のみ)

- `references/pr-comment.md` の手順で、統合レポートを PR へ 1 本のコメントとして投稿する (再実行時は既存コメントを更新)。
- **投稿前にレポート本文をユーザーに提示し、承認を得る** (投稿は公開行為)。
- 投稿後の指摘には `references/pr-comment.md` の「指摘への対応記録」規則で対応の種別と理由を残す。

## 停止条件

- レビュー対象が解決できない (diff が空 / PR や spec が見つからない)
- 外部 CLI も Claude subagent への degrade もすべて失敗した (`review-routing.md` の degrade 規則を使い切った)
- `--comment` が PR 以外の対象 (diff / spec) に指定された
- 投稿するレポート本文にユーザーの承認が得られていない

## 禁止事項

- エージェントごとに異なるプロンプトを渡す (比較の公平性が崩れる)
- スキップを伏せて全エージェントがレビューしたかのように報告する
- 指摘の根拠 (`file:line`) を省いてマージする
- CLI 名・モデルを skill に直書きする (`context/ai-agents.md` を読む)
- ユーザー承認なしに PR へコメントを投稿する
- 指摘ごとに個別コメントをばら撒く (投稿は統合レポート 1 本 + 対応記録の返信 1 本)
- `high` の指摘を独断で「対応しない」にする (ユーザー判断必須)
