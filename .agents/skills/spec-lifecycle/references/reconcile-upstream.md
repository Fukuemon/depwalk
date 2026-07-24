# Reconcile Upstream Docs

phase: scaffold / clarify / prompts (`phase-scaffold.md` / `phase-clarify.md` / `phase-prompts.md`) が PRD / Design Doc / feature doc / context / ADR と spec の整合を検証するための手順。正本 path は `Spec Workflow Contract` から取得する。

## 入力

- PRD path (`Spec Workflow Contract` 参照)
- Design Doc path (同上)
- feature doc (`design/features/<feature>/DesignDoc_<feature>.md`)
- context library (`context/<topic>.md`)
- ADR dir (同上)
- 対象 issue 要約 / draft 内容

## 検証手順

1. PRD の以下を確認:
   - スコープ (Phase 内 / Phase 外)
   - Non Goals
   - 関連する Persona / Outcome / Guardrail
2. Design Doc (landscape) の以下を確認:
   - モジュール責務 (`apps/*`, `packages/*`, `e2e`)
   - Phase 方針 / 設計上の重要ポイント / Alternatives
3. feature doc (`design/features/<feature>/DesignDoc_<feature>.md`) の以下を確認:
   - 対象 feature の設計方針 / データ構造 / 主要ユースケース / テスト観点
4. context (`context/<topic>.md`) の以下を確認:
   - architecture (package / runtime / state boundary)
   - toolchain / engineering (task / config / quality gate)
   - testing (E2E runtime contract) / infrastructure (deploy / env / security)
5. ADR を以下で検索:
   - 関連する技術選定 (例: framework / routing / data fetching)
   - 既に却下された代替案
6. 各項目を以下のいずれかに分類:
   - **継承**: 上位文書と完全に整合
   - **補足**: 上位文書の枠内で詳細化 (spec 側で OK)
   - **変更提案**: 上位文書の更新が必要 (spec だけでは閉じない)

## 出力

spec の `## 上位文書整合` テーブルに以下を記入する:

| 上位文書    | 節 / 該当箇所              | 整合方針 (継承 / 補足 / 変更提案) |
| ----------- | -------------------------- | --------------------------------- |
| PRD         | スコープ → Phase 1         | 継承                              |
| Design Doc  | モジュール責務 → ui        | 補足                              |
| feature doc | lp-site → 設計方針         | 補足                              |
| context     | engineering → quality gate | 継承                              |
| ADR-0003    | UI 共通化判断条件          | 変更提案                          |

## 「変更提案」を 1 件でも検出したときの扱い

- spec の `論点一覧` に `<上位文書> への変更提案` を追加する
- 下流 phase (clarify / diagram / prompts) に進む前に phase: sync (`phase-sync.md`) を提案して停止する
- spec 単独で勝手に上位文書を書き換えてはならない
