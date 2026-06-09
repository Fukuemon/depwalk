# GitHub Issue フォーマット

GitHub Issue のタイトル・本文フォーマット。

## Issue の種類

| type       | タイトル形式            | 推奨ラベル    | 用途                           |
| ---------- | ----------------------- | ------------- | ------------------------------ |
| `feature`  | `✨ feature: <サマリ>`  | `enhancement` | 新機能、利用者価値を増やす改善 |
| `bug`      | `🐛 bug: <サマリ>`      | `bug`         | 不具合修正                     |
| `research` | `🔎 research: <サマリ>` | `research`    | 調査、技術検証、スパイク       |
| `task`     | `🛠 task: <サマリ>`     | `task`        | 実装や運用で実施すべき具体作業 |
| `chore`    | `🧹 chore: <サマリ>`    | `chore`       | 保守、設定変更、依存更新、雑務 |

## タイトル

```text
<絵文字> <type>: <サマリ>
```

- 日本語で簡潔に、末尾句点なし
- 50 文字以内を目安にする
- type は本文テンプレートと一致させる

## 本文テンプレート

- `feature`: [../assets/issue-feature-template.md](../assets/issue-feature-template.md)
- `bug`: [../assets/issue-bug-template.md](../assets/issue-bug-template.md)
- `research`: [../assets/issue-research-template.md](../assets/issue-research-template.md)
- `task`: [../assets/issue-task-template.md](../assets/issue-task-template.md)
- `chore`: [../assets/issue-chore-template.md](../assets/issue-chore-template.md)

## メッセージ生成の原則

1. 実際の問題や要件を正確に把握してから書く
2. 曖昧な表現を避け、具体的な作業や確認条件を書く
3. 調査 issue では「何を判断できれば終わりか」を明示する
4. task や chore では、作業内容を検証可能な粒度に分解する
