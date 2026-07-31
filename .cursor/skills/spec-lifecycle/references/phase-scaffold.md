# Phase: scaffold

issue から spec dir と `index.md` を template ベースで生成する phase。
PRD / Design Doc / feature doc / context / ADR と整合をとった上でスキャフォルディングし、矛盾を検知したら phase: sync (`phase-sync.md`) を提案する。

## 先に読むもの

- `spec-contract.md` (Spec Workflow Contract) — 正本 path / templates (target は `context/project.yml` の `domains`)
- `reconcile-upstream.md` (同 dir) — 上位文書整合の手順
- 対象 issue (なければ phase 1 intake で `spec-issue-read` を先に呼ぶ)

## 手順

1. **issue 把握**: 対象 issue 内容を取得 (既に把握済みならスキップ可)
2. **上位文書読み込み**: `Spec Workflow Contract` の PRD / Design Doc / feature doc / context / ADR path を読み、対象機能に関係する節だけを抽出する
3. **spec dir 決定**:
   - 形式: `<spec dir base>/<issue-id>-<slug>/`
   - 既存ディレクトリがあれば上書きせず、差分提案のみ
4. **整合チェック** (`reconcile-upstream.md`):
   - PRD のスコープ / Non Goals と矛盾していないか
   - Design Doc のモジュール責務 / Phase / 全体像と矛盾していないか
   - feature doc の設計方針 / context の architecture・規約・運用契約と矛盾していないか
   - 関連 ADR があれば該当 ID を控える
   - 矛盾を検出した場合は **phase: sync を提案して停止**
5. **`index.md` 生成**: `templates/specs/template.md` (minimal core) をコピーし、以下を埋める:
   - メタ情報 (Issue 番号 / Branch / Owner / Status)
   - 設計フェーズ状況 (intake = 完了, scaffold = 進行中)
   - 上位文書整合 (PRD / Design Doc / feature doc / context / ADR の節と整合方針)
   - 関連資料 / 背景 / スコープ / 要件の解釈
   - 実装対象 (`context/project.yml` の `domains` から該当を ◯)
   - 機能仕様の各サブセクション (該当しない場合は空のまま残す)
6. **Appendix 取り込み判定**: 機能種別に応じて `templates/specs/appendices/<topic>.md` を取り込むか確認:
   - API endpoint がある → `appendices/api.md`
   - 永続データ層がある → `appendices/database.md`
   - ロール / 権限がある → `appendices/authorization.md`
   - 画面コンポーネントツリーがある → `appendices/screen-spec.md`
   - E2E 対象 UI がある → `appendices/testid.md`
   - **ユーザーに確認してから挿入する** (該当する appendix が無いスコープなら何も追加しない)
7. **増分判定 (必須)**: 対象が **既存実装への増分** か **ゼロからの新規** かを判定する。
   次のいずれかに該当すれば増分として、spec の `## 設計フェーズ状況` 備考に「実装突合: 対象」と明記する
   (phase: clarify 冒頭の「実装との突合ゲート」の対象になる):
   - 対象機能の spec / 実装が既に存在する (`specs/` に過去版がある、実装ファイルがある)
   - 複数 feature が共有する挙動 (状態判定・遷移・不変条件・横断フロー) を参照・変更する
   - issue が「〜を追加」「〜を変更」「既存の〜に」等、新規でなく増分を示している
8. **初期論点の洗い出し**: issue / 上位文書から未確定事項を `論点一覧` に列挙
9. 次アクションを提案: phase: clarify (`phase-clarify.md`) で論点解決へ

> 正本境界 (`Spec Workflow Contract`): 作成段階では spec が自身の決定の作業正本でよい。durable な設計成果 (IA / フロー / データモデル等) を design へ反映し正本を移すのは phase: sync の正本ハンドオフで行う。draft 段階で design 側を勝手に書き換えない。

## 停止条件

- 上位文書 (PRD / Design Doc / feature doc / context / 既存 ADR) と矛盾しており、解決方針が決まらない
- 対象 issue または spec dir が決まらない
- 実装対象 app / package が `context/project.yml` の `domains`から特定できない
- `templates/specs/template.md` が見つからない
- 既存 spec を上書きしようとしている
