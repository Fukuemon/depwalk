# 要因分類レポート: 実環境 Gradle multi-project の残存未解決 call

> Issue #27 / spec `specs/27-unresolved-call-diagnosis/` の P2_02 (再計測・分類) の成果物。
> 分類は P1_01 で追加した診断 metadata 4 項目 (`resolutionPhase` / `exceptionClass` / `receiverKind` / `receiverTypeResolved`) と既存の reason / callKind / target による機械集計。
> 集計スクリプトは scratch 領域で実行し、repo へは残していない (再現手順は本レポートの分類規則に従えば再構成できる)。

## 計測条件

| 項目                 | 値                                                                                                                                                       |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Analyzer             | `analyzers/java/build/libs/java-analyzer.jar` (branch `feature-27`、P1_01 診断 metadata 込み)                                                            |
| 実行 JVM             | JDK 25 (Temurin)、`-Xmx6g` (Resilience4j) / `-Xmx10g` (追加検証プロジェクト)                                                                             |
| 経路                 | 自動 discovery (`--source-root` なし)                                                                                                                    |
| Resilience4j         | 公開 OSS。commit `2f3d998c85f10b8ff88a3dca9f24bacf11c3a250` (issue #27 一次調査と同一)。`./gradlew classes` で全 module の classes output を生成後に解析 |
| 追加検証プロジェクト | Spring Boot マルチモジュール (7 module)。同梱 wrapper で classes 生成後に解析。識別情報は記録しない (集計値のみ)                                         |

両プロジェクトとも `exit 1` / `JAVA_INCOMPLETE_ANALYSIS` で、総数は一次調査ベースライン (350 件 / 14,248 件) と完全一致した (再現性確認済み)。

## 分類規則 (機械集計)

診断 metadata から次の順で第一次バケットへ振り分けた。⑦と⑧、および①③と⑥の波及は metadata 単独では相互に区別できないため、複合バケットとして数え、P3_01 の判定は複合バケット単位で行う。

| バケット                            | 規則                                                                                                                                             |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| ④ method-reference fallback 欠落    | `callKind = method-reference` (constructor-reference 含む)                                                                                       |
| ⑤ explicit-super/this fallback 欠落 | `callKind = explicit-constructor-invocation`                                                                                                     |
| ③ JDK fluent 後続 (chain 波及)      | `receiverTypeResolved = false` かつ `receiverKind = MethodCallExpr` かつ target が JDK stream / Optional 系                                      |
| ①/⑥ chain 波及 (receiver 型不明)    | `receiverTypeResolved = false` かつ `receiverKind = MethodCallExpr` (上記以外)                                                                   |
| ②/⑥ receiver 型不明 (非 chain)      | `receiverTypeResolved = false` かつ `receiverKind = NameExpr` 等 (lambda parameter / generic 型変数由来)                                         |
| ⑦/⑧ 生成 member 救済欠落            | `receiverTypeResolved = true` かつ `resolutionPhase = bytecode-rescue` (receiver 型は判明しているのに source member が無く、bytecode 救済も失敗) |

## 件数分布

### Resilience4j (総数 350)

| バケット                                             | 件数 |  割合 |
| ---------------------------------------------------- | ---: | ----: |
| ①/⑥ chain 波及 (receiver 型不明)                     |  173 | 49.4% |
| ⑦/⑧ 生成 member 救済欠落 (method 65 + constructor 1) |   66 | 18.9% |
| ②/⑥ receiver 型不明 (NameExpr)                       |   56 | 16.0% |
| ④ method-reference fallback 欠落                     |   29 |  8.3% |
| ③ JDK fluent 後続                                    |   20 |  5.7% |
| ⑤ explicit-super fallback 欠落                       |    6 |  1.7% |

module 別: micrometer 194 / spring6 48 / micronaut 40 / core 23 / spring-boot3 15 / spring-boot4 15 / その他 15 (一次調査と同一分布)。

exceptionClass 別: `UnsolvedSymbolException` 305 / `UnsupportedOperationException` 39 / `IllegalStateException` 4 / `MethodAmbiguityException` 2。

### 追加検証プロジェクト (総数 14,248)

| バケット                                                     |  件数 |  割合 |
| ------------------------------------------------------------ | ----: | ----: |
| ⑦/⑧ 生成 member 救済欠落 (method 4,101 + constructor 1,866)  | 5,967 | 41.9% |
| ②/⑥ receiver 型不明 (NameExpr 3,325 + その他 5)              | 3,330 | 23.4% |
| ③ JDK fluent 後続                                            | 2,370 | 16.6% |
| ①/⑥ chain 波及 (receiver 型不明)                             | 1,554 | 10.9% |
| ④ method-reference fallback 欠落 (+ constructor-reference 7) |   990 |  6.9% |
| ⑤ explicit-super fallback 欠落                               |    37 |  0.3% |

module 別: 最多 module 8,780 / 次点 4,054 / 共通基盤 module 727 / 他 4 module 687。

exceptionClass 別: `UnsolvedSymbolException` 14,159 / `MethodAmbiguityException` 47 / `UnsupportedOperationException` 34 / 例外なし (候補選択の曖昧さ) 4 / `ConcurrentModificationException` 2。

## 代表例と観察

- **⑦/⑧ 生成 member 救済欠落** — 追加検証プロジェクトの最大要因 (41.9%)。receiver 型は判明済み (`receiverTypeResolved=true`) なのに source に member がなく、bytecode 救済も失敗している。同プロジェクトは Lombok (`@Getter` / `@AllArgsConstructor` 等) を広範に使い、cross-module 呼び出しが支配的であることから、fixture (`patterns/CrossModuleLombokCase` 等) で確認した cross-module 救済欠陥と同型と判断できる。Resilience4j 側の同バケット (66 件、Lombok 不使用) は `IntervalFunction.java:36` の `checkAttempt` (implicit-this、同一 file 内の static member) など、生成 member 以外の解決欠落を含む — P3_01 で細分を確認する。
- **①/⑥ chain 波及** — Resilience4j 最大要因 (49.4%)。`AbstractBulkheadMetrics.java:50` の `Gauge.builder(...).description(...).tag(...)` 等、chain 起点の解決失敗が後続 call を連鎖的に未解決化する。起点が解消すれば波及分も解消するため、実効件数は起点数より小さい。
- **②/⑥ receiver 型不明 (NameExpr)** — `ContextPropagator.java:108` の `Map.forEach((p, v) -> p.retrieve())` のような lambda parameter の型推論失敗が典型。
- **④ method-reference** — `PredicateCreator.java:39` の `Predicate::or` 等。`UnsupportedOperationException` の多く (R4j 39 件中の大半) は method reference 解決経路の JavaParser 未実装に対応する。
- **⑤ explicit super** — `InMemoryBulkheadRegistry.java:122` 等の 6 件 (R4j) / 37 件 (追加検証)。件数は少ないが機械的に救済可能。
- **未分類ゼロ** — 全 detail が上記バケットへ分類され、「未分類 (要診断 metadata)」は発生しなかった。D2 の 4 項目で分類目的は達成できている。

## P3_01 (対応方針判定) への引き継ぎ

1. **⑦/⑧ (5,967 + 66 件)**: fixture で cross-module 救済欠陥は確定済み (P4_02 対象)。R4j 側の非 Lombok 残余 (implicit-this の static member 等) が P4_02 の修正でどこまで解消するかは修正後再計測で確認し、残る場合は別要因として細分する。
2. **④ (990 + 29 件) / ⑤ (37 + 6 件)**: 救済ロジック欠落 (D3(a)) として P4_01 で修正。
3. **①③⑥ 波及 (計 4,117 + 249 件)**: 起点解消 (⑦⑧④⑤ の修正) でどこまで減るかを修正後再計測で観測してから、残余に対する ⑥ external-target 判定 (P4_03) と ①③ 回避策 / scope 外記録 (P4_04) を判断する。
4. **②/⑥ lambda parameter (3,330 + 56 件)**: JavaParser 上流限界 (D3(c))。回避策コストと再計測後の残存規模で判定する。
5. **⑥ の external-target 判定規則の材料**: `receiverTypeResolved=false` の detail のうち、chain 起点 / lambda 由来で receiver が scope 外 API (JDK / library) と静的に判別できるものが対象候補。具体規則は P3_01 でユーザー承認を得る。

## 変更履歴

| 日付       | 変更内容                                    |
| ---------- | ------------------------------------------- |
| 2026-07-21 | P2_02 で初版作成 (修正前ベースラインの分類) |
