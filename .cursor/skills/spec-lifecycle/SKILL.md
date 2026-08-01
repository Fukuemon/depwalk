---
name: spec-lifecycle
description: spec を context/project.yml の spec.phases が定める phase 列 (既定は intake → scaffold → clarify → diagram → track → sync → prompts) で半自律に進め、gate phase 完了時に spec-review とユーザー承認を待つ orchestrator。"通しで設計" / "spec-lifecycle" / "spec-full" / "spec を最後まで" で起動する。
---
# Spec Lifecycle

spec を 1 phase ずつ進める **半自律 orchestrator**。設計プロセス全体 (scaffold 〜 prompts 生成) を
**単一コンテキストで** 進め、各 phase の手順は `references/phase-*.md` を on-demand で Read する
(別 skill 呼び出しに分割しない)。
ドメイン判断は必ずユーザーに確認を取り、推測では進めない。
gate phase の完了時に `spec-review` (fresh-context evaluator) を必ず通す。

## いつ使うか

- spec を下書きから実装 prompt 生成まで通したい
- 既存 spec の続きを resume したい

## 先に読むもの

- `references/spec-contract.md` (Spec Workflow Contract)
- `context/project.yml` の `spec.phases` (実行する phase の集合と順序)
- `references/phase-guide.md` (本プロジェクトで毎回確認する論点)
- 各 phase の手順は実行時に該当 `references/phase-*.md` を Read する (下記実行フロー参照)
- `styleguide-documents` skill (spec / phase 文書を書く際の品質基準)
- 対象 spec の `index.md` (再開時)

## 単一コンテキスト原則

論点テーブル・PhaseStatus・`spec-review` 結果は **この orchestrator の単一コンテキストで保持** する。
各 phase に入ったときだけ該当 `references/phase-*.md` を Read し、phase をまたいで状態を引き継ぐ
(参考 repo の `sekkei` と同方針)。phase の手順を別 skill として再起動せず、コンテキストの分断を避ける。
intake と gate レビューのみ、再利用ユーティリティとして別 skill を呼ぶ。

## 入力

- `$ARGUMENTS`: issue 番号 or 既存 spec path
- 引数なし → ユーザーに対象を確認

## Phase レジストリと実行順序

実行する phase の **集合と順序** は `context/project.yml` の `spec.phases` (識別子の配列) が決める。
各識別子を **どう実行するか**・gate かどうかは下のレジストリ (製品非依存・固定) が定める。
phase の採否・並べ替えは `spec.phases` の編集だけで完結し、本ファイルの編集は要らない。

| 識別子     | kind      | 実行 (Read する reference / 呼ぶ skill)            | gate | 必須停止タイミング                   |
| ---------- | --------- | -------------------------------------------------- | ---- | ------------------------------------ |
| `intake`   | external  | skill: `spec-issue-read` または `spec-requirement` | —    | 受け入れ再検証で欠落があれば差し戻し |
| `scaffold` | reference | `references/phase-scaffold.md`                     | —    | 上位文書と矛盾で停止                 |
| `clarify`  | reference | `references/phase-clarify.md` (全件)               | ✅   | **全論点で毎回停止** (必須)          |
| `diagram`  | reference | `references/phase-diagram.md`                      | —    | 未確定論点があれば停止               |
| `track`    | reference | `references/phase-track.md`                        | ✅   | 未確定論点があれば停止               |
| `sync`     | reference | `references/phase-sync.md` (該当時のみ)            | —    | 上位文書反映先の判断が読めない場合   |
| `prompts`  | reference | `references/phase-prompts.md`                      | ✅   | 未確定論点があれば停止               |

- `intake` は **受け入れ再検証** を含む: 入力 (issue / requirements doc) を `spec-requirement` の
  `references/intake-checklist.md` で再検証し、欠落 (誰が / なぜ / 完了条件の不明) があれば
  設計に入らず `spec-requirement` へ差し戻す。作成時に検証済みでも受け入れ側で再検証する
  (作成時の自己申告を信用しない)。ユーザーが「このまま進めて」と言ってもゲートは緩めない。
- `kind: reference` = 到達時に該当ファイルを Read して実行 / `kind: external` = 別 skill を起動。
- `gate: ✅` = 完了時に `spec-review` を実行。非 gate phase はレビューせず次へ進み、
  **次に現れる gate で未レビュー分を累積レビュー** する (token 節約)。スキップされた phase
  (例: 該当なしの `sync`) の範囲も次の gate に繰り越す。
- **最終 gate** = 配列で最後に現れる gate phase。`spec.phases` が未設定なら上表の並びを既定列とする。

## 中核原則 (共通ガード)

- 未確定論点が 1 件でも残っている場合、次 phase に進まず停止する
- 各 phase 完了後、必ず `[次 phase 提案] → [ユーザー同意] → [実行]` の順 (自動遷移しない)
- gate phase の完了時に `spec-review` 必須 (スキップ禁止)
- 確定済の判断 (解決済みの論点) を上書きしない
- 停止して人間判断を仰ぐ基準と停止テンプレートは `references/handoff.md` を正本とする

## 実行フロー

進捗をこのチェックリストで追う (既定列の場合。実際の並びは `spec.phases` に従う):

```text
- [ ] intake (spec-issue-read / spec-requirement)
- [ ] scaffold (phase-scaffold.md)
- [ ] clarify (phase-clarify.md 全件) → gate review
- [ ] diagram (phase-diagram.md)
- [ ] track (phase-track.md) → gate review
- [ ] sync (phase-sync.md, 該当時)
- [ ] prompts (phase-prompts.md) → gate review (最終)
- [ ] 各 phase 完了時: PhaseStatus 更新 + WIP commit
```

### 1. 事前確認

- 対象 issue / spec path を確定し、`spec.phases` を読んで実行列を確定する
- 既存 spec がある場合、`## 設計フェーズ状況` を参照して現在 phase を検出:
  - `進行中` → その phase から再開
  - `完了` → **gate phase ならレビュー未了扱い**。同 phase の `spec-review` から再開。
    非 gate phase は `完了` を終端とし、次 phase に進める
  - `保留` → 備考解消をユーザーに確認、解消後に `進行中` に戻し再実行。
    複数あれば `spec.phases` の並び順で先に来るものから解消する
  - `未着手` → 直近の `未着手` から開始
- テーブルが無い旧 spec はテンプレートから該当 section を追加提案

### 2. phase 実行ループ

各 phase のサイクル:

```text
[ユーザー同意] → [reference Read + phase 実行] → [ハンドオフ判定 (handoff.md)]
  → (gate のみ) [spec-review] → [指摘対応判定]
  → [PhaseStatus 更新] → [次 phase 提案] → [ユーザー同意] → …
```

配列の各識別子をレジストリで引き、`reference` なら Read して実行、`external` なら対応 skill を起動する。

### 3. gate レビュー (gate phase の完了時のみ)

- gate phase 完了時に `spec-review` を実行し、**前の gate 以降の未レビュー phase 分を累積して** 渡す
- 結果を `PASS` / `NEEDS_WORK` で記録
- **NEEDS_WORK の自動修正禁止**。方針をユーザーに確認
- 自動修正を許可する対象は以下に限定:
  - typo (誤字脱字)
  - リンク切れ
  - 見出し整形 (Markdown 見出しレベル / 番号揃え)
  - 表組の体裁 (カラム整形 / 空白調整)
- 対応後、再度 `spec-review` を回して PASS を確認

### 4. PhaseStatus 更新と issue の status 遷移

phase 完了 (レビュー指摘対応含む) ごとに spec の `## 設計フェーズ状況` を更新:

- 作業開始時: `進行中` / 最終更新日
- phase 完了: `完了`
- gate でレビュー指摘対応完了: `レビュー済` (非 gate phase は `完了` が終端)
- ユーザー判断待ち: `保留` + 備考に理由

各 phase 完了時、PhaseStatus 更新とあわせて WIP commit を残す (`workflow-git` の運用に従う)。
長時間 run でも phase 単位で handoff が保全され、git log が進捗の第二記録になる。protected branch へは直接コミットしない。

issue 側の進捗要約は `status:*` ラベル + 状態遷移コメントで残す (正本: `workflow-git` の
`references/issue-status.md`)。本 skill が行う遷移は 2 箇所のみ:

- scaffold 開始時: → `status:designing`
- 最終 gate レビュー済 (prompts 生成完了): → `status:ready-to-implement`

遷移の承認は phase 完了報告の同意と同時に取る (2 度聞きしない)。

### 5. clarify と track の二重追記防止

phase: clarify (`phase-clarify.md`) は決定内容を `## 上位資料からの変更点` に追記する責務を持つ
(追記行に `source: clarify` を残す)。
phase: track (`phase-track.md`) では **clarify で未追記の変更のみ** 反映する。

### 6. 進捗報告

各 phase 完了時:

```text
✅ {phase 識別子}: {phase 名} {状態}
  - 変更箇所: {概要}
  - レビュー: (gate のみ) PASS / NEEDS_WORK + 指摘件数
  - PhaseStatus: {状態}
次 phase: {次の識別子} に進みますか？ (Y / 後で / 中断)
```

intake と非 gate phase はレビュー行を省略する (`状態` は `完了`)。

## 停止条件

- 最終 gate phase `レビュー済`
- 最終レビューで `PASS`
- 論点テーブル未決定ゼロ
- `<spec dir>/prompts/` 生成完了
- ユーザーにサマリ + 実装セッション起動の提案を提示

## 禁止事項

- ドメインロジックを推測で決める
- `spec-review` の NEEDS_WORK を独断で修正 (typo 等の例外を除く)
- 論点テーブルに未決定を残したまま clarify より後の phase に進む
- gate レビューのスキップ
- ユーザー確認なしの次 phase 移行
- 確定済み判断 (解決済みの論点) の上書き
- ユーザー応答待ちタイムアウト (「反応がないので進めます」)
