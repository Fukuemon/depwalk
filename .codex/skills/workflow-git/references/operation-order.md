# Git Workflow Order

Kufu Monorepo で Git / GitHub 操作を進めるときの標準順序。

## 標準順序

1. Issue または作業単位を確定する
2. `develop` から作業ブランチを切る
3. 実装・文書修正を行う
4. 差分確認と必要ファイルのステージングを行う
5. コミットメッセージを作成して commit する
6. 必要なら push する
7. 必要なら `develop` 向けに PR を作成する

## branch 作成前チェック

- `git status` で未整理の差分がないか確認する
- 現在の branch が `main` / `master` / `develop` なら、その branch 上で作業を続けない
- ブランチ名に入れる ticket / issue 番号を確定する

## protected branch guard

- `main` / `master` / `develop` は保護ブランチとして扱う
- これらの branch では commit / push / 直接作業をしない
- hook や tool guard がブロックした場合は回避せず、作業ブランチへ移る

## commit / PR / Issue で毎回確認すること

- 差分と本文の整合性
- 変更理由を説明できるか
- 関連 Issue / PR / spec のリンク先が正しいか
- base branch が `develop` になっているか

## push / PR の扱い

- `git push` はユーザーの明示依頼がある場合だけ行う
- `gh pr create` / `gh pr edit` も明示依頼がある場合だけ行う
- 明示依頼がない場合は本文案まで作って止める
