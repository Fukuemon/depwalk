# golangci-lint + depguard と生成依存図を quality gate へ組み込む

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。対象ファイルは本 prompt に記載済み)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- 有効化する linter は depguard のみ (他 linter の一括有効化はスコープ外)
- package の物理移動・改名をしない (フラット構成を維持する。D8)
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・
  先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの
  既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を
  要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが
  誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは
  「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。
  要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・
  変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

1. `feature/34` を checkout する (P1_01 で作成済み。Draft PR 継続)
2. P1_01 の完了 (依存是正済み・全テスト PASS) を確認する

### ステップ 1: golangci-lint + depguard の導入

1. golangci-lint をバージョン固定で導入し、`core/.golangci.yml` に depguard ルールを「設計仕様」のとおり定義する
2. **検証としてまず意図的な違反 import** (`domain` から `platform` を import する一時ファイル) **を書き、lint が理由付きメッセージで FAIL することを確認してから削除する**
3. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ 2: quality gate への組み込み

1. `lefthook.yml` の repo-analysis (Go quality) 系列に golangci-lint 実行を追加する
2. CI (`.github/workflows/ci.yml`) の Go job に同じ検査を追加する
3. `lefthook run pre-commit` が新 gate 込みで PASS することを確認し、diff レビューを回す

### ステップ 3: 依存図の生成と drift 検査

1. `scripts/depgraph.sh` を新規作成する: `cd core && go list -f '{{.ImportPath}} {{range .Imports}}{{.}} {{end}}' ./internal/... ./cmd/...` の出力から `core/internal` package 間のエッジだけを抽出し、mermaid (`graph LR`) に整形して `context/architecture.md` の生成マーカー区間 (`BEGIN GENERATED: core-depgraph` 〜 `END`) を置換する。冪等 (再実行で diff ゼロ) にする
2. スクリプトを実行し、生成された依存図が architecture.md の依存規則・P1_01 完了後の実 import と一致することを確認する
3. `lefthook.yml` の repo-analysis 系列に「depgraph 再生成 → `git diff --exit-code context/architecture.md`」の drift 検査を追加する (CI にも同じ検査を追加)
4. diff レビューを回す

### ステップ最終: 最終確認

1. 全テスト / lint / vet / fmt / E2E がパスすることを確認する
2. spec #32 の `## 上位資料からの変更点` の該当行に反映済み注記を追記する
3. PR を Ready に変更しレビュアーを指名する (merge で issue #34 クローズ)

## 実装コンテキスト

- spec: `specs/32-architecture-refactor/index.md` (解決済みの論点 D5 / D8 / テスト・評価方針)
- 正本: `context/engineering.md` (依存方向 gate)、`context/architecture.md` (Package Boundary の依存規則と生成マーカー区間)、`adr/0007-layered-architecture-refactor.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path: `core/.golangci.yml` (新規)、`scripts/depgraph.sh` (新規)、`lefthook.yml`、`.github/workflows/ci.yml`、`context/architecture.md`

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_core_wire-acl-port.md`
- 完了後に着手可能になる後続 prompt: なし (issue #34 の最終 prompt)
- 必要な repo 状態: `feature/34` に P1_01 まで commit 済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 生成した依存図が architecture.md の依存規則と食い違う場合は停止して報告する (規則側の誤りか実装の逸脱かをユーザーが判定する)

## タスク境界

### 実装する範囲

- golangci-lint (バージョン固定) + `core/.golangci.yml` の depguard ルール (package 単位)
- `scripts/depgraph.sh` (依存図生成) と生成マーカー区間の更新・drift 検査
- lefthook pre-commit / CI への組み込み

### 実装しない範囲

- depguard 以外の linter 有効化・lint 指摘の一括修正
- package の物理移動・doc の path 変更 (フラット構成のまま変わらない。D8)
- Java 側の検査 (ArchUnit) — issue #35 (P3_02) の責務
- ADR-0002 / ADR-0003 の本文変更 (履歴文書)
- コードの機能変更

## 設計仕様

spec #32 D5 / D8 (確定) の抜粋 — depguard ルール (package 単位、`files` + `deny` + `desc` の宣言形式):

```yaml
# core/.golangci.yml (構成イメージ。キーの正確な形式は導入バージョンの公式スキーマに従う)
# 依存規則の正本は context/architecture.md の Package Boundary。M = github.com/Fukuemon/depwalk/core/internal
linters:
  enable:
    - depguard
linters-settings:
  depguard:
    rules:
      graph:
        files: ["**/internal/graph/**"]
        deny: # graph は他の internal package に依存しない
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyze",
              desc: "graph は analyze に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/protocol",
              desc: "graph は wire 表現 protocol に依存できません (ACL が変換します)",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyzer",
              desc: "graph は analyzer に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/output",
              desc: "graph は output に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/cli",
              desc: "graph は cli に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/traversal",
              desc: "graph は traversal に依存できません (依存方向は traversal → graph)",
            }
      traversal:
        files: ["**/internal/traversal/**"]
        deny: # traversal → graph のみ許可
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyze",
              desc: "traversal は analyze に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/protocol",
              desc: "traversal は protocol に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyzer",
              desc: "traversal は analyzer に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/output",
              desc: "traversal は output に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/cli",
              desc: "traversal は cli に依存できません",
            }
      analyze:
        files: ["**/internal/analyze/**"]
        deny: # analyze → graph / traversal のみ許可 (抽象は analyze 側の port で表現する)
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/protocol",
              desc: "analyze は protocol に依存できません (port を protocol 側が実装します)",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyzer",
              desc: "analyze は analyzer に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/output",
              desc: "analyze は output に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/cli",
              desc: "analyze は cli に依存できません",
            }
      output:
        files: ["**/internal/output/**"]
        deny: # output → graph / traversal のみ許可
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/protocol",
              desc: "output は protocol に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyze",
              desc: "output は analyze に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/analyzer",
              desc: "output は analyzer に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/cli",
              desc: "output は cli に依存できません",
            }
      protocol:
        files: ["**/internal/protocol/**"]
        deny: # protocol → analyze (port 実装) / analyzer / graph のみ許可
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/output",
              desc: "protocol は output に依存できません",
            }
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal/cli",
              desc: "protocol は cli に依存できません",
            }
      analyzer:
        files: ["**/internal/analyzer/**"]
        deny: # analyzer は他の internal package に依存しない (呼ばれる側)
          - {
              pkg: "github.com/Fukuemon/depwalk/core/internal",
              desc: "analyzer は他の internal package に依存できません",
            }
```

- `cli` はコンポジションルートとしてルール対象外 (全 package を import してよい)
- 依存規則の正本は `context/architecture.md` の Package Boundary。ルールと正本が食い違う場合は停止

## テスト観点

- IF 禁止 import が追加された場合、THEN lint が検出し CI / pre-commit を FAIL させる (受け入れ基準 3)。意図的違反での FAIL 確認を必ず実施する
- IF 実 import が変わったのに依存図を再生成していない場合、THEN drift 検査が CI / pre-commit を FAIL させる
- 既存コードは P1_01 完了時点で違反ゼロのはずであり、lint 導入によって既存テスト・既存コードの変更が発生しないこと
- architecture.md の境界記述と実 import の一致 (受け入れ基準 5)

## 検証コマンド

- `cd core && golangci-lint run ./...` (導入した固定バージョンで)
- `bash scripts/depgraph.sh && git diff --exit-code context/architecture.md` (依存図の冪等性と drift ゼロ)
- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`
- `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)
- `lefthook run pre-commit`

## 完了条件

- [ ] `core/.golangci.yml` に depguard ルール (package 単位) が定義され、バージョン固定で導入されている
- [ ] 意図的な違反 import で lint が FAIL することを確認した (確認用ファイルは削除済み)
- [ ] `scripts/depgraph.sh` が冪等に依存図を生成し、architecture.md の生成マーカー区間が実 import と一致している
- [ ] depguard と depgraph drift 検査が lefthook pre-commit と CI の両方に組み込まれている
- [ ] spec #32 の変更点テーブルに反映済み注記を追記した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] PR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
