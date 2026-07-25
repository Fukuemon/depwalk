# ブランチ命名規則

## フォーマット

```text
<prefix>/<チケット番号>
```

**パターン**: `^(feature|research|fix|refactor)/[0-9]+$`

## Prefix 一覧

| Prefix     | 用途                   |
| ---------- | ---------------------- |
| `feature`  | 新機能追加             |
| `research` | 調査、設計、検証       |
| `fix`      | 不具合修正             |
| `refactor` | 振る舞いを変えない整理 |

## 例

```text
feature/23426
research/23480
fix/23500
refactor/23512
```

## Git Flow 前提の運用

- 基本ブランチは `main` または `master` と `develop` を想定する
- 日常的な作業ブランチは原則 `develop` から切る
- `feature/*`、`research/*`、`fix/*`、`refactor/*` は作業後に `develop` へ戻す

## チケット番号の抽出

ブランチ名からチケット番号を抽出する場合:

```bash
git branch --show-current | grep -oE '[0-9]+$'
```
