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
