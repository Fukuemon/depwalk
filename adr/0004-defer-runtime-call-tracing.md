# ADR-0004: 動的呼び出しの完全追跡を初期スコープに含めない

## 状態

承認

## 決定日

2026-07-11

## 背景

depwalk は、Java/Spring Boot プロジェクトの変更影響調査に使う caller / callee 関係を静的解析で抽出する。通常のメソッド呼び出し、型階層、Spring Bean 定義はソースや bytecode から候補を導ける一方、次の仕組みは実行時状態によって呼び出し先が変わる。

- Reflection: `Class.forName`、`Method.invoke` 等の対象が文字列、設定値、外部入力から決まる。
- AspectJ Runtime / Spring AOP: advice が compile-time / load-time weaving や実行時 interceptor chain により呼び出し前後へ挿入される。
- 実行時 Proxy: JDK Dynamic Proxy / CGLIB が生成する型と interceptor chain が ApplicationContext 構築時に決まる。
- 条件付き Bean: profile、property、classpath、環境変数等によって有効な実装が変わる。
- SpEL / 文字列参照: 実行時評価される値から対象クラスやメソッドが選択される。

これらを完全に追跡するには、静的解析だけでなく Java Agent、JFR、APM / OpenTelemetry 等による実行時観測と、観測した環境・入力・経路の管理が必要になる。また実行時トレースが示すのは「観測した実行で通った経路」であり、実行され得る全経路を網羅する静的解析とは意味が異なる。

## 決定

Reflection、AspectJ Runtime、実行時 Proxy、実行時条件評価により初めて確定する呼び出しの**完全追跡**は、初期スコープに含めない。

動的機構を無視するのではなく、Java Analyzer は次の境界で扱う。

- ソース、bytecode、annotation、設定として静的に確認できる情報から候補を導ける場合は、候補 edge と解決根拠を出力する。
- 一意に確定できない場合は、根拠なく一つへ絞らず、候補と未解決理由を `metadata` / `diagnostic` で観測可能にする。
- 動的機構が存在することを検出できる場合は、その事実を diagnostic として残し、edge を黙って欠落させない。
- 「実際に実行された経路」が必要になった場合は、静的解析の拡張ではなく Runtime Trace feature として別途設計する。

spec #24 で成功 Graph の完全性境界を追加した。静的な救済を尽くしても primary diagnostic に残る call がある request は、部分 Graph を成功させず `JAVA_INCOMPLETE_ANALYSIS` で fatal にする。ただし、動的・未解決 call の全候補、理由、位置は Protocol 共通 `error.details` に決定順で保持し、Graph を破棄した後も観測可能にする。この変更は Runtime Trace を初期スコープへ追加するものではない。

再検討条件は次のいずれかとする。

- 静的解析で主要ユースケースの caller / callee 精度を満たせない。
- Proxy / Reflection 由来の未解決が、実利用の解析結果で支配的になる。
- 利用者が「実行され得る候補」ではなく「特定環境・入力で実際に実行された経路」を要求する。
- Runtime Trace の収集・機密情報・性能コストを受け入れる運用要件が定まる。

### 状態追記 (spec #27、2026-07-21)

決定内容自体は変更しない、実測に基づく状態の追記として記録する。

**未解決理由の観測可能性を具体化した (D2)**: `error.details.metadata` へ sanitize 済みの診断 4 項目 (`resolutionPhase` / `exceptionClass` / receiver 式種別 / receiver 型取得成否) を追加した。本 ADR の決定「候補と未解決理由を `metadata` / `diagnostic` で観測可能にする」を、要因クラスへ機械的に分類できる粒度まで具体化したもの。詳細は [feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md) の `diagnostic / error code 体系`。

**再検討条件を実測で評価し、抵触しないと判断した**: 実環境の Gradle multi-project 2 件 (Resilience4j、追加検証プロジェクト) で bytecode 救済・external-target 判定の欠陥修正後に再計測したところ、全 call site のうち 97.7%〜98.7% が解決済みまたは根拠を伴う scope 外判定で分類され、未解決として残る diagnostic は 1.3%〜2.3% だった。残余は「chain / lambda の起点が scope 内で、かつ classfile / 確定 AST の根拠で解決も scope 外判定もできない」形状に収束しており、Proxy / Reflection 由来ではなく、件数比率も「支配的」とは言えない。したがって現時点で本 ADR の再検討条件のいずれにも抵触しないと判断する。詳細な計測値と要因分類は [spec #27 report](../specs/27-unresolved-call-diagnosis/report.md) を参照する。

**完全性 gate の opt-in 緩和を追加した (Runtime Trace とは独立)**: 上記の残余があっても実プロジェクトで graph を実用できるよう、`metadata.allowIncompleteAnalysis` (既定 `false`) を追加した。有効時は primary diagnostic が残っていても request を fatal にせず、解決済み edge / 明示除外を含む graph を成功として公開する。残存分は診断としてそのまま可視であり (`silentOmission == 0` を維持)、根拠のない型推測や実行時観測を一切追加しない。この機構は「未解決を隠して精度を偽装する」ものではなく、決定に既にある「候補と未解決理由を観測可能にする」方針の運用面の緩和 (fatal ではなく advisory として扱う選択肢) であり、再検討条件 4 (Runtime Trace の運用要件が定まる) には該当しない。詳細は [feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md) の完全性 gate の節。

## 代替案

- 初期リリースから Java Agent / JFR による Runtime Trace を実装する。
  - 却下理由: 対象 JVM への agent 導入、実行シナリオの網羅、観測データの保管と機密情報管理が必要になり、静的解析 CLI の導入容易性と CI 利用を損なう。観測されなかった経路を「存在しない」と誤認する別の不完全性も生じる。
- 動的呼び出しをヒューリスティックで一意に推測する。
  - 却下理由: false positive / false negative の根拠を利用者が確認できず、変更影響調査で危険な過小評価を生む。
- 動的機構を検出しても何も出力しない。
  - 却下理由: 利用者が解析結果を完全と誤認する。未解決理由を観測可能にする方針に反する。

## 影響

### 良い影響

- 対象プロジェクトへ agent を導入せず、ソースと build 成果物から解析できる。
- CI とローカルで同じ静的入力に対して再現可能な結果を得られる。
- 確定結果、候補、未解決を区別し、解析精度の限界を利用者が判断できる。

### 悪い影響 / トレードオフ

- 実行時にのみ生成・選択される依存関係は完全には列挙できない。
- diagnostic を確認せず確定 edge だけを見る利用者は、動的依存を見落とす可能性がある。
- 将来 Runtime Trace を追加する場合、静的結果と観測結果の同一 symbol 対応、重複排除、信頼度表現が必要になる。

### 影響範囲

- 対象モジュール / package: `java-analyzer`, `analyzer-protocol`, `core`

## 実装・運用への反映

- spec 更新要否: 要。Interface Dispatch / Spring DI の後続 spec で非対象、diagnostic、再検討条件を継承する。
- context / AI 向け設定更新要否: 不要。既存の Design Doc Non Goals と整合する。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): Non Goals、Java Analyzer の段階導入
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): Phase 1 の既知の制約、Phase 2 / Phase 3
- [specs/9-java-analyzer](../specs/9-java-analyzer/): Java Analyzer Phase 1 の決定記録
- [specs/24-gradle-multi-module-source-roots](../specs/24-gradle-multi-module-source-roots/): 不完全解析を fatal にしつつ未解決 detail を保持する決定経緯
- [specs/27-unresolved-call-diagnosis](../specs/27-unresolved-call-diagnosis/): 診断 metadata の具体化、実測による再検討条件の評価、完全性 gate の opt-in 緩和の決定経緯
