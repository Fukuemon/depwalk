---
name: spec-lifecycle
description: >-
  spec を intake → scaffold → clarify → diagram → track → sync → tasks → review
  の順に半自律で進め、各 phase gate でユーザー承認を待つ orchestrator。"通しで設計" / "spec-lifecycle" /
  "spec-full" / "spec を最後まで" で起動する。
---

# Spec Lifecycle

spec を 1 phase ずつ進める **半自律 orchestrator**。設計プロセス全体 (scaffold 〜 prompts 生成) を
**単一コンテキストで** 進め、各 phase の手順は `references/phase-*.md` を on-demand で Read する
(別 skill 呼び出しに分割しない)。
ドメイン判断は必ずユーザーに確認を取り、推測では進めない。
各 phase 完了時に `spec-review` (fresh-context evaluator) を必ず通す。

## いつ使うか

- spec を下書きから実装 prompt 生成まで通したい
- 既存 spec の続きを resume したい

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- `references/phase-guide.md` (本プロジェクトで毎回確認する論点)
- 各 phase の手順は実行時に該当 `references/phase-*.md` を Read する (下記実行フロー参照)
- `styleguide-documents` skill (spec / phase 文書を書く際の品質基準)
- 対象 spec の `index.md` (再開時)

## 単一コンテキスト原則

論点テーブル・PhaseStatus・`spec-review` 結果は **この orchestrator の単一コンテキストで保持** する。
各 phase に入ったときだけ該当 `references/phase-*.md` を Read し、phase をまたいで状態を引き継ぐ
(参考 repo の `sekkei` と同方針)。phase の手順を別 skill として再起動せず、コンテキストの分断を避ける。
intake (phase 1) と review gate (phase 8) のみ、再利用ユーティリティとして別 skill を呼ぶ。

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
- [ ] Phase 1: intake (spec-issue-read / spec-requirement)
- [ ] Phase 2: scaffold (phase-scaffold.md) → review
- [ ] Phase 3: clarify (phase-clarify.md 全件) → review
- [ ] Phase 4: diagram (phase-diagram.md) → review
- [ ] Phase 5: track (phase-track.md) → review
- [ ] Phase 6: sync (phase-sync.md, 該当時) → review
- [ ] Phase 7: tasks (phase-prompts.md) → review
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

各 phase のサイクル (phase 2-7 は該当 reference を Read してから実行):

```text
[ユーザー同意] → [reference Read + phase 実行] → [ハンドオフ判定] → [spec-review] → [指摘対応判定]
  → [PhaseStatus 更新] → [次 phase 提案] → [ユーザー同意] → …
```

| Phase | 実行 (Read する reference / 呼ぶ skill)            | 必須停止タイミング                 |
| ----- | -------------------------------------------------- | ---------------------------------- |
| 1     | skill: `spec-issue-read` または `spec-requirement` | 要件曖昧なら停止                   |
| 2     | `references/phase-scaffold.md`                     | 上位文書と矛盾で停止               |
| 3     | `references/phase-clarify.md` (全件)               | **全論点で毎回停止** (必須)        |
| 4     | `references/phase-diagram.md`                      | 未確定論点があれば停止             |
| 5     | `references/phase-track.md`                        | 未確定論点があれば停止             |
| 6     | `references/phase-sync.md` (該当時のみ)            | 上位文書反映先の判断が読めない場合 |
| 7     | `references/phase-prompts.md`                      | 未確定論点があれば停止             |
| 8     | skill: `spec-review` (phase 2 以降の完了時)        | NEEDS_WORK 残存                    |

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

### 5. clarify と track の二重追記防止

phase: clarify (`phase-clarify.md`) は決定内容を `## 上位資料からの変更点` に追記する責務を持つ
(追記行に `source: clarify` を残す)。
phase: track (`phase-track.md`) では **clarify で未追記の変更のみ** 反映する。

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
