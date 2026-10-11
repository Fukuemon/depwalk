# ADR-0004: 動的呼び出しの完全追跡を初期スコープに含めず、候補と未解決理由を観測可能にする

## Status

承認

## Context

depwalk は、Java / Spring Boot プロジェクトの変更影響調査に使う caller / callee の関係を静的解析で抽出する。  
通常のメソッド呼び出し、型階層、Spring Bean の定義は、ソースや bytecode から候補を導ける。  
一方、次の仕組みは実行時の状態によって呼び出し先が変わる。

- Reflection: `Class.forName`、`Method.invoke` などの対象が、文字列、設定値、外部入力から決まる。
- AspectJ Runtime / Spring AOP: advice が compile-time / load-time weaving や実行時の interceptor chain により、呼び出しの前後へ挿入される。
- 実行時 Proxy: JDK Dynamic Proxy / CGLIB が生成する型と interceptor chain が、ApplicationContext の構築時に決まる。
- 条件付き Bean: profile、property、classpath、環境変数などによって、有効な実装が変わる。
- SpEL / 文字列参照: 実行時に評価される値から、対象のクラスやメソッドが選ばれる。

これらを完全に追跡するには、静的解析に加えて Java Agent、JFR、APM / OpenTelemetry などによる実行時の観測と、観測した環境・入力・呼び出しの連なりの管理が必要になる。  
また実行時トレースが示すのは「観測した実行で実際に起きた呼び出し」であり、実行され得る呼び出しの全体を扱う静的解析とは意味が異なる。

## Decision

Reflection、AspectJ Runtime、実行時 Proxy、実行時の条件評価により初めて確定する呼び出しの**完全追跡**は、初期スコープに含めない。

動的機構を無視するのではなく、Java Analyzer は次の境界で扱う。

- ソース、bytecode、annotation、設定として静的に確認できる情報から候補を導ける場合は、候補 edge と解決根拠を出力する。
- 一意に確定できない場合は、根拠なく 1 つへ絞らず、候補と未解決理由を `metadata` / `diagnostic` で観測可能にする。
- 動的機構の存在を検出できる場合は、その事実を diagnostic として残し、edge が何の記録も無く欠落する状態を作らない。
- 「実際に実行された呼び出し」が必要になった場合は、静的解析の拡張ではなく Runtime Trace feature として別に設計する。

### 未解決理由の観測粒度

未解決理由は、要因のクラスへ機械的に分類できる粒度まで具体化する。  
具体化した診断の項目、出力先、sanitize の制約は、Java Analyzer の Protocol への写像の Design Doc が持つ。  
Java Analyzer の Protocol への写像: [DesignDoc.md](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/DesignDoc.md)

### 成功 Graph の完全性境界

静的な救済を尽くしても primary diagnostic に残る call がある request は、部分 Graph を成功させず `JAVA_INCOMPLETE_ANALYSIS` で fatal にする。  
動的・未解決の call の全候補、理由、位置は Protocol 共通の `error.details` に決定順で保持し、Graph を破棄した後も観測可能にする。

この gate には opt-in の緩和を持たせる。  
`metadata.allowIncompleteAnalysis` の既定は `false` とする。  
この値が `true` のとき、primary diagnostic が残っても request を fatal にせず、解決済みの edge と明示除外を含む graph を成功として公開する。  
残った分は診断としてそのまま見え、`silentOmission == 0` を維持する。  
根拠のない型推測や実行時の観測は一切追加しない。  
この緩和は未解決を隠して精度を偽装するものではなく、「候補と未解決理由を観測可能にする」方針を fatal ではなく advisory として運用する選択肢である。  
緩和時の診断出力と不変条件は、Java Analyzer の解析エンジンの Design Doc が持つ。  
Java Analyzer の解析エンジン: [DesignDoc.md](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/DesignDoc.md)

完全性境界とその緩和は、どちらも Runtime Trace を初期スコープへ追加するものではない。

### 再検討条件

次のいずれかに該当したら、この決定を再検討する。

1. 静的解析で、主要なユースケースの caller / callee の精度を満たせない。
2. Proxy / Reflection 由来の未解決が、実利用の解析結果で支配的になる。
3. 利用者が「実行され得る候補」ではなく「特定の環境・入力で実際に実行された呼び出し」を要求する。
4. Runtime Trace の収集・機密情報・性能のコストを受け入れる運用要件が定まる。

## Verification

実環境の Gradle multi-project 2 件 (公開 OSS の Resilience4j と、追加の検証プロジェクト) で、bytecode 救済と external-target 判定の欠陥を直した後に再計測した。  
全 call site のうち 97.7%〜98.7% が、解決済みか、根拠を伴う scope 外の判定に分類され、未解決として残る diagnostic は 1.3%〜2.3% だった。

残りは「chain / lambda の起点が scope 内で、かつ classfile / 確定 AST の根拠で解決も scope 外の判定もできない」形に収束している。  
Proxy / Reflection 由来ではなく、件数の比率も支配的とは言えない。  
したがって再検討条件 2 に抵触せず、他の条件にも該当しない。

## Considered Options

| 選択肢                                                                     | 強み                                                                                                                          | 弱み                                                                                                                                                                                                                    |
| -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** 完全追跡を初期スコープに含めず、候補と未解決理由を観測可能にする | 対象プロジェクトへ agent を導入せずに解析できる。同じ静的入力に対して再現可能な結果を得られる。確定、候補、未解決を区別できる | 実行時にだけ生成・選択される依存関係は完全には列挙できない。diagnostic を確認しない利用者は動的依存に気づかない可能性がある                                                                                             |
| 初期リリースから Java Agent / JFR による Runtime Trace を実装する          | —                                                                                                                             | 対象 JVM への agent の導入、実行シナリオの網羅、観測データの保管と機密情報の管理が必要になり、静的解析 CLI の導入の容易さと CI での利用を損なう。観測されなかった呼び出しを「存在しない」と誤認する別の不完全性も生じる |
| 動的呼び出しをヒューリスティックで一意に推測する                           | —                                                                                                                             | false positive / false negative の根拠を利用者が確認できず、変更影響調査で危険な過小評価を生む                                                                                                                          |
| 動的機構を検出しても何も出力しない                                         | —                                                                                                                             | 利用者が解析結果を完全と誤認する。未解決理由を観測可能にする方針に反する                                                                                                                                                |

## Consequences

### Positive

- 対象プロジェクトへ agent を導入せず、ソースと build 成果物から解析できる。
- CI とローカルで、同じ静的入力に対して再現可能な結果を得られる。
- 確定の結果、候補、未解決を区別し、解析精度の限界を利用者が判断できる。

### Negative

- 実行時にだけ生成・選択される依存関係は、完全には列挙できない。
- diagnostic を確認せず確定 edge だけを見る利用者は、動的依存に気づかない可能性がある。
- 将来 Runtime Trace を追加する場合、静的な結果と観測結果の同一 symbol の対応、重複の排除、信頼度の表現が必要になる。
