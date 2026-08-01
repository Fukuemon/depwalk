# ADR-0008: 文書の鮮度を git 証拠で担保し、読み取りマップを生成物にする

## 状態

提案

## 決定日

2026-07-29 (起案)

> 状態が「提案」の間は起案日を記す。承認時に決定日へ置き換え、以後の変更は ADR-0007 に倣い「改訂」として追記する。

## 背景

本プロジェクトの文書は「docs が正本、コードが導出物」という SDD の前提で運用しており、この方向は維持する。一方で **正本であること自体は、正本が実装と一致していることを保証しない**。実測 (2026-07-28) で次の 2 つの構造的な穴が確認された。

### 穴 1: 鮮度が人の手書きに依存している

`context/README.md` の Freshness 契約は「各ファイル先頭に `> 最終更新: YYYY-MM-DD` を置き、内容変更時に更新する」と定めている。この日付は人が手で書くため、**更新を忘れても・実態と食い違っても、機械的には検出できない**。実際 14 ファイル (`design/` 7 本 + `context/` 7 本) がこのヘッダを持つが、日付の正しさを担保する仕組みは存在しない。

加えてヘッダ行は `> 最終更新: 2026-07-26 / Status: 完了 (300 字を超える改訂注記...)` のような散文へ肥大しており、機械可読でも人間可読でもなくなっている。

### 穴 2: 手書きの読み取り索引が育たない

`context/impact-index.yaml` は「巨大な文書を全文読みせず、小さい索引から必要なファイルだけを読む」ことを狙って置かれたが、**テンプレートのプレースホルダのまま一度も埋められていない**。原因は索引が手書きであること、すなわち「文書を書く作業とは別に、索引を更新する作業が必要」な構造そのものにある。

同種の索引は `README.md` / `context/README.md` / `adr/README.md` / `design/features/README.md` の 4 箇所に分散しており、いずれも手書きで内容が実態から乖離している (詳細は [issue #40](https://github.com/Fukuemon/depwalk/issues/40))。

### 既に成功しているパターン

一方で本リポジトリには、同じ「文書が腐る」問題を解いた実例が 1 つある。Core の package 依存図は `scripts/depgraph.sh` が `go list` の実 import から生成し、`context/architecture.md` の生成マーカー区間を冪等に置換する。再生成して diff が出れば drift とみなし、lefthook pre-commit と CI が FAIL させる (判断の正本は [ADR-0007](0007-layered-architecture-refactor.md))。

本 ADR は、**depgraph で実証済みのパターンを文書運用全体へ一般化する**ものである。外部ツール (OpenWiki 等) の調査も行ったが、それらは「コードが正本・docs が導出物」を前提としており本プロジェクトとは向きが逆のため、ツール自体は導入せず設計思想のみを取り込む。

## 要求

### 成功条件

- 文書の鮮度が、人の記憶や手書きの日付ではなく **git の差分**から機械的に判定できる
- 読み取りマップが手書き作業を伴わずに更新され、実態から乖離しない
- 実装と乖離した文書が PR レビュー時点で可視化される
- 検査が形骸化しない (内容を読まずに検査を通す抜け道が、実質的な唯一の運用手段にならない)

### 業務ルール

| #   | ルール                                                                       | 理由                                                                                       |
| --- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| 1   | 機械的に一意導出できる事実は生成物とし、drift を FAIL 扱いにする             | depgraph で実証済み。手書きより常に正しい                                                  |
| 2   | 機械的に導出できない事実 (本文の妥当性) は FAIL させず、stale 通知に留める   | 判定が不確実なものを FAIL にすると「日付だけ進めて通す」運用へ堕ち、gate が形骸化する      |
| 3   | 文書の鮮度は手書きの日付ではなく、最後に実装と突き合わせた commit で表現する | 日付は嘘をつけるが、commit と `git log` の差分は嘘をつけない                               |
| 4   | 索引は文書自身のメタ情報から生成し、索引を独立に手で編集しない               | 手書き索引が育たないことは `impact-index.yaml` が実例として示している                      |
| 5   | ADR と spec は鮮度検査の対象外とする                                         | ADR は決定時点の不変記録であり更新されないことが正しい。spec は issue close 時に削除される |

### 受け入れ基準 (EARS)

1. THE SYSTEM SHALL 鮮度検査の対象となる各文書に、その文書が語る実装範囲 (`governs`) と最後に突き合わせた commit (`verified_commit`) を機械可読な形式で保持する。
2. WHEN `verified_commit` から HEAD までの間に `governs` 配下の変更が存在するとき、THE SYSTEM SHALL その文書を stale として CI 上で一覧提示する。
3. THE SYSTEM SHALL stale の存在のみを理由に CI を FAIL させない。
4. IF 生成された読み取りマップが文書のメタ情報と乖離した場合、THEN THE SYSTEM SHALL drift として CI / pre-commit を FAIL させる。
5. THE SYSTEM SHALL 手書きの `> 最終更新: YYYY-MM-DD` ヘッダを持たない。
6. WHEN 文書がまだ実装との突き合わせを経ていないとき、THE SYSTEM SHALL その状態を `verified_commit: unverified` として表現し、常に stale として一覧提示する。

### スコープ外

文書本文の内容そのものの推敲・再編成 ([issue #40](https://github.com/Fukuemon/depwalk/issues/40) が扱う)、外部ドキュメント生成ツールの導入、`specs/` / `adr/` の鮮度管理、コード側の挙動変更。

## 決定

### 1. 文書メタ情報を YAML frontmatter で持つ

対象文書の先頭に frontmatter を置き、`> 最終更新: YYYY-MM-DD` ヘッダを廃止する。日付は `verified_commit` から `git log` で導出できるため、手書きの日付欄は不要になる。

```yaml
---
type: feature-design # design-doc | feature-design | context | prd
title: Graph Engine
description: 呼び出しグラフの node / edge が持つ属性と wire → 値型の変換契約 # 索引の 1 行説明
status: 完了 # design 系のみ
keywords: [graph, Symbol, SourceLocation]
governs:
  - core/internal/graph
verified_commit: <sha> | unverified
---
```

`description` は索引の生成に使う 1 行説明である (2026-08-01 追加)。決定 4 の索引は `- [file](link) — description` の形で出力するため、説明の出所が frontmatter 側に必要になる。`title` で代替すると索引が文書名の羅列になり、「何を読めば足りるか」の判断に使えない。OKF v0.1 の予約フィールドでもあるため名前をそれに合わせる。

`type` の値域に `index` を置かない。索引の実体 (`context/reading-map.yaml`) は本 frontmatter を入力とする生成物であり、自身が frontmatter を持つ対象ではないためである。

一方 `context/README.md` のように**生成区間を含む人手の文書**は、索引ではなくその文書が属する層の型で表す (`context/README.md` なら `type: context`)。生成されるのはファイル全体ではなくマーカー区間だけであり、文書そのものは人が書くものだからである。

`prd` は `templates/prd/template.md` のための値である (2026-08-01 追加)。本プロジェクトは統合モード (Why/What を Design Doc に内包) のため PRD の実体を持たないが、テンプレートは配布物として残るため型を用意する。PRD を作る場合の置き場は repo 直下であり、鮮度検査の対象 (`design/` / `context/`) には含まれない。

肥大した改訂注記は frontmatter へ持ち込まず、本文の履歴節へ移す。

#### 先行適用 (2026-08-01) で確認したこと

全 14 本へ展開する前に、性質の異なる 3 本 (`design/features/graph/`、`context/toolchain.md`、`context/README.md`) へ先行適用して schema を検証した。

- **frontmatter 自体は prettier で変化しない**。3 本とも prettier 3.6.2 適用後も frontmatter は 1 文字も変わらなかった。したがって [issue #45](https://github.com/Fukuemon/depwalk/issues/45) の生成器は frontmatter をそのままパースしてよい。**ただしこれは「人手の文書を `.prettierignore` へ入れなくてよい」ことを意味しない** — 下記「生成区間を含む文書の整形」を参照
- **`governs` を持たない文書が実在する** (`context/README.md`)。パーサは `governs` と `verified_commit` の**両方が欠けている場合だけ**鮮度検査の対象外として扱う。片方だけ欠けている状態は設定ミス (キー名の typo / 移行時の書き漏れ) として **error にする**。片欠けを黙って対象外にすると、governed な文書が stale 一覧から静かに消え、未確認の棚卸しという目的が崩れる
- **`governs` は単一 package とは限らない**。`context/toolchain.md` は 6 パスを持ち、そこには `analyzers/java/model-provider/build.gradle.kts` や `gradle-wrapper.properties` のように「本文が正本として記録している値の出所」も含める。**その文書が語っている契約の実装場所をすべて挙げる**のが基準であり、文書が置かれた package だけではない (`design/features/graph/` が変換契約と公開の原子性も定義するため `protocol` / `analyze` を含むのが例)
- パーサは `governs` のパスが実在するかを検査しない。ビルドスクリプトの記法差 (`.gradle` / `.gradle.kts`) など project 側の事情を吸収するため。ただし文書の著者は実在するパスを書く

#### 生成区間を含む文書の整形

`context/README.md` のように**人手の本文と生成マーカー区間が同居する文書**は、frontmatter が安定しているだけでは足りない。決定 4 のとおり README のファイル一覧は生成対象であり、生成物は markdown テーブルなので prettier が列幅を揃え直す (上記の実測)。frontmatter が無傷でも、テーブル部分で ping-pong が起きて drift 検査が壊れる。

**決定 (2026-08-01)**: マーカー区間は**テーブルではなく箇条書きで出力する**。prettier 3.6.2 が正規形の箇条書きを一切変更しないことを実測で確認した。

```markdown
<!-- BEGIN GENERATED: context-index -->

- [architecture.md](architecture.md) — package / runtime boundary, 依存方向
- [toolchain.md](toolchain.md) — toolchain 一覧, build 構成

<!-- END GENERATED: context-index -->
```

これは `scripts/depgraph.sh` が成功した理由そのもの、すなわち **formatter が触らない形式を選ぶ**という発想である。formatter と整形規則を一致させにいく (生成器を prettier に依存させる) より、そもそも衝突しない形式を選ぶほうが壊れにくい。失うのは表形式の読みやすさだが、索引としては箇条書きで足りる。

検討した他案と却下理由:

| 案                                       | 却下理由                                                                                                |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 生成器が出力を prettier に通してから書く | 冪等性は確認できたが、生成器が npx / prettier のバージョンに依存する (hook 側の pin とずれると再発する) |
| 生成区間を含む README を除外する         | その README の人手の本文まで prettier の対象外になる。対象 README は今後増える                          |

なお `context/reading-map.yaml` は**ファイル全体が生成物**なので本節の対象外であり、他の生成物と同じく `.prettierignore` へ入れる。判断が要るのは人手の本文と同居するマーカー区間だけである。

#### `verified_commit` の初期値

移行時に全文書へ HEAD を入れてはならない。それは「全文書が現在の実装と一致している」という宣言になるが、**文書と実態が乖離していること自体が [issue #40](https://github.com/Fukuemon/depwalk/issues/40) の起票理由**であり、事実に反する。

そこで `unverified` を正規の値として許容し、次の規則で初期値を決める。

- #40 で本文を実際に読み直し、実装と突き合わせた文書 → その作業の commit
- 読み直していない文書 → `unverified`

`unverified` の文書は初回から stale として点灯する。これにより移行直後の stale 一覧が「**まだ実装と突き合わせていない文書のリスト**」として正しい意味を持ち、消し込みの作業リストとしてそのまま使える。虚偽の初期値を入れて一覧を空にするより、未検証であることを明示するほうが運用上の価値が高い。

語彙は Google の Open Knowledge Format (OKF) v0.1 から借用するが、**準拠は宣言しない**。OKF は「必須フィールドは `type` のみ、他は producer の自由」という最小主義を取るため `governs` / `verified_commit` を独自拡張として載せられ、将来必要になれば準拠へ移行できる。一方で「index ファイルには frontmatter を置けない」等の制約を今受け入れる利益はない。

### 2. `governs` はコードパスに限らず設定ファイルも指す

コードに直接紐づかない context 文書は、その文書が語る対象の設定ファイルを監視対象にする。これにより実質すべての対象文書が `governs` を持てる。対象は `> 最終更新` ヘッダを持つ 14 本 (`design/` 7 本 + `context/` 7 本) とし、割り当ては次のとおり。

| 文書                                 | governs                                                                                                                                                                                  |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `design/DesignDoc.md`                | `design/features/` (feature の増減で landscape を見直す)                                                                                                                                 |
| `design/features/graph/`             | `core/internal/graph`, `core/internal/protocol`, `core/internal/analyze` (変換契約と公開の原子性も本 doc が定義するため)                                                                 |
| `design/features/traversal/`         | `core/internal/traversal`                                                                                                                                                                |
| `design/features/output/`            | `core/internal/output`                                                                                                                                                                   |
| `design/features/cli/`               | `core/internal/cli`, `core/cmd/depwalk`                                                                                                                                                  |
| `design/features/analyzer-protocol/` | `core/internal/protocol`, `core/internal/analyze`, `core/internal/analyzer`, `testdata/`                                                                                                 |
| `design/features/java-analyzer/`     | `analyzers/java/`                                                                                                                                                                        |
| `context/architecture.md`            | `core/internal/`, `analyzers/java/src/main/`, `core/.golangci.yml`                                                                                                                       |
| `context/toolchain.md`               | `mise.toml`, `core/go.mod`, `analyzers/java` の build 定義 (`build.gradle.kts` / `settings.gradle.kts` / `model-provider/build.gradle.kts` / `gradle/wrapper/gradle-wrapper.properties`) |
| `context/engineering.md`             | `lefthook.yml`, `.github/workflows/`, `scripts/`, `hooks/`                                                                                                                               |
| `context/testing.md`                 | `core/e2e/`, `testdata/`, `analyzers/java/src/test/`                                                                                                                                     |
| `context/infrastructure.md`          | `.github/workflows/`, `analyzers/java/build.gradle*`                                                                                                                                     |
| `context/ai-agents.md`               | `.claude/agents/` (下記の注意を参照)                                                                                                                                                     |
| `context/README.md`                  | なし (索引部分は生成物。決定 4 を参照)                                                                                                                                                   |

`context/ai-agents.md` の `governs` には制約がある。この文書が語る subagent 定義の**正本は sdd-template リポジトリ**にあり、本リポジトリの `.rulesync/` は symlink で commit しない。したがって検出できるのは「再生成された生成物 (`.claude/agents/`) が変わったこと」までであり、正本側の変更そのものは追えない。この限界を承知のうえで生成物を監視対象にする。

`context/project.yml` は YAML のため frontmatter を持てず、既存の `meta.updated` を継続する。

### 3. 検査は「生成物は FAIL、本文は通知のみ」で二分する

| 対象           | 判定方法                                          | 強度                   | 実行箇所          |
| -------------- | ------------------------------------------------- | ---------------------- | ----------------- |
| 読み取りマップ | 再生成して diff が出るか (depgraph と同一の作法)  | FAIL                   | lefthook + CI     |
| 文書本文の鮮度 | `git log <verified_commit>..HEAD -- <governs...>` | 通知のみ (exit code 0) | CI の job summary |

本文の鮮度を FAIL にしない理由は、判定が不確実だからである。`governs` 配下が変わっても文書が正しいままのケースは日常的に存在し、それを FAIL 扱いにすると「内容を読まずに `verified_commit` だけ進めて通す」ことが唯一の現実的な運用になる。それは検査が存在しないのと同じである。

鮮度検査は pre-commit には載せない。commit を跨いで初めて意味を持つ検査であり、毎 commit で `git log` を回す価値がない。

#### 前提: 生成物を formatter の対象外にする

**drift 検査を成立させるには、生成物が pre-commit の formatter に触られないことが前提になる。** これを満たさないと検査は恒久的に FAIL する。

`scripts/depgraph.sh` が成功しているのは、マーカー区間の置換だけが理由ではない。**生成物が mermaid のコードフェンスであり、prettier が触らない形式だった**ことが効いている。本 ADR の生成物は markdown のテーブルと YAML であり、この前提を引き継げない。

prettier 3.6.2 での実測 (2026-08-01):

| 生成形式               | prettier の挙動                                  | drift 検査 |
| ---------------------- | ------------------------------------------------ | ---------- |
| mermaid コードフェンス | 触らない                                         | 成立する   |
| markdown テーブル      | 列幅を揃え直す                                   | **壊れる** |
| YAML ファイル          | 引用符の統一・配列の折り返し・空白の正規化を行う | **壊れる** |
| md 内の yaml フェンス  | 同上 (フェンス内でも整形する)                    | **壊れる** |

生成 → formatter が整形 → 再生成で元に戻る、の ping-pong になり、`git diff --exit-code` が常に非ゼロになる。同じ現象は rulesync 生成物で既に発生しており ([issue #46](https://github.com/Fukuemon/depwalk/issues/46))、14 ファイルが該当する。

したがって **生成物を `.prettierignore` へ登録し、整形の責務を生成器に一任する**。この対応は [issue #46](https://github.com/Fukuemon/depwalk/issues/46) が扱い、**[issue #45](https://github.com/Fukuemon/depwalk/issues/45) の前提条件とする**。生成器を先に書くと、必ずこの ping-pong に突き当たる。

#### pre-commit の対象範囲

drift 検査 (生成物側) の入力は**全対象文書の frontmatter**であるため、`go-depgraph-drift` のように `core/**/*.go` へ絞る glob 設計は取れない。glob は `design/**/*.md` / `context/**/*.{md,yaml}` / 生成スクリプト自身に広げ、**毎 commit で全対象文書の frontmatter をパースする**前提とする。対象は 14 ファイルで、YAML の先頭ブロックを読むだけなので lint 起動を伴う depguard と違いコストは無視できる。

#### 自己参照 stale の扱い

`context/engineering.md` の `governs` には `scripts/` が含まれるため、**本 ADR が導入する `scripts/doc-freshness.sh` を追加する commit 自体が engineering.md を stale にする**。同種の自己参照は `.github/workflows/` を監視する文書でも起きる。

これを除外規則で消さず、**そのまま stale として点灯させる**。quality gate を増やしたなら engineering.md (root task boundary / repository quality gate を扱う文書) は実際に見直すべきであり、この点灯は誤検出ではなく正しい検出である。除外規則を入れると、本当に見直しが必要な変更まで一緒に握り潰される。

### 4. 逆引き表を生成物にし、`context/reading-map.yaml` へ改名する

`governs` が文書側にあれば、「コードパス → 読むべき文書」の逆引き表は機械生成できる。これは `impact-index.yaml` が本来狙っていた「何を読めば足りるか」そのものであり、手書きの `read:` / `source_refs:` / `coverage:` は不要になる。`keywords` のみ frontmatter 側に持たせて生成に含める。

あわせて `context/impact-index.yaml` を **`context/reading-map.yaml` へ改名する**。`impact-index` という名は「変更の影響範囲 (impact analysis)」を連想させるが、再定義後の実体は読み取りのルーティング表であり意味がずれている。また「索引 / index」の語は 4 箇所の README で既に使われており、その分散自体が [issue #40](https://github.com/Fukuemon/depwalk/issues/40) の課題である。新しい名前は、このファイルの唯一の存在理由 (「何を読めば足りるか」) をそのまま表す。

同じ生成器が `context/README.md` 等の一覧テーブルも埋める。ただしこれらの README は**索引と人が書く本文が同居している**点に注意が要る。`context/README.md` は「ファイル一覧」(生成対象) と「位置づけ / Producer・Consumer 契約 / 記載しないもの」(人が書く本文) を併せ持つ。

したがって生成器はファイル全体を書き換えず、`scripts/depgraph.sh` と同じく **`<!-- BEGIN GENERATED: ... -->` / `<!-- END GENERATED: ... -->` のマーカー区間だけを置換する**。マーカー外の本文には触れない。この分離があるため `context/README.md` 自身は `governs` を持たず、鮮度検査の対象にもしない (索引部分は drift 検査が、本文は各 context 文書の検査が守る)。

## 代替案

### 却下: OpenWiki 等の文書自動生成ツールを導入する

コードから AI が wiki を生成・追従させるツール群 (OpenWiki / DeepWiki 等) を検討した。却下理由は 3 点。

1. これらは「コードが正本、docs は導出物」を前提とし、本プロジェクトの SDD (「衝突したら Design Doc が正」) と向きが逆である。導入すると正本が二重化する
2. 生成物が root の `AGENTS.md` / `CLAUDE.md` へ書き込むが、本リポジトリではこの 2 つは `.rulesync/` からの生成物で直接編集が禁止されており、正面衝突する
3. 生成される文書の品質は設計品質を担保する水準に届かない (公開されている利用報告でも「アーキテクチャの構造化は有用だが、内容は概略レベル」と評価されている)

思想 (git 証拠による鮮度担保 / progressive disclosure / 読み取りマップの生成物化) のみを取り込む。

### 却下: 鮮度検査も CI FAIL にする

強制力は最大になるが、判定が不確実な検査を FAIL にすると抜け道 (`verified_commit` だけ進める) が唯一の運用手段になり、gate が形骸化する。業務ルール 2 の通り却下する。

### 却下: `governs` を持てない文書に時間ベースの fallback (例: 90 日) を入れる

見落としは減るが、実装が安定している領域の文書に対しても定期的に警告が出る。読まれない警告を量産すると通知全体の信頼性が落ちるため却下し、代わりに設定ファイルを監視対象にする (決定 2)。

### 却下: 既存の `> 最終更新:` 行を拡張してパースする

変更量は小さいが、散文中の構造をパースする実装は脆く、既に注記が肥大している実態と噛み合わない。frontmatter は Prettier / GitHub の表示にも素直に載る。

## 影響

### 良い影響

- 文書の鮮度が人の記憶に依存しなくなる。手書きの日付という「嘘をつける欄」が消える
- 読み取りマップ (`context/reading-map.yaml`) が手書き作業なしに育つ。[issue #40](https://github.com/Fukuemon/depwalk/issues/40) で手で埋める作業が発生しなくなる
- AI エージェントが機械可読な `type` / `keywords` / `governs` から必要な文書だけを引ける (token 削減)
- 実装と乖離した文書が PR 時点で可視化され、`context-harvest` の取りこぼしが検出できる

### 悪い影響 / トレードオフ

- 対象文書 14 本と `templates/` 配下 9 本の frontmatter 移行が一度必要になる
- 移行直後は `unverified` の文書が stale 一覧を占める。これは意図した状態だが、消し込みが進まなければ一覧が常時満杯となり通知が無視されるようになる
- `verified_commit` の更新責務を運用へ組み込む必要がある
- 鮮度検査は通知のみのため強制力がない。文書の正しさは最終的に人の判断に依存する (これは意図した割り切りである)

### 影響範囲

- 対象モジュール / package: 横断 (`domain:core`)。実装コードへの変更はなく、対象は `design/` / `context/` / `templates/` / `scripts/` / `lefthook.yml` / `.github/workflows/`

## 実装・運用への反映

着手順序は次のとおり。**[#46](https://github.com/Fukuemon/depwalk/issues/46) を先頭に置く**のは掃除のためではなく、生成物と formatter の境界が決まらないと [#45](https://github.com/Fukuemon/depwalk/issues/45) の drift 検査が成立しないため (上記「前提: 生成物を formatter の対象外にする」)。

| 順  | issue      | 内容                                                                     |
| --- | ---------- | ------------------------------------------------------------------------ |
| 1   | #46        | `.prettierignore` で生成物を除外し、rulesync 生成物の drift 検査を入れる |
| 2   | #40 (一部) | frontmatter を 2〜3 本へ先行適用し、schema を検証する                    |
| 3   | #45        | 検証済みの schema を入力に生成器と鮮度検査を書く                         |
| 4   | #40 (残り) | schema を全対象文書 14 本と `templates/` 9 本へ展開する                  |

2 と 4 を分けるのは、14 本へ手で frontmatter を付けた後に schema の不備が見つかると 14 本をやり直すことになるため。

- spec 更新要否: 要。[issue #40](https://github.com/Fukuemon/depwalk/issues/40) のスコープへ frontmatter 化を取り込み、索引生成器と鮮度検査は後続 issue として分割する
  - #40 (拡張): frontmatter 規約の制定・全対象文書 14 本と `templates/` 9 本への適用。`context/impact-index.yaml` は `context/reading-map.yaml` へ改名し、生成物化を前提に手で埋めない。読み直した文書のみ `verified_commit` を入れ、残りは `unverified` とする
  - [#45](https://github.com/Fukuemon/depwalk/issues/45): 2 つのスクリプトを実装する。互いに独立しているため PR は分けてよい
    - `scripts/reading-map.sh` — frontmatter から読み取りマップと各 README の索引区間を生成し、drift を FAIL させる (lefthook + CI)
    - `scripts/doc-freshness.sh` — stale 一覧を CI の job summary に出す
- context / AI 向け設定更新要否: 要
  - `context/README.md` の Freshness 契約を frontmatter ベースへ改訂する
  - `context/reading-map.yaml` (改名後) の冒頭コメント (育て方) を生成物である旨へ改訂する
  - `context/README.md` のファイル一覧と、`impact-index` を参照している既存記述をすべて新名へ追随させる
  - **`verified_commit` を誰が進めるかは skill 側の契約である**。`spec-lifecycle` の sync phase と `context-harvest` が更新責務を負うが、これらの skill の正本は sdd-template リポジトリにあり、本リポジトリの `.rulesync/` は symlink で commit しない。skill 側の変更は `skill-feedback` 経由で正本へ書き戻す
- 実装上の注意: lefthook の `run` は `sh -c` で実行され `set -e` が効かない。生成と `git diff --exit-code` は `&&` で連結する (既存の `go-depgraph-drift` と同じ形)

## 関連ドキュメント / チケット

- [ADR-0007](0007-layered-architecture-refactor.md): 生成 + drift 検査パターンの初出 (`scripts/depgraph.sh`)
- [context/README.md](../context/README.md): 改訂対象の Freshness 契約
- [context/impact-index.yaml](../context/impact-index.yaml): 生成物化と `context/reading-map.yaml` への改名の対象
- [design/DesignDoc.md](../design/DesignDoc.md)
- issue / PR:
  - [#40](https://github.com/Fukuemon/depwalk/issues/40): 文書再編成。frontmatter 化と `reading-map.yaml` への改名を取り込む
  - [#45](https://github.com/Fukuemon/depwalk/issues/45): 読み取りマップの生成器と鮮度検査スクリプトの実装 (#46 と #40 の frontmatter 適用が前提)
  - [#46](https://github.com/Fukuemon/depwalk/issues/46): 生成物の drift 検査と `.prettierignore` の境界。#45 の前提条件
