---
name: spec-prompts
description: Generates self-contained implementation prompts under specs/<...>/prompts/ from a finalized spec. Use when the user asks for "プロンプト生成" / "実装プロンプト" / "spec-prompts" after a spec passes its review gate.
targets:
  - "*"
---

# Spec Prompts

完成済 spec から、後続の実装セッションが自己完結に実行できる Markdown prompt を `specs/<...>/prompts/` 配下に生成する。

## 絶対ガード (違反時は生成を中止してやり直し)

生成した **すべての prompt** に以下のセクションが揃っていること。
1 つでも欠けていたらその prompt は未完成。書き直す。

| #   | 必須セクション          | 省略不可の理由                                             |
| --- | ----------------------- | ---------------------------------------------------------- |
| 1   | `## 絶対ルール`         | 探索禁止 / 不明点ハンドリング / 別 prompt 領域への侵入禁止 / `references/antipatterns.md` の制約ブロック |
| 2   | `## 作業ステップ`       | 各ステップに検証手順を含む                                 |
| 3   | `## 実装コンテキスト`   | spec / 関連 path / 必要な前提を列挙                        |
| 4   | `## 前提条件`           | 依存先 prompt / 必要な repo 状態                           |
| 5   | `## 不明点ハンドリング` | 矛盾 / 欠落時に停止して確認する指示                        |
| 6   | `## タスク境界`         | 実装する範囲 / しない範囲                                  |
| 7   | `## 設計仕様`           | spec 該当節を必要最小限で抜粋                              |
| 8   | `## テスト観点`         | 該当 spec のテスト観点を抜粋                               |
| 9   | `## 検証コマンド`       | repo の標準 task (build / lint / typecheck / test / e2e)   |
| 10  | `## 完了条件`           | チェックリスト形式。実行者は最初にこれをタスク化する       |

## いつ使うか

- spec の論点が全て解決済 (`未確定事項` ゼロ) で、`spec-review` を通過済
- 設計を実装単位に分割したい

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- `references/prompt-rules.md` — 分割 / 命名 / 自己完結性のルール
- `references/prompt-template.md` — prompt 本体テンプレート
- `references/antipatterns.md` — 生成 prompt の `## 絶対ルール` に注入する実装制約
- 対象 spec の `index.md` 全体

## 入力

- `$ARGUMENTS` から対象 spec path を特定
- 引数なし → 会話コンテキストから推定

## 出力先

```text
specs/<...>/prompts/
├── P1_01_*.md    # phase 1: 並列不可の基盤作業
├── P2_01_*.md    # phase 2: P1 完了後
└── ...
```

## 命名規則

```text
P{phase}_{seq}_{target}_{scope}.md
```

- `phase`: 実装 phase (1 始まり)
- `seq`: phase 内連番 (2 桁ゼロ埋め)
- `target`: `context/project.md` の対象ドメイン一覧から選ぶ
- `scope`: 1 prompt で扱う責務 (ケバブケース)

例 (target はプロジェクトの対象ドメインに置き換える):

- `P1_01_<target>_page_shell.md`
- `P1_02_<target>_component_variants.md`
- `P2_01_<target>_flow.md`

## 実行フロー

1. spec の `未確定事項` がゼロであることを確認 (1 件でも残れば停止)
2. spec の review 結果が PASS であることを確認 (なければ `spec-review` を先に呼ぶ)
3. `references/prompt-rules.md` / `references/prompt-template.md` を読む
4. spec の操作 / 機能 / 依存関係を分析
5. target / phase で分割
6. 各 prompt を `prompt-template.md` に従って生成
7. **セルフ検証**: 各 prompt を絶対ガードの 10 項目で突合し、欠落があれば書き直す
8. 生成ファイル一覧と依存関係表を報告

## 生成後の報告

```md
| ファイル          | phase | target     | 並列可 | 依存先 | 概要 |
| ----------------- | ----- | ---------- | ------ | ------ | ---- |
| P1_01_<target>... | 1     | <target-a> | -      | なし   | ...  |
| P2_01_<target>... | 2     | <target-b> | P2_02  | P1_01  | ...  |
```

## 停止条件

- spec の `未確定事項` がゼロでない
- `spec-review` が PASS でない (NEEDS_WORK のまま prompts を作らない)
- target が `Spec Workflow Contract` の target 一覧から特定できない
- 絶対ガード 10 項目を満たせない prompt が残っている
- 別 app / package の追加探索を要求する prompt になっている (自己完結性違反)
