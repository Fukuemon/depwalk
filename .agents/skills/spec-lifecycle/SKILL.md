---
name: spec-lifecycle
description: >-
  Drives a spec through intake → scaffold → clarify → diagram → track → sync →
  tasks → review in a half-autonomous loop, stopping at every phase gate for
  user approval. Use when the user asks to "通しで設計" / "spec-lifecycle" /
  "spec-full" / "spec を最後まで".
---
# Spec Lifecycle

spec を 1 phase ずつ進める **半自律 orchestrator**。
ドメイン判断は必ずユーザーに確認を取り、推測では進めない。
各 phase 完了時に `spec-review` (fresh-context evaluator) を必ず通す。

## いつ使うか

- spec を下書きから実装 prompt 生成まで通したい
- 既存 spec の続きを resume したい

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- `references/phase-guide.md` (本プロジェクトで毎回確認する論点)
- 対象 spec の `index.md` (再開時)

## 入力

- `$ARGUMENTS`: issue 番号 or 既存 spec path
- 引数なし → ユーザーに対象を確認

## 中核原則

### 共通ガード (全 phase 共通)

- 未確定論点が 1 件でも残っている場合、次 phase に進まず停止する
- 各 phase 完了後、必ず `[次 phase 提案] → [ユーザー同意] → [実行]` の順 (自動遷移しない)
- `spec-review` は phase 2 以降の完了時に必須
- 確定済の判断 (解決済みの論点) を上書きしない

### ハンドオフ基準 (必ず人間判断を仰ぐ)

以下を検知したら停止してユーザーに質問する。推測禁止:

1. **ドメインロジック**: 業務意味が複数解釈できる / 要件未明記の振る舞い
2. **データ整合性**: 既存データとの整合リスク、破壊的変更の可能性
3. **認可 / 権限**: ロール別アクセス可否が要件から読めない
4. **トランザクション境界**: 複数 op 横断の失敗補償が不明確
5. **画面 / コンテンツ仕様との乖離**: spec と要求の矛盾
6. **既存規約との不整合**: `workflow-git` / `Spec Workflow Contract` と衝突
7. **`spec-review` の NEEDS_WORK**: 自動修正禁止 (typo / リンク切れ / 見出し整形 / 表組体裁 のみ自動修正可)
8. **論点テーブルに未決定が残存している状態での次 phase 進行**

停止テンプレート:

```text
【判断が必要です: {phase 名}】
論点: {何が不明か}
選択肢:
  A) {案 A} — メリット / デメリット
  B) {案 B} — メリット / デメリット
  (推奨: A / なし)
このまま進めますか？選択肢を教えてください。
```

## 実行フロー

進捗をこのチェックリストで追う (各 phase 完了時に更新):

```text
- [ ] Phase 1: intake (issue-read / requirement)
- [ ] Phase 2: scaffold (spec-draft) → review
- [ ] Phase 3: clarify (spec-resolve 全件) → review
- [ ] Phase 4: diagram (spec-diagrams) → review
- [ ] Phase 5: track (spec-track) → review
- [ ] Phase 6: sync (spec-sync, 該当時) → review
- [ ] Phase 7: tasks (spec-prompts) → review
- [ ] 各 phase 完了時: PhaseStatus 更新 + WIP commit
```

### 1. 事前確認

- 対象 issue / spec path を確定
- 既存 spec がある場合、`## 設計フェーズ状況` を参照して現在 phase を検出:
  - `進行中` → その phase から再開
  - `完了` → **レビュー未了扱い**。同 phase の `spec-review` から再開 (phase 1 は除く)
  - `保留` → 備考解消をユーザーに確認、解消後に `進行中` に戻し再実行
  - `未着手` → 直近の `未着手` から開始
- テーブルが無い旧 spec はテンプレートから該当 section を追加提案

### 2. phase 実行ループ

各 phase のサイクル:

```text
[ユーザー同意] → [skill 実行] → [ハンドオフ判定] → [spec-review] → [指摘対応判定]
  → [PhaseStatus 更新] → [次 phase 提案] → [ユーザー同意] → …
```

| Phase | Skill                                       | 必須停止タイミング                 |
| ----- | ------------------------------------------- | ---------------------------------- |
| 1     | `spec-issue-read` または `spec-requirement` | 要件曖昧なら停止                   |
| 2     | `spec-draft`                                | 上位文書と矛盾で停止               |
| 3     | `spec-resolve` (全件)                       | **全論点で毎回停止** (必須)        |
| 4     | `spec-diagrams`                             | 未確定論点があれば停止             |
| 5     | `spec-track`                                | 未確定論点があれば停止             |
| 6     | `spec-sync` (該当時のみ)                    | 上位文書反映先の判断が読めない場合 |
| 7     | `spec-prompts`                              | 未確定論点があれば停止             |
| 8     | `spec-review` (phase 2 以降の完了時)        | NEEDS_WORK 残存                    |

### 3. レビュー (各 phase 完了時)

- phase 1 以外、完了時に `spec-review` を必ず実行
- 結果を `PASS` / `NEEDS_WORK` で記録
- **NEEDS_WORK の自動修正禁止**。方針をユーザーに確認
- 自動修正を許可する対象は以下に限定:
  - typo (誤字脱字)
  - リンク切れ
  - 見出し整形 (Markdown 見出しレベル / 番号揃え)
  - 表組の体裁 (カラム整形 / 空白調整)
- 対応後、再度 `spec-review` を回して PASS を確認

### 4. PhaseStatus 更新

phase 完了 (レビュー指摘対応含む) ごとに spec の `## 設計フェーズ状況` を更新:

- 作業開始時: `進行中` / 最終更新日
- skill 完了: `完了`
- レビュー指摘対応完了: `レビュー済`
- ユーザー判断待ち: `保留` + 備考に理由

各 phase 完了時、PhaseStatus 更新とあわせて WIP commit を残す (`workflow-git` の運用に従う)。
長時間 run でも phase 単位で handoff が保全され、git log が進捗の第二記録になる。protected branch へは直接コミットしない。

### 5. spec-resolve と spec-track の二重追記防止

`spec-resolve` は決定内容を `## 上位資料からの変更点` に追記する責務を持つ。
phase 5 `spec-track` では **spec-resolve で未追記の変更のみ** 反映する。

### 6. 進捗報告

各 phase 完了時:

```text
✅ Phase N: {phase 名} {状態}
  - 変更箇所: {概要}
  - レビュー: PASS / NEEDS_WORK + 指摘件数
  - PhaseStatus: {状態}
次 phase: {N+1. 名前} に進みますか？ (Y / 後で / 中断)
```

phase 1 はレビュー対象外のため、レビュー行は省略する。

## 停止条件

- phase 7 `レビュー済`
- 最終レビューで `PASS`
- 論点テーブル未決定ゼロ
- `<spec dir>/prompts/` 生成完了
- ユーザーにサマリ + 実装セッション起動の提案を提示

## 禁止事項

- ドメインロジックを推測で決める
- `spec-review` の NEEDS_WORK を独断で修正 (typo 等の例外を除く)
- 論点テーブルに未決定を残したまま phase 4 以降に進む
- `spec-review` のスキップ
- ユーザー確認なしの次 phase 移行
- 確定済み判断 (解決済みの論点) の上書き
- ユーザー応答待ちタイムアウト (「反応がないので進めます」)
