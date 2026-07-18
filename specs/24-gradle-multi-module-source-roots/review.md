# Issue #24 spec review

## Review 2026-07-15 12:53

Verdict: PASS

### 観点別評価

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33-57` に Design Doc、両 feature doc、context、ADR ごとの継承・補足方針が記録されている。Core を言語非依存に保つ境界 (`specs/24-gradle-multi-module-source-roots/index.md:131,164-173`) は Design Doc P1〜P4 (`design/DesignDoc.md:159-166`) および package boundary (`context/architecture.md:10-17`) と整合する。任意 field による互換拡張を優先する方針 (`specs/24-gradle-multi-module-source-roots/index.md:39,45-54`) も ADR-0001 (`adr/0001-analyzer-protocol-jsonl-spi.md:26-30`) と矛盾しない。
- 未解決論点: PASS — D1〜D9 は決定欄が空欄ではなくすべて `未決` と明示され (`specs/24-gradle-multi-module-source-roots/index.md:138-148`)、決定者・期限も管理されている (`specs/24-gradle-multi-module-source-roots/index.md:154-158`)。確定図は空の scaffold に留まり (`specs/24-gradle-multi-module-source-roots/index.md:296-311`)、prompts も生成方針のみで下流設計へ進んでいない (`specs/24-gradle-multi-module-source-roots/index.md:323-328`)。
- 実装対象明示: PASS — `core`、`traversal`、`output`、`analyzer-protocol`、`java-analyzer` の全正規 target と変更有無・責務が明記されている (`specs/24-gradle-multi-module-source-roots/index.md:160-170`)。これは対象ドメイン一覧 (`context/project.md:66-74`) と一致し、Core → Analyzer は Protocol 経由のみとする境界も明示されている (`specs/24-gradle-multi-module-source-roots/index.md:172-173`)。
- template 必須節: PASS — メタ情報から機能仕様まで (`specs/24-gradle-multi-module-source-roots/index.md:6-215`)、Interface / Data / Performance / Error / Test / Flow / 実装分割 (`specs/24-gradle-multi-module-source-roots/index.md:216-329`)、上位資料変更点・レビュー・変更履歴・備考 (`specs/24-gradle-multi-module-source-roots/index.md:330-382`) が揃っている。設計フェーズ状況も11フェーズを保持する (`specs/24-gradle-multi-module-source-roots/index.md:15-31`)。
- EARS acceptance: PASS — WHEN / IF / THE SYSTEM SHALL による観測可能な振る舞いが6件あり (`specs/24-gradle-multi-module-source-roots/index.md:124-131`)、複数 root の解析範囲、module 間型解決、DI 候補、後方互換、path、Core 境界をテスト可能な結果で定義している。成功条件も graph、scope、path、互換性、fixture の観測結果として具体化されている (`specs/24-gradle-multi-module-source-roots/index.md:110-116`)。
- prompts 自己完結性: N/A — scaffold phase であり、論点解決から実装分割まで未着手 (`specs/24-gradle-multi-module-source-roots/index.md:25-30`)。prompts は未生成で、生成方針のみ記載されている (`specs/24-gradle-multi-module-source-roots/index.md:323-328`)。
- 正本境界: N/A — sync phase 未実行であり、上位資料への `反映済` 行はない。durable な追加は将来の track / sync phase で反映すると明記されている (`specs/24-gradle-multi-module-source-roots/index.md:330-363`)。

PASS

## Review 2026-07-16 12:48

Verdict: PASS

### 観点別評価

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33-57` に統合モードの Why / What、Design Doc の S1 / S2 / S4 / S5、Protocol・Java Analyzer feature doc、context、ADR-0001 / 0003 / 0005 との整合方針が明記されている。Core は共通 Protocol と言語非依存な `--source-root` の受け渡しだけを担い、Gradle Tooling API・source set・型解決を Java Analyzer に閉じる (`specs/24-gradle-multi-module-source-roots/index.md:194-203,297-310,324-326`) ため、Design Doc の P1〜P4 (`design/DesignDoc.md:159-166`) と package boundary (`context/architecture.md:8-17`) に整合する。既存契約との差分は sync 候補として分類済みで、新規 ADR-0006 の必要性も追跡されている (`specs/24-gradle-multi-module-source-roots/index.md:519-567`)。
- 未解決論点: PASS — D1〜D9 の全行が `解決済み` (`specs/24-gradle-multi-module-source-roots/index.md:150-165`) で、各決定に理由・トレードオフ・ADR 判断・決定日・決定者がある (`specs/24-gradle-multi-module-source-roots/index.md:167-291`)。未確定事項は明示的に「なし」 (`specs/24-gradle-multi-module-source-roots/index.md:293-295`)。図は diagram phase 前の空 placeholder と明記されており、未決定のまま下流設計を確定した状態ではない (`specs/24-gradle-multi-module-source-roots/index.md:485-500`)。
- 実装対象明示: PASS — `core / traversal / output / analyzer-protocol / java-analyzer` の全 target が `context/project.md:66-74` の対象ドメインと一致し、変更有無と責務が明示されている (`specs/24-gradle-multi-module-source-roots/index.md:297-310`)。Core → Analyzer は Protocol のみで、Gradle 固有処理は `analyzers/java/` に閉じる (`specs/24-gradle-multi-module-source-roots/index.md:322-326,380-385`)。spec 固有の別系統な検証コマンドは提示されておらず、Quick Commands との競合もない。
- template 必須節: PASS — テンプレートの必須 Level 2 節がすべて存在する (`specs/24-gradle-multi-module-source-roots/index.md:6-597`)。`hooks/spec/validate_document.sh specs/24-gradle-multi-module-source-roots/index.md` もエラーなし。更新日、フェーズ状況、レビュー、変更履歴も本文の clarify 完了状態と同期している (`specs/24-gradle-multi-module-source-roots/index.md:6-31,569-592`)。
- EARS acceptance: PASS — `WHEN / IF / THE SYSTEM SHALL` による観測可能な基準が、正常系、validation、discovery、型解決 context、fallback、性能計測、E2E 同値性、Core 境界まで定義されている (`specs/24-gradle-multi-module-source-roots/index.md:124-148`)。対応する具体的なテスト観点と計測指標も列挙されている (`specs/24-gradle-multi-module-source-roots/index.md:431-483`)。
- prompts 自己完結性: N/A — clarify phase 完了時点のレビューで prompts は未生成。実装分割も prompts 生成方針までで、tasks phase の確定前である (`specs/24-gradle-multi-module-source-roots/index.md:502-517`)。
- 正本境界: N/A — 上位資料への durable 成果は track / sync phase で反映すると明記され、現時点の変更表は候補であり「反映済」行を持たない (`specs/24-gradle-multi-module-source-roots/index.md:519-567`)。

PASS

## Review 2026-07-17 07:35

Verdict: PASS

### 観点別評価

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33-57` で Design Doc、feature docs、context、ADR との継承・補足・更新予定を明示し、Core の言語非依存境界も `:374-381` で維持している。
- 未解決論点: PASS — `specs/24-gradle-multi-module-source-roots/index.md:165-179` の D1〜D13 はすべて解決済みで、`:364-366` も未確定事項なしと明記している。図は `:588-603` で次 phase の空プレースホルダーに留まる。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:368-381` は `context/project.md:66-74` の全 target と一致し、Core／Protocol／Java Analyzer の責務境界を明示している。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-713` に `templates/specs/template.md:7-287` および `hooks/spec/validate_document.sh:21-44` の必須節・必須サブ節がすべて存在する。更新日、設計フェーズ状況、変更履歴も `:11`、`:19-31`、`:687-708` で同期されている。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:124-158` に WHEN / IF / THE SYSTEM SHALL の観測可能な基準があり、対応するテスト観点を `:523-575` に具体化している。
- prompts 自己完結性: N/A — prompts は未生成でレビュー対象外。
- 正本境界: N/A — `specs/24-gradle-multi-module-source-roots/index.md:622-676` は track / sync 前の変更候補で、「反映済」行がないため未 sync 段階である。

PASS

## Review 2026-07-18 08:34

Verdict: NEEDS_WORK

### 観点別評価

- 上位文書整合: NEEDS_WORK — D14 は scope 内の全 call site を分類対象にする一方、Java Analyzer 正本はパース不能ファイルを file-level diagnostic だけで除外して継続する。パースできないファイル内の call site は列挙できず、両契約を同時に満たせない。
- 未解決論点: NEEDS_WORK — call-site 完全性の母集合と source/bytecode 再対応付けの到達可能性境界が未確定。
- 実装対象明示: PASS — Core、Protocol、Java Analyzer の変更有無と責務は `context/project.md` の対象ドメインおよび Core の言語非依存境界と整合する。
- template 必須節: PASS — 必須節、更新日、フェーズ状態、変更履歴は D14 clarify 再review待ちの状態と同期している。
- EARS acceptance: NEEDS_WORK — 「全 call site」「固定期待集合」を検証するための call-site identity と観測経路が不足している。
- prompts 自己完結性: N/A — prompts は未生成。
- 正本境界: N/A — track / sync 前で、durable な変更は反映候補として管理されている。

### 指摘

- High: パース不能ファイルと「全 call site 分類」の境界を決める必要がある。D14 は scope 内 source file の全 call site に primary 終端種別を要求するが、既存正本は `JAVA_PARSE_ERROR` によりファイル全体を飛ばして継続する。この場合、ファイル内 call site の総数も outcome も確定できない。分類母集合を「正常に parse できた AST から列挙した call site」に限定する、parse error を fatal にする、または file-level outcome を別指標として扱う、のいずれかを明記し、EARS・計測・テストを同期する必要がある。
  - `specs/24-gradle-multi-module-source-roots/index.md:151`
  - `specs/24-gradle-multi-module-source-roots/index.md:369-376`
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md:185-193`
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md:295-303`
- High: bytecode から source への再対応付けを、owning context と依存到達可能 context に制限する必要がある。D6 は非依存 module の型を solver に混ぜない契約だが、D14 の workspace-wide index は同一 binary name/signature だけで bytecode 宣言を source へ対応付けるよう読める。module A が外部 jar の型を参照し、非依存 module B に同名 source 型がある場合、B へ誤帰属し得る。project classes output 由来か、かつ呼出元 context から到達可能な source declaration かを照合条件として明記する必要がある。
  - `specs/24-gradle-multi-module-source-roots/index.md:250-259`
  - `specs/24-gradle-multi-module-source-roots/index.md:369-372`
  - `specs/24-gradle-multi-module-source-roots/index.md:577-581`
- Medium: silent omission の検出母集合と E2E 観測方法が不足している。outcome 解決処理自身が call site を登録する構造では、visitor が見落とした call site は総数にも入らず、誤って `silentOmission = 0` になり得る。また stderr は理由別集計だけなので、record を出さない `excluded` を含む「call site ごとの固定期待集合」は検証できない。解決前に path・range・kind 等の安定 identity で call-site inventory を作ること、および test から個別 outcome を観測する方法を定義するか、受け入れ基準を集計値照合へ限定する必要がある。
  - `specs/24-gradle-multi-module-source-roots/index.md:373-376`
  - `specs/24-gradle-multi-module-source-roots/index.md:562-563`
  - `specs/24-gradle-multi-module-source-roots/index.md:591`
  - `specs/24-gradle-multi-module-source-roots/index.md:612-615`

NEEDS_WORK

## Review 2026-07-18 10:35

Verdict: PASS

### 観点別評価

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33-57, 717-775`。Design Doc / feature doc / context / ADR との継承・補足・変更候補と sync 先が明示され、Core の言語非依存境界も維持されている。
- 未解決論点: PASS — `specs/24-gradle-multi-module-source-roots/index.md:171-194, 432-434`。D1〜D17 はすべて解決済みで、未確定事項は「なし」。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:436-449`。`context/project.md` の全 target と一致し、変更対象・非変更対象および Core / Analyzer 間の責務境界が明示されている。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-31, 33-104, 171-196, 432-451, 503-600, 683-821`。validation contract の必須節と11フェーズを備え、更新日・レビュー・変更履歴も同期されている。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:126-169`。WHEN / IF / THE SYSTEM SHALL により、入力、出力、fatal、分類完全性、性能計測が観測可能に定義されている。
- prompts 自己完結性: N/A — `specs/24-gradle-multi-module-source-roots/index.md:710-715`。現在は prompts 生成前の clarify phase。
- 正本境界: N/A — `specs/24-gradle-multi-module-source-roots/index.md:717-720`。上位資料への「反映済」行はなく、track / sync 前の作業記録段階。

PASS

## Review 2026-07-18 12:12

Verdict: NEEDS_WORK

### 観点別評価

- 上位文書整合: NEEDS_WORK — D18 の `methodSymbol.sourceLocation` は所有 source type の宣言位置を指すとするが、Protocol 正本では method の「定義位置」である。さらに D20 は未解決理由を持つ先行 diagnostic を無効化するため、ADR-0004 の観測可能性契約とも未整合。Gradle dependency resolution の外部 network・認証・cache 更新も `context/infrastructure.md` の現行契約との差分として未追跡。
- 未解決論点: PASS — D1〜D20 の決定欄はすべて `解決済み` で、`未確定事項` は「なし」 (`specs/24-gradle-multi-module-source-roots/index.md:189-210,493-495`)。各 D18〜D20 に決定理由、トレードオフ、ADR 判断、決定日、決定者がある (`:448-491`)。
- 実装対象明示: PASS — `core / traversal / output / analyzer-protocol / java-analyzer` の全正規 target と変更有無・責務を明記し、Core → Analyzer の Protocol 境界も維持している (`specs/24-gradle-multi-module-source-roots/index.md:497-510`; `context/project.md:66-74`)。
- template 必須節: PASS — 必須 Level 2 節、機能仕様の必須小節、11フェーズ、更新日、変更履歴が揃っている (`specs/24-gradle-multi-module-source-roots/index.md:6-31,512-565,567-909`; `hooks/spec/validate_document.sh:21-44,79-109`)。
- EARS acceptance: PASS — D18〜D20 を含む bytecode-only member、実 CLI E2E、call completeness fatal が観測可能な WHEN / IF / THE SYSTEM SHALL として定義され、対応する固定期待テストもある (`specs/24-gradle-multi-module-source-roots/index.md:152-180,693-709`)。
- prompts 自己完結性: N/A — prompts は未生成で、現在は clarify review gate 待ち (`specs/24-gradle-multi-module-source-roots/index.md:25-30,759-791`)。
- 正本境界: N/A — track / sync 前で「反映済」行はなく、変更点は handoff 候補として管理されている (`specs/24-gradle-multi-module-source-roots/index.md:793-858`)。

### 指摘

- High: D18 の bytecode-only member に所有 type の位置を `methodSymbol.sourceLocation` として入れるのは、Protocol の既存意味論と矛盾する。
  - `specs/24-gradle-multi-module-source-roots/index.md:448-452` — source AST に member 宣言がないにもかかわらず、所有 type の宣言位置を member の `sourceLocation` に設定している。
  - `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md:92-95,115-125` — `methodSymbol.sourceLocation` は定義位置であり、位置を持たない symbol では省略可能。
  - `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md:183-190` — field 意味論の変更は breaking change。
  - `specs/24-gradle-multi-module-source-roots/index.md:580-585,814-820` — spec は response schemaを変更しないとし、変更候補も metadata passthrough のみで、`SourceLocation` の意味変更を分類していない。
  - 対応案: bytecode-only member の `sourceLocation` は省略し、owner type の位置を明示的な anchor metadata として表すか、Protocol の意味変更として versioning と sync 対象を再設計する。

- High: D20 の全 record 破棄により、ADR-0004 が要求する動的・未解決理由の観測可能性が失われる。
  - `specs/24-gradle-multi-module-source-roots/index.md:478-489` — primary diagnostic が残ると generic な `JAVA_INCOMPLETE_ANALYSIS` を返し、先行 diagnostic をすべて無効化する。error に残す情報は件数と最初の位置・call kind までで、元の unresolved reason は契約化されていない。
  - `adr/0004-defer-runtime-call-tracing.md:27-32` — 静的に確定できない場合も候補・未解決理由を metadata / diagnostic で観測可能にし、動的機構の検出事実を残すことを決定している。
  - `specs/24-gradle-multi-module-source-roots/index.md:41-55,850-858` — ADR-0004 との整合・更新要否が追跡されていない。
  - 対応案: fatal error の metadata に理由別集計または決定的な unresolved detail を保持する契約を定めるか、ADR-0004 を更新対象として明示する。

- Medium: Tooling API discovery が新たに要求し得る network・repository 認証・Gradle user cache 更新を、infrastructure 正本との差分として分類していない。
  - `specs/24-gradle-multi-module-source-roots/index.md:351-361,611-616` — dependency download、repository 認証、Gradle user cache 書き込みを許容する。
  - `context/infrastructure.md:15-18` — 外部インフラ依存を持たず、対象 source への read-only access のみ、secret / token 不要とする現行契約。
  - `specs/24-gradle-multi-module-source-roots/index.md:842-848` — context 変更候補は architecture / toolchain / testing のみで、infrastructure がない。
  - 対応案: `context/infrastructure.md` の runtime・認証・cache 副作用を sync 対象へ追加する。

上位文書との矛盾・未分類差分があるため、clarify で上記を解消した後、`spec-lifecycle` の track / sync phase で Protocol feature doc、ADR-0004、`context/infrastructure.md` へ反映する必要がある。

NEEDS_WORK

## Review 2026-07-18 12:57

Verdict: NEEDS_WORK

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: NEEDS_WORK — `specs/24-gradle-multi-module-source-roots/index.md:524-525` は Core が Java 固有 metadata を解釈しないとしつつ、CLI renderer に `JAVA_INCOMPLETE_ANALYSIS.metadata.unresolvedCalls` の専用解釈を要求する。これは Core を言語非依存に保つ `design/DesignDoc.md:163-166`、`context/architecture.md:15-17` と矛盾する。
- 未解決論点: NEEDS_WORK — D1〜D23 は解決済み、未確定事項も「なし」だが (`specs/24-gradle-multi-module-source-roots/index.md:197-221,548-550`)、D22 の opaque passthrough と Java 固有 CLI 表示、および D23 の任意 build logic 実行と credential 非出力保証が両立する方式は未決定のままである。
- 実装対象明示: NEEDS_WORK — target 一覧自体は `context/project.md:66-74` と一致する (`specs/24-gradle-multi-module-source-roots/index.md:552-565`)。ただし Java 固有 error code / metadata を Core CLI が解釈する `:524-525` は、明記された「Core に Java 固有解決を持ち込まない」境界 `:914` を越える。
- template 必須節: PASS — 必須 Level 2 節は `specs/24-gradle-multi-module-source-roots/index.md:6-984` にすべて存在し、`hooks/spec/validate_document.sh` も成功した。更新日、フェーズ状況、レビュー、変更履歴は `:6-31,930-979` に同期されている。
- EARS acceptance: NEEDS_WORK — D18〜D22 の bytecode-only member、owner location、実 binary E2E、完全性 fatal、unresolved detail は観測可能に具体化されている (`specs/24-gradle-multi-module-source-roots/index.md:157-188,752-778`)。一方、任意の build logic を実行し得る `:150,533-541` 状態で credential が stdout / stderr に現れないという絶対保証 `:152` は、Gradle build output の隔離・抑制・redaction 契約なしには検証可能な受け入れ基準になっていない。
- prompts 自己完結性: N/A — prompts は未生成で、clarify review gate 待ちである (`specs/24-gradle-multi-module-source-roots/index.md:25-30,853-858`)。
- 正本境界: N/A — sync phase 前で「反映済」行はなく、durable な内容は変更候補として管理されている (`specs/24-gradle-multi-module-source-roots/index.md:860-928`)。

### 指摘 (NEEDS_WORK の場合のみ)

- `specs/24-gradle-multi-module-source-roots/index.md:524-525` — CLI が `JAVA_INCOMPLETE_ANALYSIS` と `unresolvedCalls` を専用解釈すると、2つ目以降の Analyzer の failure 表示追加時に Core 変更が必要になる。Protocol 共通の言語非依存 failure-detail schemaを定義する、metadataを汎用表示する、または人間向け詳細表示をAnalyzer stderrへ委譲する、のいずれかに決める必要がある。現状の `ADR-0003 更新なし` (`:925`) とも両立しない。
- `specs/24-gradle-multi-module-source-roots/index.md:150-152,533-541` — build logic は任意 code であり、credentialを自ら標準出力・標準エラーへ書ける。Tooling APIのbuild outputをどこへ接続するか、利用者へ転送しない範囲、例外messageのsanitize、悪意あるfixtureがdummy credentialを出力するnegative testを定義するか、保証を「depwalk生成logには出さない」へ限定する必要がある。
- `specs/24-gradle-multi-module-source-roots/index.md:910-917` — sync候補が不足している。D23は `context/architecture.md` のState / Runtime Boundaryにも反映対象として追加すべきである。現行契約は対象repositoryへのread-only accessだけを前提とする (`context/architecture.md:39-48`)。
- `specs/24-gradle-multi-module-source-roots/index.md:910-917` — `context/project.md` もsync対象に必要である。現行Quick Commandsは旧classpath前提の開発起動と単一fixture E2Eのまま (`context/project.md:46-58`) で、D3/D12/D19の自動discovery、明示時language metadata、required multi-module実CLI E2Eを反映していない。

NEEDS_WORK

## Review 2026-07-18 14:20

Verdict: NEEDS_WORK

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: NEEDS_WORK — `specs/24-gradle-multi-module-source-roots/index.md:454-458` の `CallSiteId` は enclosing method ID を必須とするが、既存仕様は instance initializer / field initializer を constructor に畳み込む (`design/features/java-analyzer/DesignDoc_java-analyzer.md:127-142`)。1つの AST call site が複数 constructor caller に対応する場合の inventory 展開・ID・ledger cardinality が未定義で、取得不能時は正当な既存構文まで `JAVA_INTERNAL_ERROR` になる。また `methodSymbol.metadata` の Core graph 保持 (`specs/24-gradle-multi-module-source-roots/index.md:471-471,512-522,620-620`) は、現行 Protocol 正本の「Graph Symbol は保持せず、将来 issue で別途設計」 (`design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md:111-113`) と context の graph model 境界 (`context/architecture.md:14-15`) を変更するが、Graph feature doc を変更・sync 対象として追跡していない。clarify で境界を確定後、`spec-lifecycle` の sync phase で関連正本へ反映する必要がある。
- 未解決論点: NEEDS_WORK — `specs/24-gradle-multi-module-source-roots/index.md:370-380` は provider bytecode level、bundled Tooling API 対応範囲、検証 wrapper versions を「ADR-0006 / toolchain に記録する」とするだけで具体値・決定者・期限がない一方、`specs/24-gradle-multi-module-source-roots/index.md:610-612` は未確定事項を「なし」としている。sync は決定の転記 phase であるため、D11 の互換性 matrix を clarify で確定するか、期限・決定者付き未確定事項として管理する必要がある。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:614-627` は `context/project.md:66-74` の全 target と一致し、各 target の変更有無・責務と Core → Analyzer の Protocol 境界を明示する。command 契約との差分も D27 (`specs/24-gradle-multi-module-source-roots/index.md:598-605`) で局所 smoke ではなく正本 sync 対象として説明されている。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-1057` に validator が要求する全 Level 2 節と `## 備考` が存在し、11フェーズ (`:15-31`)、スコープ両 subsection (`:88-110`)、機能仕様の必須設計 topic (`:629-684`) も揃っている。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:135-194` に WHEN / IF / THE SYSTEM SHALL の観測可能な基準があり、具体的な fixed set・exit status・record field・Tooling API bypass の検証項目へ展開されている (`:790-892`)。
- prompts 自己完結性: N/A — prompts は未生成であり、実装分割も prompts 生成前の方針段階 (`specs/24-gradle-multi-module-source-roots/index.md:911-926`)。
- 正本境界: N/A — `## 上位資料からの変更点` は clarify 由来の変更候補を記録した段階で、sync phase の「反映済」行はない (`specs/24-gradle-multi-module-source-roots/index.md:928-999`)。

### 指摘 (NEEDS_WORK の場合のみ)

- `specs/24-gradle-multi-module-source-roots/index.md:454-458` — field / instance initializer 内 call の semantic caller 展開を定義し、1 AST call site が複数 constructor に畳み込まれる場合の `CallSiteId`、inventory entry 数、ledger outcome 数を確定する。
- `specs/24-gradle-multi-module-source-roots/index.md:471-471,512-522,945-978` — `methodSymbol.metadata` を Core graph `Symbol` に保持する変更について Graph feature doc を関連資料・上位文書整合・変更先へ追加し、Protocol / Graph / context の正本変更を sync phase で一貫して反映する。
- `specs/24-gradle-multi-module-source-roots/index.md:370-380,610-612` — bundled Tooling API version、対応 Gradle wrapper 範囲、provider classfile target / daemon JVM matrix を D11 で決定する。後続決定なら未確定事項に期限・決定者を記録し、下流 phase を止める。

NEEDS_WORK

## Review 2026-07-18 15:43

Verdict: PASS

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33-62` で Design Doc、関連 feature doc、context、ADR の継承・補足・変更提案を明示し、`987-1059` で各 sync 先を追跡している。Core の言語非依存境界も `202-203, 679-680` で維持され、未追跡の矛盾はない。
- 未解決論点: PASS — `specs/24-gradle-multi-module-source-roots/index.md:210-241` の D1〜D30 はすべて「解決済み」、`663-665` の未確定事項は「なし」。下流 phase は `26-31` で未着手のままである。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:667-680` は `context/project.md:66-74` の全 target と一致し、変更対象と非変更対象、Core → Analyzer の Protocol 境界を明示している。コマンド契約との差分も `1044` で sync 対象として追跡している。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-1126` にテンプレートおよび validator の全必須節・サブ節が存在し、`hooks/spec/validate_document.sh` も終了コード 0。更新日、フェーズ状況、レビュー履歴、変更履歴は `11, 19-31, 1061-1075, 1076-1121` で同期されている。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:139-203` に WHEN / IF / THE SYSTEM SHALL の観測可能な基準があり、入力 validation、discovery、互換性、fatal、Graph、E2E の期待結果までテスト可能に定義されている。
- prompts 自己完結性: N/A — prompts は未生成で、prompts phase はレビュー対象に含まれていない。実装分割・prompts 方針も `specs/24-gradle-multi-module-source-roots/index.md:970-985` で未着手として管理されている。
- 正本境界: N/A — `specs/24-gradle-multi-module-source-roots/index.md:987-1059` に「反映済」行はなく sync phase 前である。冒頭 `3-4` と `61-62` でも durable 成果は sync phase で handoff すると明示されている。

PASS

## Review 2026-07-18 16:02

Verdict: NEEDS_WORK

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33` の突合表と `:1085` の変更候補で、既存 feature doc / context / ADR との差分と sync 先を分類済み。
- 未解決論点: PASS — `specs/24-gradle-multi-module-source-roots/index.md:205` の D1〜D30 は全件解決済み、`:663` の未確定事項は「なし」。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:667` の target は `context/project.md:66` の対象ドメインと一致し、`:679` で Core → Analyzer の Protocol 境界を維持している。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-1224` にテンプレート必須節がすべて存在し、diagram phase 更新は `:15`、`:1175` に同期済み。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:139-203` に観測可能な WHEN / IF / THE SYSTEM SHALL 条件がある。
- prompts 自己完結性: N/A — diagram phase のレビューであり prompts は対象外。
- 正本境界: N/A — `specs/24-gradle-multi-module-source-roots/index.md:1085` はすべて変更候補で、上位資料への「反映済」行はなく sync 前。
- diagram phase gate: NEEDS_WORK — 下記の図間・D13間の不整合がある。

### 指摘 (NEEDS_WORK の場合のみ)

- `specs/24-gradle-multi-module-source-roots/index.md:979-985` — Flowchart は ledger と primary diagnostic の判定後に初めて `method / edge / diagnostic` を出力する流れで、Analyzer が成功判定まで全 graph をbufferするように読める。一方、Sequence は `:1040-1045` で ledger検査前にrecordをstreamし、D13も `:417-421` で「先行record後にfatalがあり得る」「Analyzer-side streamingを維持し、bufferするのはCore」と確定している。Flowchartの成功nodeをexit 0の確定だけにするか、record streamingを完全性判定前へ移し、Core保留との境界を両図で一致させる必要がある。
- `specs/24-gradle-multi-module-source-roots/index.md:979-984`、`:1040-1056` — 両図とも resolver中のallowlist外例外 / `LinkageError` / error未出力の非ゼロexit経路がなく、D13 `:412-420` と E21/E22 `:820-821` のrequest-level fatalを可視化できていない。「D1〜D30の可視化」とするなら、resolverから`JAVA_INTERNAL_ERROR`またはprocess exit failureへ至り、Coreが先行recordを破棄する分岐を追加する必要がある。

NEEDS_WORK

## Review 2026-07-18 16:24

Verdict: NEEDS_WORK

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: NEEDS_WORK — `specs/24-gradle-multi-module-source-roots/index.md:422,1056,1074` は Core が wire record を暫定保持し、exit 0 後に Graph へ commit すると定める。一方、`design/features/graph/DesignDoc_graph.md:64,85,89` は受信時に値型へ変換・Graph 登録し、wire record を持ち回らない 1-pass 契約である。`specs/24-gradle-multi-module-source-roots/index.md:1130` の Graph 変更候補は metadata のみで、この変更を追跡していない。
- 未解決論点: PASS — `specs/24-gradle-multi-module-source-roots/index.md:205-240` の D1〜D30 はすべて解決済みで、同 `:665-667` の未確定事項は「なし」。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:669-682` は `context/project.md:66-74` の全 target と一致し、Core／Protocol／Java Analyzer の責務境界を明示している。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-1243` に template／validator の必須節が揃い、同 `:11,26,1241` で更新日・phase 状態・変更履歴も同期している。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:139-203` に観測可能な WHEN／IF／THE SYSTEM SHALL 条件があり、特に fatal と全 record 破棄は同 `:168-174` でテスト可能に定義されている。
- prompts 自己完結性: N/A — diagram phase の review であり prompts は対象外。
- 正本境界: N/A — `specs/24-gradle-multi-module-source-roots/index.md:1101-1104` は track／sync 前の変更候補で、「反映済」行はない。

### 指摘 (NEEDS_WORK の場合のみ)

- `specs/24-gradle-multi-module-source-roots/index.md:1120-1130 / 上位資料からの変更点` — D13 による Core の暫定 record 保持と exit 0 後の Graph commit は、Graph feature doc の 1-pass 即時登録契約も変更する。Graph の「record 変換／構築フロー」を sync 対象へ追加し、`spec-lifecycle` の sync phase で `design/features/graph/DesignDoc_graph.md:64,85,89` を更新する必要がある。
- 前回の diagram 指摘自体は解消済み。Analyzer-side streaming と Core 暫定保持は `specs/24-gradle-multi-module-source-roots/index.md:983-998,1055-1075`、allowlist 外 `RuntimeException`／`LinkageError` は同 `:1050-1053`、error record なし非ゼロ exit と全 record 破棄は同 `:1058-1061` に明示され、D13 (`:412-423`) と整合する。

NEEDS_WORK

## Review 2026-07-18 16:35

Verdict: NEEDS_WORK

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: PASS — Graph feature doc の既存 1-pass 契約 (`design/features/graph/DesignDoc_graph.md:64,85,89`) を維持しつつ、非公開 staging Graph と公開・破棄境界を変更提案として分類している (`specs/24-gradle-multi-module-source-roots/index.md:49,1148`)。architecture と ADR-0001 の sync 候補にも追跡済みである (`:1175,1185`)。
- 未解決論点: NEEDS_WORK — D1〜D30 は解決済み、未確定事項は「なし」だが (`specs/24-gradle-multi-module-source-roots/index.md:212-241,666-668`)、D13 内に staging 方針と矛盾する旧「終了後に構築」表現が残っている (`:419-423`)。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:670-683` は `context/project.md:66-74` の全 target と一致し、Core／Protocol／Java Analyzer の責務境界を明示している。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-1267` にテンプレート必須節が揃い、更新日、diagram phase 状態、レビュー履歴、変更履歴も `:11,15-31,1193-1262` に同期されている。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:141-203` に観測可能な WHEN／IF／THE SYSTEM SHALL 条件があり、staging への逐次変換、fatal 破棄、正常 stream の参照完全性検証もテスト可能に具体化されている (`:863,894-896`)。
- prompts 自己完結性: N/A — diagram phase のレビューであり prompts は未生成。
- 正本境界: N/A — sync phase 前で「反映済」行はなく、変更候補として Graph feature doc、context、ADR を管理している (`specs/24-gradle-multi-module-source-roots/index.md:1119-1191`)。
- diagram phase gate: NEEDS_WORK — 前2回の図そのものへの指摘は解消済み。Analyzer-side streaming と未知 resolver failure／errorなし非ゼロexitの破棄経路は `:983-1005,1052-1072`、staging Graph の1-pass登録・fatal時の参照検証なし破棄・成功時だけの公開は `:1064-1100` に明示されている。ただし下記のD13本文不整合が残る。

### 指摘 (NEEDS_WORK の場合のみ)

- `specs/24-gradle-multi-module-source-roots/index.md:419-423` — `:419` は valid fatal request で「graph構築を行わない」、`:421` は成功結果をprocess終了・fatal不在確認後に「構築する」とする一方、`:422-423` は受信ごとにstaging Graphを構築し、終了後は公開または破棄だけを行う契約である。`:419` を「参照完全性検証・成功公開を行わない」、`:421` を「成功結果として公開しない」等へ直し、構築時点を staging への逐次登録で一意にする必要がある。

NEEDS_WORK

## Review 2026-07-18 16:49

Verdict: PASS

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: PASS — `specs/24-gradle-multi-module-source-roots/index.md:33-62,958-1100`。統合モードを明記し、Design Doc・feature doc・context・ADRとの差分を変更提案として分類済み。図もD13の逐次受信、非公開staging Graph、fatal時破棄、成功時公開と整合する。
- 未解決論点: PASS — `specs/24-gradle-multi-module-source-roots/index.md:210-241,666-668`。D1〜D30はすべて解決済みで、未確定事項は「なし」。
- 実装対象明示: PASS — `specs/24-gradle-multi-module-source-roots/index.md:670-683,1106-1110`。`context/project.md:66-74`の全targetと一致し、Core→AnalyzerはProtocol経由に限定。コマンド契約の変更候補も`specs/24-gradle-multi-module-source-roots/index.md:1175-1178`で分類済み。
- template 必須節: PASS — `specs/24-gradle-multi-module-source-roots/index.md:6-1265`。`hooks/spec/validate_document.sh:21-44`の必須節をすべて備え、文書検証も成功。
- EARS acceptance: PASS — `specs/24-gradle-multi-module-source-roots/index.md:139-203`。入力、discovery、完全性、fatal、E2Eを観測可能なWHEN／IF／THE SYSTEM SHALL形式で規定。
- prompts 自己完結性: N/A — diagram phase reviewであり、promptsは対象に含まれない。
- 正本境界: N/A — `specs/24-gradle-multi-module-source-roots/index.md:1119-1122`。sync phaseは未実行で「反映済」行はなく、durable成果は今後ハンドオフ予定。

PASS
