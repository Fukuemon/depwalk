# spec review 記録 (#9 Java Analyzer)

`spec-review` (fresh-context evaluator = `spec-reviewer` subagent) の結果を追記する。

## Review 2026-07-11 (phase: scaffold)

Verdict: **NEEDS_WORK**

### 観点別評価

| 観点               | 結果       | 要点                                                                                                                    |
| ------------------ | ---------- | ----------------------------------------------------------------------------------------------------------------------- |
| 上位文書整合       | PASS       | DesignDoc (モジュール責務 / P2・P3 / Future Work) / analyzer-protocol feature doc / context / ADR-0001・0002 と矛盾なし |
| 未解決論点         | PASS       | D1-D10 の決定欄がすべて `未決` と明示。Q2 は決定者・期限付きで管理され上流 DesignDoc Q2 と一致。下流 phase の先行なし   |
| 実装対象明示       | NEEDS_WORK | target 名は `context/project.md` の対象ドメインと一対一だが、`core` の責務境界が spec 内で自己矛盾 (下記指摘)           |
| template 必須節    | PASS       | `hooks/spec/validate_document.sh` の必須 23 節をすべて充足。メタ情報・変更履歴も同期                                    |
| EARS acceptance    | PASS       | WHEN 2 / IF 3 / THE SYSTEM SHALL 2。protocol 正本と照合可能で曖昧動詞なし                                               |
| prompts 自己完結性 | N/A        | prompts 未生成 (phase 10 未着手)                                                                                        |
| 正本境界           | N/A        | sync 未実行。spec が作業正本でよい段階                                                                                  |

### 指摘 (blocking)

**`index.md` — `core` の責務境界が spec 内で矛盾している**

- スコープ「やること」に「Core からの起動方法の確定」を置き、論点 D2 に「Core が Analyzer をどう発見・起動するか」(候補 A: CLI flag / B: 環境変数 / D: Core に同梱) を置く一方、実装対象テーブルでは `core` を `-` とし「S5 のため差分を出さない」と宣言している。
- 実コードでは Core 側の起動コマンド解決は未実装 (`core/internal/analyzer/runner.go` は `Command{Path, Args}` を呼び出し側から受け取るのみ、`core/internal/cli/root.go` に analyze command / flag なし)。D2 を A/B/D のいずれで決めても Core (CLI) 側に差分が出るため、「差分を出さない」と両立しない。
- `design/DesignDoc.md` の S5 は「**新しい言語の Analyzer を追加するとき** Core を変更せずに済む」であり、初号機 (Java) 導入時の Core 側配線を免責する条項ではない。spec の S5 解釈は上位定義より強い。
- 対応案: ① 実装対象テーブルの `core` 行に「D2 の決定に伴う起動コマンド解決の**実装**は本 spec のスコープ外 (後続 CLI interface spec が担当)、本 spec は決定のみ」と責務境界を明示する / ② `core` を `◯` に変え、Java Analyzer 起動配線を実装対象に含める。あわせて S5 の表現を DesignDoc 定義に合わせる。

### 指摘 (advisory / non-blocking)

- D6 の決定候補 B「lambda も含める」は protocol の `symbolKind` enum (`method` / `constructor` / `function` / `initializer`) に lambda を持たないため、選ぶと契約変更 (major bump 判断) が必要。clarify 時に protocol 影響を明記する。
- 「ADR 起票要否: 判断保留」は template の `要 / 不要` から外れる値。clarify で確定させる。
- `context/project.md` の Quick Commands は Go のみで Java 側 build / test コマンドを持たない。D1 / D10 の決定は `context/project.md` と `context/engineering.md` (「Analyzer build を束ねる必要が出た時点で make-like wrapper を検討」) に波及するため、「context への影響」表で拾う。

### 対応 (完了)

- blocking: ユーザー判断により「Core 側の初回配線 (`depwalk analyze` command + Analyzer 起動コマンド解決) を本 spec の実装対象に含める」と決定。実装対象テーブルの `core` を ◯ (初回配線のみ / Java 固有の分岐は入れない) に変更し、スコープ「やること」に追加。S5 の表現を DesignDoc 定義 (2 つ目以降の Analyzer 追加時に Core 無変更) に合わせた。
- advisory: D6 に protocol の `symbolKind` enum 制約を注記。ADR 起票要否に判断条件を明示。「context への影響」表を記入。

## Review 2026-07-11 (phase: scaffold, 2 回目)

Verdict: **NEEDS_WORK**

前回 blocking (`core` の責務境界の矛盾) は解消を確認。新たな blocking が 1 件。

### 指摘 (blocking)

**S5 の再定義が「継承」扱いのまま、Design Doc へ back-propagation 登録されていない**

- 上位定義 (`design/DesignDoc.md` の S5 測定方法「Analyzer 追加で Core モジュールに差分が発生しないこと」/ P4) には「初号機は対象外」という留保がない。一方 spec は「Java 導入に伴う初回配線は S5 の対象外」と宣言し、Core への差分を実装対象に含めた。spec の成功条件が上位 S5 の測定方法を上書きしている。
- にもかかわらず上位文書整合表の Design Doc 行は `継承` のままで、「Design Doc への影響」表にも S5 の明確化行がない。このままでは Design Doc 側に旧定義が残り drift する。

### 対応 (完了)

- 上位文書整合表の Design Doc 行を分割し、「成功条件 S5 / 設計原則 P4」行を `変更提案` として登録。
- 「Design Doc 更新要否」を S5 / P4 の明確化を含む形に更新。
- 「Design Doc への影響」表に S5 / P4 の測定方法明確化の行を追加 (phase: sync で反映予定)。
- advisory: 上位文書整合表に `context: engineering` 行を追加。「context への影響」表の Quick Commands 行に `depwalk analyze` の波及を追記。

## Review 2026-07-11 (phase: scaffold, 3 回目)

Verdict: **NEEDS_WORK** (指摘はすべてメタ情報 / review 記録の追随。設計内容の変更は不要)

### 観点別評価

- 上位文書整合 / 未解決論点 / 実装対象明示 / EARS acceptance: **PASS** (S5 の back-propagation 登録を確認)
- prompts 自己完結性 / 正本境界: N/A (prompts 未生成 / sync 未実行)
- template 必須節: NEEDS_WORK (`Spec Workflow Contract`「文書メタ情報の同期」未達)

### 指摘 (blocking / 機械的修正)

1. 設計フェーズ状況の phase 3 備考が「矛盾なし」のまま。実際は S5 / P4 の齟齬を検出し変更提案として登録した状態。
2. 2 回目レビューの記録が `review.md` と index のレビュー表に未追記。phase 2 の状態が `レビュー済` だが直近 verdict は NEEDS_WORK で実態と合わない。

### 指摘 (advisory)

- 「Design Doc への影響」表の feature 設計行「(状態: 完了)」が既に反映済とも読める。phase: sync で反映する旨に書き分ける。
- S5 は `context/architecture.md` / `context/testing.md` にも再掲されている。明確化するなら phase: sync で追随確認対象に含める。

### 対応 (完了)

- phase 3 の備考を「S5 / P4 の齟齬を検出 → 変更提案として登録 (phase: sync で反映)」に更新。
- phase 2 の状態を `進行中` に戻し、PASS 到達時に `レビュー済` へ更新する運用に修正。
- `review.md` に 2 回目 / 3 回目の記録を追記し、1 回目の「対応」placeholder を埋めた。index のレビュー表にも各回の行を追加。
- advisory: feature 設計行を「phase: sync で反映」と書き分け。「context への影響」表に `context/architecture.md` 行 (S5 再掲の追随) を追加し、`context/testing.md` 行にも S5 再掲の追随確認を追記。

## Review 2026-07-11 (phase: scaffold, 4 回目)

Verdict: **PASS**

全観点で PASS (prompts 自己完結性 / 正本境界は sync・prompts 未実施のため N/A)。phase: scaffold の gate を通過。

### 参考 (非ブロッキング / 次 phase で解消)

- ADR 起票要否 (「phase: clarify で `要 / 不要` に確定」) と ADR 影響表の `(phase: clarify で判断)` は、clarify 完了時に確定値へ更新する。
- 本ファイル 1 回目の記録にある「必須 23 節」は数え違い (`hooks/spec/validate_document.sh` の必須節は 22)。現行 spec の充足性には影響しない。

## Review 2026-07-11 (phase: clarify)

Verdict: **NEEDS_WORK**

### 指摘 (blocking)

**scope 外に宣言されたメソッドへの呼び出しの扱いが D3 / D4 / D7 の間で未決**

- D4 は `fullGraph` を「scope 内の全 `methodSymbol` と**その間の**全 `callEdge`」と定義する (node は scope 内に限られる)。
- D7 は「Spring では呼び出しの大半が interface 越しであり、辺を落とすと S1 / S2 が実用にならない」として宣言型への `callEdge` を必須とする。
- しかし `userRepository.findById(id)` の宣言型メソッドは継承元の `JpaRepository#findById` (jar 由来 = scope 外) であり、D4 の定義では node にならず edge も出ない。D3 で classpath を必須化したコストが出力に現れない。
- protocol は「valid な `callEdge` は解決済み `methodSymbol` を参照する」と定めるため、node を出さずに edge だけ出す実装は契約違反になる。

### 指摘 (advisory)

1. classpath 必須性の粒度 (key 不在 vs 値としての空配列) が D10 の運用細則にしかなく、D3 / D8 単体では読めない。
2. metadata の classpath key 名と Core の passthrough flag 名が未定。Analyzer 側の必須性検査は key 名の合意なしに実装できない。
3. D5 の表に constructor のメソッド名 token (`<init>` か単純クラス名か) が無い。D10 で unit test の検証範囲に置いているため確定が要る。
4. 性能の数値目標が「実測 baseline 後に確定」として保留されているが、`## 未確定事項` に決定者 / 期限付きで登録されていない。

### 対応 (完了)

- **D11 を追加起票し決定**: node 化の判定基準を「宣言型」ではなく「**レシーバの静的型**」とする。レシーバ静的型が scope 内なら、継承した library メソッドでもその型のメソッドとして node 化し edge を張る (継承元は `metadata` に保持)。レシーバ静的型が scope 外 (`String` / `List` 等) なら出力しない (JDK / library ノイズの排除)。これにより D4 の node 母集合を再定義し、D7 の主張と protocol の valid edge 契約を両立させた。
- advisory 1: D3 に「key は必須 / 値としての空配列は許容」を明記し、D8 の `JAVA_MISSING_CLASSPATH` の説明も key 不在に限定した。
- advisory 2: D2 に「flag / 環境変数 / metadata key の具体名は phase 6 (Interface 設計) で確定する」と明記した。
- advisory 3: D5 の表に constructor 行 (`#<init>(...)`) を追加した。
- advisory 4: `## 未確定事項` に性能数値目標を決定者 (Fukuemon) / 期限 (Phase1 実装完了時) 付きで登録した。

## Review 2026-07-11 (phase: clarify, 2 回目)

Verdict: **NEEDS_WORK**

D11 の追加で blocking は解消したが、D11 の規則自体に穴が見つかった。

### 指摘 (blocking)

1. **scope 内クラス間の継承で node が分裂する** — D11 を「常にレシーバ静的型に帰属」としたため、`BaseService#save` (scope 内) を `UserService` が継承している場合、`userService.save()` の callee が `UserService#save` という別 `methodId` の node になる。`BaseService#save` を起点にした caller 探索が取りこぼし、S1 の網羅性が壊れる。`this.foo()` / `super.foo()` の帰属先も未規定。
2. **D5 の `signature` が「宣言型」のままで D11 と矛盾** — 同一 node の `signature` / `methodId` が二通り生成されうる。
3. **EARS (`WHERE 呼び出し先が interface...`) が D11 反映前 (宣言型基準) のまま** — 受け入れ基準が決定と食い違い、テスト化すると誤った契約を検証する。

### 指摘 (advisory)

- scope 内サブクラス経由で scope 外の static を参照する形 (`Sub.staticFromLibBase()`) の扱いが読めない。
- 性能数値目標の記録先が「phase 8 と feature doc」の二箇所で、正本が曖昧。

### 対応 (完了)

- **D11 の帰属規則を修正**: 「**宣言型を優先し、宣言が scope 外のときだけレシーバの静的型へ引き上げる**」に変更。scope 内継承では宣言型に一本化されるため node 分裂が起きない。`this` / `super` / static も同一規則で決まる (static のレシーバは参照した型とみなす)。override 解決は Phase3 の担当という制約も明記した。
- D5 の `signature` 定義を「帰属型 (D11 の規則で決まる型)」に改め、D11 への参照を張った。
- EARS を帰属型基準に更新し、「宣言もレシーバ静的型も scope 外なら出力しない (diagnostic も出さない)」を 1 条追加した。
- 性能数値目標の正本を「phase: sync 後の feature doc に一本化、spec 側は決定時スナップショット」と明記した。

## Review 2026-07-11 (phase: clarify, 3 回目)

Verdict: **NEEDS_WORK**

### 指摘 (blocking)

1. **override 時の帰属型が一意に定まらない** — 「宣言型優先」の「宣言型」が、根の宣言サイト (基底) か、レシーバ静的型から見える最も具象な宣言かで two-way に読める。前者だと override 実装 (`UserService#save`) が dead node になり影響調査ができず、後者だと基底起点の caller 探索が取りこぼす。
2. **引き上げが JDK メソッドを巻き込む** — 「宣言が scope 外ならレシーバ型へ引き上げ」を字義どおり適用すると `userService.toString()` (`java.lang.Object#toString`) が `UserService#toString()` として node 化され、`equals` / `hashCode` が scope 内の全型ぶん生成される。D11 自身のノイズ排除根拠と衝突する。あわせて `fullGraph` の node 母集合の列挙方法 (宣言列挙か call site 発見か) が未定義。

### 指摘 (advisory)

- D7 の見出し文と Error ケース表 row2 が「宣言型」のままで D11 と字面が食い違う。
- D10 の Java unit test 検証範囲に D11 (帰属型規則) が入っていない。
- `new Foo()` (constructor) はレシーバを持たないため規則の当てはめが読めない。
- `## 未確定事項` の注記「1 件でも残っていれば下流 phase は止める」と、登録済み 2 件の「止める対象に含めない」が字面上ぶつかる。

### 対応 (完了)

- **B1**: 帰属型の「宣言型」を **SymbolSolver が解決した実際の宣言サイト** (メソッド本体が書かれている型) と定義。override していれば override 先、していなければ継承元。これにより「合成 node による分裂」も「override の dead node」も起きない。基底型変数経由の呼び出しが override 実装に届かない点は Phase1 の既知の制約 (Phase3 = SootUp の担当) として宣言。
- **B2**: 引き上げ除外 package を導入。既定で `java.*` / `javax.*` / `jakarta.*` を除外し、`analysisRequest.metadata` で上書き可能とした (key 名は phase 6 で確定)。あわせて `fullGraph` の node 母集合を「① scope 内宣言の全列挙 (呼ばれていないメソッドも含む) ② 引き上げ node は call site 由来のみ」と定義した。
- advisory: D7 の見出し文 / Error 表 row2 を帰属型基準に統一。D10 の Java unit test 検証範囲に D11 を追加。`new Foo()` の扱い (constructor は継承されないため引き上げ無し) を D11 に明記。未確定事項の注記に「決定者 / 期限付きで Phase1 に影響しない項目は除く」を追加。

## Review 2026-07-11 (phase: clarify, 4 回目)

Verdict: **NEEDS_WORK** (決定内容の欠陥ではなく、EARS の同期漏れ)

### 指摘 (blocking)

**EARS / D5 が D11 の第 3 分岐 (引き上げ除外 package) に追随していない**

- EARS の WHERE 条は「帰属型 (D11: 宣言が scope 内なら宣言型、scope 外ならレシーバの静的型)」という 2 分岐要約のままで、除外 package の分岐が落ちている。`java.*` は interface / 抽象メソッドを多数含む (`Iterable#iterator` / `Comparable#compareTo` / `Runnable#run`) ため空振りしない。`myCollection.iterator()` に対し EARS は「引き上げて edge を出す」、D11 表は「出力しない」と**逆の結論**を出す。
- 「出力しない」ケースの EARS が row4 (宣言もレシーバも scope 外) のみで、row3 (除外 package) に対応する受け入れ基準が無い。D10 の unit test 検証範囲には除外 package が入っており、テスト対象と受け入れ基準の間に穴が残る。
- D5 の `signature` 行の括弧書きにも同じ古い 2 分岐要約が残っている。

### 指摘 (advisory)

- D4 の表が node 母集合の列挙方法 (D11 に記載) を相互参照していない。
- 除外判定の「prefix 一致」の粒度が未定義 (`java` が `javafx` を巻き込むか)。
- D10 の検証範囲に「除外 package の metadata による上書き」が挙がっていない。

### 対応 (完了)

- EARS の WHERE 条を「D11 の規則で決まる帰属型」への参照に改め、除外 package の IF 条を 1 条追加した (例: `userService.toString()` / `myCollection.iterator()`)。「宣言サイトもレシーバも scope 外」の条も宣言サイト基準の表現に統一した。
- D5 の `signature` 行の括弧書きを「帰属型の決定規則は D11 を正本とする」に統一した。
- advisory: D4 に「node 母集合の列挙方法は D11 を正本とする」の相互参照を追加。除外判定を「`.` 区切り segment 単位の prefix 一致」と定義 (`java` は `javafx` に一致しない)。D10 の検証範囲に除外 package の上書きを追加。

## Review 2026-07-11 (phase: clarify, 5 回目)

Verdict: **PASS**

全観点で PASS (prompts 自己完結性 / 正本境界は未実施のため N/A)。phase: clarify の gate を通過。D1-D11 の決定が protocol 契約 (feature doc) を変更せずに成立していること、EARS / D4 / D5 / D6 / D7 / D8 / D10 / エラー表が D11 を正本として一貫していること、未決ゼロ (保留 2 件はいずれも決定者・期限付きで Phase1 に影響しない) が確認された。

### advisory への対応 (完了)

- 宣言サイトの定義を「本体が書かれている型」→「**SymbolSolver が override 解決まで済ませた後に返す、そのメソッド宣言の所在型** (本体の有無を問わない = interface / 抽象メソッドの宣言を含む)」に改めた。abstract / interface メソッドに字義どおり適用できない誤読余地を除いた。
- EARS の `myCollection.iterator()` の例に「レシーバ静的型が scope 内の場合」という前提を明記した (静的型が `java.util.List` なら row4 に該当するため)。

### 残る advisory (次 phase で扱う)

- EARS の WHEN / WHERE 条は「出力する」を述べ、除外は後続の IF 条が担う構造。テスト設計 (phase 9) で「IF 条が例外として優先される」ことを明示する。
