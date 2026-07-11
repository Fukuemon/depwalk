# Prompt Rules

phase: prompts (`phase-prompts.md`) で生成する prompt の粒度 / 責務境界 / 自己完結性を揃えるルール。

## 設計原則

- 1 prompt = 1 責務 (target 1 つ × scope 1 つ)
- 明示された path 以外の探索を前提にしない
- 依存関係は phase で表現する (P1 → P2 → P3)
- 同一 phase 内の prompt は **並列可** または **並列不可** を明示する
- target を無理に 1 本にまとめない (`Spec Workflow Contract` の target 一覧で分ける)
- 未確定事項が残るなら prompt を作らず、spec 側へ戻す

## 自己完結性ルール

- 実装に必要な spec 抜粋を prompt 本文に **コピーする** (spec の参照だけでは不可)
- 他 app / package の追加探索を要求しない
- 検証コマンドは `context/project.md` の Quick Commands にある標準 task を直接書く
- 「既存コードを参考に」「既存実装を確認して」等の探索誘発表現を使わない

## 受け入れ基準の EARS 風書き換え

spec の `## 要件の解釈` の EARS 風記述から、対応する受け入れ基準だけを抜粋する。

- WHEN ... THEN ...
- IF ... THEN ...
- WHILE ... THE SYSTEM SHALL ...

## 完了条件のタスク化ルール

生成する prompt の `## 絶対ルール` セクション末尾に必ず以下を含める:

```text
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、
  各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止。
```

## 実装アンチパターンの注入

各 prompt の `## 絶対ルール` に `antipatterns.md` (同 dir) の制約ブロックを必ず注入する。
スコープクリープ / 観測可能契約の無断変更 / 推測実装 / 過剰 fallback / dead code を防ぐ。

## ブランチ準備

各 prompt の `## 作業ステップ` 冒頭に「ステップ 0: ブランチ準備」を入れる。
ブランチ命名 / ベースブランチは `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` の値を使う。
prompt にプロジェクト固有のブランチ運用 (target branch 切替手順 等) を直書きしない。

## 命名規則

```text
P{phase}_{seq}_{target}_{scope}.md
```

- `phase`: 整数 (1 始まり)
- `seq`: 2 桁ゼロ埋め (`01`, `02`, ...)
- `target`: `Spec Workflow Contract` の target 一覧から 1 つ
- `scope`: ケバブケースで責務を表す (`page_shell` / `button_variants` / `contact_flow` 等)

## phase の決め方

- P1: 他 prompt に依存しない基盤作業 (型 / 共通 component / 設定)
- P2 以降: P1 完了後に着手できる feature 単位
- 同一 phase 内で並列実行可能かは「変更ファイルの衝突」で判定する

### 統合の判断基準

モデル容量が許す限り 1 prompt に統合する:

- 同一リソースの CRUD
- 同一 entity への拡張
- 同一画面の全セクション

### 分割を維持すべき境界

- DDL / migration と application code
- backend と frontend (target が異なる)
- frontend と E2E test
- target をまたぐ場合は必ず別 prompt にする

### 依存関係の例

| 依存関係                        | phase 分割               |
| ------------------------------- | ------------------------ |
| Schema → 全 backend / frontend  | Schema を最初の phase に |
| Backend API → Frontend          | Frontend は API の後     |
| 全画面 → E2E                    | E2E は最後               |
| 同じデータ層を参照する API 同士 | 並列 OK                  |

## レビュー

各ステップに「diff レビュー」手順を入れる。
レビュー実行手段 (codex / 別 AI / `spec-review`) は repo / ユーザーの設定に合わせて prompt 内で具体化する。
本 phase は **特定のレビュー CLI を強制しない**。

## 禁止事項

- spec に書かれていない技術選定を prompt が独自に行う
- 個人 PC の絶対 path を埋め込む (`$(ghq root)` 等の動的解決を使う)
- 不明点を推測で埋める / TODO 残しで先に進む
- 別 prompt の責務範囲に踏み込む
- 完了条件にチェックリスト形式以外を使う
