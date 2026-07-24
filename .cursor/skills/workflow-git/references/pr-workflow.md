# PR Workflow

## 作成前チェック

- 現在の branch が作業ブランチである
- 差分が PR タイトル / 本文と一致している
- 関連 Issue と spec のリンクが整理されている
- base branch が `develop` でよい

## PR 本文に含めること

- Summary: この PR で達成したこと
- Background: 必要なら背景や制約
- Changes: 主要変更点
- Testing: 実施内容または未実施理由
- Related Issues: `Closes` / `Refs`
- Notes: 残課やレビュー観点

## 本リポジトリ の PR で意識すること

- PRD / Design Doc / feature doc / context / spec / ADR のどこを更新したかを明示する
- app / package 側の実装前提が変わる場合は Notes で伝える

## マージ後 (issue が close したとき)

- 対応する spec (`specs/<issue-id>-*/`) が残っていれば、`spec-lifecycle` の `references/closeout.md` に従い
  sync の取りこぼしゼロを確認して spec を削除する (削除 commit に issue 番号を残す)

## 停止条件

- base branch が `main` / `master` になっているが、明示指示がない
- Testing を空欄にしないと本文が作れない
- Related Issues が曖昧で、レビュー単位を追跡できない
