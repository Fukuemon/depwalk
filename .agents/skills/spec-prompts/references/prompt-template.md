# Prompt Template

`spec-prompts` が生成する各 prompt の骨組み。生成時はこのテンプレートをコピーし、`{ }` プレースホルダを埋める。
project 固有のレビュー CLI / branch 運用は spec / `AGENTS.md` / `workflow-git` 側に閉じ込め、本テンプレートには直書きしない。

```markdown
# {タスク名}

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。

1. 最新の base branch を取得
2. 作業ブランチ `feature/{Issue番号}` を作成
3. PR / MR テンプレートを確認し、完了条件を description に転記する
4. Draft PR / MR を作成して push する

### ステップ 1: {作業単位 1}

1. テストを先に書く (TDD)
2. 実装する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 2: {作業単位 2}

(同上)

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを `spec-track` に渡す
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `<spec dir>/index.md`
- 参照する appendix:
  - `<spec dir>/index.md` 内の {該当 section}
- repo root: `$(ghq root)/<repo>`
- 参照する path:
  - `apps/{target}/...`
  - `packages/{name}/...`

## 前提条件

- 完了しているべき phase / 依存 prompt: {例: P1_01_<target>_page_shell.md}
- 完了後に着手可能になる後続 prompt: {例: P2_01_<target>_flow.md}
- 必要な repo 状態: {例: base branch に migration 適用済み}

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

-

### 実装しない範囲

-

## 設計仕様

spec から該当箇所を必要最小限だけ抜粋して埋める (リンクだけでは不可)。

## テスト観点

該当する観点のみ。spec の `## 機能仕様 → Testing` から抜粋。

## 検証コマンド

`context/project.md` の Quick Commands にある標準 task を直接書く。

- 例: lint / typecheck / unit test / e2e / 健全性検査 のうち該当するもの

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR / MR を作成した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
```
