---
name: context-bootstrap
description: Design Doc と短いインタビューからプロジェクトプロファイル (context/project.md) と初期 context ライブラリ (context/*.md) を生成する。新規プロダクトでテンプレートを使い始めるとき、context/ が空またはプレースホルダのままのとき、"context-bootstrap" / "context 初期化" で起動する。
---

# Context Bootstrap

初期 `design/DesignDoc.md` と短い対話を入力に、**プロジェクト profile (`context/project.md`) と context library (`context/*.md`)** を template から生成する。新規プロダクトで本テンプレートを使い始めるときの初期化 skill。

## いつ使うか

- 本テンプレートを新しいプロダクトに適用し始めるとき
- `context/project.md` が無い、または `context/*.md` が `<PLACEHOLDER>` のままのとき
- Design Doc は出来たが、技術規約レイヤがまだ無いとき

## 先に読むもの

- `styleguide-documents` skill
- `design/DesignDoc.md` (Why/What/How の前提)
- `.rulesync/rules/CLAUDE.md` の `Spec Workflow Contract`
- `templates/context/*.md` (生成元スケルトン)

## 実行フロー

> 流れ: DesignDoc 読込 → Required Context の対話確定 → project.md 生成 → context/\*.md 生成 → 未確定点の確認依頼

### 1. Design Doc から導出できる値を抽出

`design/DesignDoc.md` のモジュール責務 / Engineering Context / Overview から、技術スタック・モジュール構成・境界の手掛かりを集める。

### 2. Required Context を対話で確定

DesignDoc から導けない固有値をユーザーに確認する (1 問ずつでなくまとめて提示):

- リポジトリマップ (app repo / infra repo / ghq などのパス規約)
- パッケージ scope・命名規約 (`@<scope>/<module>` 等)・branch pattern
- Quick Commands (dev / build / lint / typecheck / test / e2e / 依存追加)
- 対象ドメイン (spec「実装対象」テーブル用の一覧)
- Issue Tracker (種別 / CLI / Repo)

確定できない値は `<PLACEHOLDER>` のまま残し、後でユーザーが埋める前提にする。

### 3. project.md 生成 (必須 — スキップ禁止)

確定した Required Context を `context/project.md` に書く。本ファイルが全 skill / CLAUDE.md の固有値の唯一の正本。

### 4. context library 生成

`templates/context/{architecture,toolchain,engineering,testing,infrastructure}.md` を `context/` 直下へ展開し、DesignDoc と Required Context から埋められる箇所を埋める。各ファイル先頭の `> 最終更新:` を当日に更新する。`context/README.md` は汎用契約として維持する。

> **ガード**: 既存の `context/*.md` を上書きする場合は差分案を提示し、ユーザー承認を得る。

### 5. 未確定点の確認依頼と次の案内

- `<PLACEHOLDER>` が残る箇所を一覧でユーザーに提示し、補完を依頼する。
- 次工程として `rulesync-sync` (各 provider へ同期)、続いて `spec-*` を案内する。

## 停止条件

- `design/DesignDoc.md` が存在せず、技術前提が読み取れない (先に `design-doc` skill を案内する)
- Required Context の必須値 (Tracker / Repo / 主要コマンド) が確定せず `<PLACEHOLDER>` の意味も合意できない
- 既存 `context/*.md` の上書きがユーザー承認されていない
