# PR メッセージフォーマット

GitHub Pull Request（PR）のタイトル・本文フォーマット。

## タイトル

推奨パターンは次のどちらか。

```text
type(scope): サマリ
```

```text
簡潔な日本語要約
```

- `#{番号} ...` のように Issue 番号をタイトル先頭へ付けない
- 50 文字以内を目安に、末尾句点なしで簡潔に書く
- コミット規約と整合する `type` を使うとレビューしやすい

## 本文テンプレート

PR 本文の雛形は [../assets/pr-body-template.md](../assets/pr-body-template.md) を使う。
差分に合わせて不要な節は削り、必要な節だけを具体化する。
通常の PR ターゲットブランチは `develop` とし、`main` / `master` を使うのは明示指示がある場合に限る。

## メッセージ生成の原則

1. 必ず `git diff` と `git log` で差分・コミット履歴を確認する
2. issue タイトルやブランチ名からの推測だけで本文を書かない
3. Summary と Changes で、何をどう変えたかを具体的に書く
4. Testing では確認手順や未実施理由を明示する
5. Related Issues では `Closes #123` / `Refs #123` を使う
6. base branch は `develop` を明示し、例外時だけ `main` / `master` を使う
