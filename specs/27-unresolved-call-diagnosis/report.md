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

## 対応方針 (P3_01、2026-07-21 ユーザー承認済み)

D3 基準の適用結果と、承認された判定:

| バケット                                         | 判定                                                                | 担当  | 内容                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| ------------------------------------------------ | ------------------------------------------------------------------- | ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ④ method reference / constructor reference       | **修正** (D3(a))                                                    | P4_01 | resolve 失敗時に method call と同等の bytecode 救済 + external-target 分類を追加                                                                                                                                                                                                                                                                                                                                                                          |
| ⑤ explicit super / this                          | **修正** (D3(a))                                                    | P4_01 | resolve 失敗時に親 / 自クラス constructor の bytecode 救済を追加                                                                                                                                                                                                                                                                                                                                                                                          |
| ⑦/⑧ 生成 member 救済欠落                         | **修正** (D3(a))                                                    | P4_02 | cross-module (依存 context の classes output) の生成 member 救済を設計どおり機能させる                                                                                                                                                                                                                                                                                                                                                                    |
| ⑥ receiver 型不明 (chain / lambda)               | **修正 (分類ロジック改善)** (D3(b))                                 | P4_03 | 承認規則: (i) receiver が chain (MethodCallExpr) の場合、chain を遡って最初に静的型が取れる式を探し、その型が scope 外 (source 宣言索引に無い) なら後続 call 全体を `external-target` へ分類する。chain 内に scope 内型が現れたら従来どおり diagnostic に残す (保守側)。(ii) lambda parameter 起点の場合、lambda の引数先 functional interface が scope 外型なら同様に `external-target` へ分類する。scope 内 functional interface なら diagnostic のまま |
| ①③⑦ 残余 (chain 波及 / JDK fluent / var+generic) | **修正 (根拠ベースの範囲で)** (D3(c)、ユーザー判断: 可能な限り対応) | P4_04 | chain の逐次救済: 解決失敗した chain の各 link を bytecode member index の戻り値型 (classfile Signature 由来) で前進解決し、後続 call の receiver 型を復元する。根拠 (classfile) の無い型推測は行わず、復元できない残余は diagnostic に残す                                                                                                                                                                                                               |
| 上記で解消しない残余                             | **v1 scope 外記録**                                                 | P5_01 | 修正後再計測で残った分を ADR-0004 の再検討条件との整合を明記して記録する                                                                                                                                                                                                                                                                                                                                                                                  |

## 変更履歴

## 再計測結果 (P5_01、修正後の同一条件計測)

修正は P4_01 (④⑤の bytecode 救済 + external 分類、synthesized member の method reference の D21 契約準拠) / P4_02 (⑧: 依存 project output を model の project 依存関係から救済対象へ含める) / P4_03 (⑥: chain 起点遡及 + lambda functional interface の external 判定) / P4_04 (chain の classfile 根拠前進解決、SAM arity 不明時の名前一意採用、local 変数 initializer の追跡)。各段階で同一条件 (同一 commit / 同一実行環境) の再計測を行った。

### 件数推移 (総数)

| 段階                                                                         |     Resilience4j | 追加検証プロジェクト |
| ---------------------------------------------------------------------------- | ---------------: | -------------------: |
| 修正前ベースライン                                                           |              350 |               14,248 |
| P4_01 (④⑤救済) 後                                                            |              334 |                    - |
| P4_02 (⑧cross-module) 後                                                     |              334 |                2,306 |
| P4_03 (⑥external判定) 後                                                     |              183 |                1,566 |
| P4_04 (前進解決) 後                                                          |              143 |                1,203 |
| 深掘り (起点遡及の一般化: var initializer / field / lambda 受け手) 後        |              143 |                1,161 |
| PR #29 multi-agent review 対応 (⑥のforward検証追加)                          |              197 |                1,545 |
| PR #29 Codex インラインレビュー対応 (規則(ii)撤回、Object erasure ガード) 後 | **223 (-36.3%)** |   **1,759 (-87.7%)** |

fixture (`patterns/`) は auto discovery で exit 0 (10 件 → 0 件、全パターン救済) となり、E2E は成功期待の回帰ガードへ更新済み。

**PR #29 レビュー対応による件数増加について**: 2 段階のレビューで false exclusion (scope 内 edge の欠落) のリスクを段階的に排除した。(1) multi-agent review (claude/codex/cursor) で、⑥の chain 起点遡及が「root が external というだけで中間の型不明区間を無条件に external とみなす」設計だと false exclusion を起こしうる、との指摘 (high) を受け、root から現在の call までの中間 link を classfile 根拠 (project 限定でない full classpath) で前進検証し、確認できない区間は diagnostic に戻すよう修正した。(2) 続く GitHub Codex インラインレビューで、a) full classpath 前進検証が境界なし type variable の descriptor erasure (`java.lang.Object`) を in-scope でない実型として誤用しうる、b) lambda parameter の external 判定 (規則 (ii)) が「受け手 method の receiver 型」という lambda parameter の実際の型と無関係な情報を根拠にしていた、という 2 件の追加指摘 (P1) を受け、それぞれ修正した。これにより recall (件数削減率) はさらに後退したが、false exclusion のリスクを排除し「実際に確認できたものだけを除外する」設計へ改めた。件数増加は主に ①/⑥ chain 波及・②/⑥ receiver 型不明バケットに集中しており、④⑧等の他バケットは変化していない。

### 残存の内訳 (v1 scope 外記録の対象候補)

| バケット                          | Resilience4j | 追加検証プロジェクト | 特徴                                                                                                                                   |
| --------------------------------- | -----------: | -------------------: | -------------------------------------------------------------------------------------------------------------------------------------- |
| ②/⑥ receiver 型不明 (NameExpr 等) |           56 |                  838 | 宣言型自体が解決できない変数 / lambda parameter (規則(ii)撤回により、受け手 method の receiver 型だけを根拠にした external 判定を廃止) |
| ①/⑥ chain 波及                    |           76 |                  157 | root は external と確定したが、中間 link を classfile 上で一意に確認できず forward 検証が前進できなかった区間                          |
| ⑦/⑧ 生成 member 救済欠落          |           66 |                   85 | 候補が一意でない / classfile に根拠が無い残余。R4j 分は Lombok 非依存の未特定解決失敗を含む (最小再現 2 形状では再現せず)              |
| ③ JDK fluent 後続                 |           14 |                  663 | chain 起点の型が取れず、前進解決の根拠も無い区間                                                                                       |
| ④ method reference 残余           |           11 |                   15 | scope 型不明かつ名前一意でない参照                                                                                                     |
| 合計                              |          223 |                1,759 |                                                                                                                                        |

残存はいずれも「classfile / 静的根拠で解決・分類できない」もので、根拠のない型推測で false edge / false exclusion を作らない方針 (feature doc / ADR-0004) の保守側に位置する。2 段階のレビュー対応 (⑥への forward 検証追加、Object erasure ガード、規則(ii)撤回) により、各バケットは「root/receiver は external らしいが、classfile 根拠で確認できない」ケースを diagnostic 側へ寄せた分だけ増加している。ADR-0004 の再検討条件 (「未解決が支配的」「主要ユースケースの精度を満たせない」) への抵触判定は次節で扱う。

### 全体に対する残余の比率と graph の精度 (2026-07-22 Codex インラインレビュー対応後の最終計測)

2 段階のレビュー対応後の追加検証プロジェクトで ledger の全 call site 内訳を直接計測した (`callSiteSummary` の `callSites` / `emitted` / `excluded[...]` / `diagnostic[...]`)。

| 区分                                                             | 追加検証プロジェクト |   Resilience4j |
| ---------------------------------------------------------------- | -------------------: | -------------: |
| 全 call site                                                     |               50,437 |         10,994 |
| edge 出力 (emitted)                                              |       26,137 (51.8%) |  5,665 (51.5%) |
| scope 外として明示除外 (external-target + lift-excluded-package) |       22,541 (44.7%) |  5,106 (46.4%) |
| **残存 diagnostic**                                              |     **1,759 (3.5%)** | **223 (2.0%)** |

emitted は 2 段階のレビュー対応を通じて一貫して変化していない (対応は receiver 型不明時の分類にのみ影響し、解決済み call には影響しない)。excluded がさらに減り (multi-agent review 対応後: 追加検証 22,755→22,541、R4j 5,132→5,106)、その分だけ diagnostic が増えている (追加検証: 1,545→1,759、R4j: 197→223)。これは「確認できないものは除外しない」設計を徹底した結果である。emitted と excluded は合わせて全体の 96.5%〜98.0% を占め、いずれも classfile / source 宣言索引の確定情報のみを根拠に判定している。残余 (3.5% / 2.0%) は「解決も scope 外判定もできない」もので、無理に判定すると false edge / false exclusion のリスクが生じるため diagnostic に残す設計判断を維持した。

### 完全性 gate の opt-in 緩和 (`allowIncompleteAnalysis`)

上記の残余があっても検証対象プロジェクトを実用できるよう、`metadata.allowIncompleteAnalysis=["true"]` で完全性 gate を opt-in で緩和する機能を実装した (feature doc `Parse・resolution・call 完全性` / `metadata 契約` に契約を追記済み)。既定値は `false` (従来の fatal 挙動を維持)。

- 有効化すると、primary diagnostic が残っていても exit 0 で成功し、解決済み edge / 明示除外を含む graph を traversal に使える。
- 残存分は診断として隠さない: 検出時点で `diagnostic` record (`JAVA_UNRESOLVED_SYMBOL` warning) が通常どおり streaming され、`callSiteSummary` の `diagnostic[...]` 集計・`silentOmission == 0` も維持される。
- outcome ledger の分類ロジック・帰属意味論・emit される edge の正しさには影響しない (gate の判定タイミングだけを変える)。
- 検証: 追加検証プロジェクトへ `--analyzer-meta allowIncompleteAnalysis=true` を付けて実行し、exit 0・26,137 edge 公開・残存が診断として可視のままであることを確認した。PR review 対応 (診断 4 項目を成功時の diagnostic record にも付与) 後は、opt-in 緩和時も要因分類が可能な粒度の情報が得られることを unit test で確認済み。

## 変更履歴

| 日付       | 変更内容                                                                                                                                                                                      |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-21 | P2_02 で初版作成 (修正前ベースラインの分類)                                                                                                                                                   |
| 2026-07-21 | P3_01 で対応方針を確定 (⑥ chain 起点遡及規則 / ② を⑥規則へ包含 / ①③⑦ は根拠ベースの逐次救済で対応、ユーザー承認済み)                                                                          |
| 2026-07-21 | P5_01 で修正後再計測を記録 (R4j 350→143 / 追加検証 14,248→1,203、fixture 全件救済)                                                                                                            |
| 2026-07-21 | ユーザー判断 C で起点遡及を一般化し最終値 143/1,161 を記録。全体比 (97.7%〜98.7% 解決/scope外判定) と `allowIncompleteAnalysis` opt-in 緩和機能を追加                                         |
| 2026-07-22 | PR #29 作成、multi-agent review 対応で false exclusion を修正 (197/1,545)。続く Codex インラインレビュー対応 (Object erasure ガード、規則(ii)撤回) で最終値 223/1,759 (全体 2.0%/3.5%) を記録 |
