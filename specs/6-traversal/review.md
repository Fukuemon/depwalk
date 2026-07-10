## Review 2026-07-08 00:01

Verdict: NEEDS_WORK

### 観点別評価 (PASS は必ず根拠 file:line / section を引用する)

- 上位文書整合: NEEDS_WORK — `specs/6-traversal/index.md:38` は Design Doc 更新要否を「未定」としているが、`specs/6-traversal/index.md:142` / `specs/6-traversal/index.md:143` で Q4 を解決済みとし、`specs/6-traversal/index.md:388` / `specs/6-traversal/index.md:389` で Design Doc Q4 を sync 時に更新すると書いているため、更新要否の状態が矛盾している。
- 未解決論点: PASS — `specs/6-traversal/index.md:129`-`specs/6-traversal/index.md:135` の D1-D5 はすべて決定欄が埋まり、`specs/6-traversal/index.md:147`-`specs/6-traversal/index.md:151` で未確定事項なしと管理されている。
- 実装対象明示: PASS — `specs/6-traversal/index.md:157`-`specs/6-traversal/index.md:163` は `context/project.md:64`-`context/project.md:72` の対象ドメイン (`core`, `traversal`, `output`, `analyzer-protocol`, `java-analyzer`) と一致し、実装有無と責務を分けている。
- template 必須節: NEEDS_WORK — 必須節自体は `specs/6-traversal/index.md:6`-`specs/6-traversal/index.md:435` に揃っているが、`specs/6-traversal/index.md:28`-`specs/6-traversal/index.md:30` の phase 状態が本文の `Performance / Security 設計`、`テスト / 評価方針`、`実装分割` の記述済み状態と同期していない。
- EARS acceptance: PASS — `specs/6-traversal/index.md:118`-`specs/6-traversal/index.md:123` に WHEN / IF / THE SYSTEM SHALL の観測可能な受け入れ基準がある。
- prompts 自己完結性: N/A — `specs/6-traversal/prompts/` は未作成で、`specs/6-traversal/index.md:370` が `spec-review` 後に prompts を生成するとしている。
- 正本境界: N/A — `specs/6-traversal/index.md:373`-`specs/6-traversal/index.md:414` に「反映済」行がなく、sync phase 未実行のため spec が作業記録として durable 判断を持つ段階である。

### 指摘 (NEEDS_WORK の場合のみ)

- `specs/6-traversal/index.md:38` / `specs/6-traversal/index.md:388`-`specs/6-traversal/index.md:389` — Design Doc 更新要否が「未定」のままだが、Q4 の循環 / 深さ上限判断は D2-D3 で解決済みで、Design Doc の Open Questions Q4 へ反映する予定も書かれている。`Design Doc 更新要否: 要` に揃えるか、まだ sync 判定前なら D2-D3 を「解決済み」としない。
- `specs/6-traversal/index.md:28`-`specs/6-traversal/index.md:30` / `specs/6-traversal/index.md:257`-`specs/6-traversal/index.md:371` — phase 表では Performance / Security が進行中、Test / Metrics と実装分割が未着手だが、本文にはそれぞれ該当節が具体化されている。phase 状態・最終更新・備考を本文の進捗に同期する。

---

## Multi-agent Review 2026-07-08 (spec-review, 3 agents: claude / codex / cursor-composer)

対応済みの上記指摘 (Design Doc 更新要否・phase 状態の同期) を確認したうえで、fresh-context の 3 エージェントに独立並列レビューさせた結果を集約する。個別のフル出力は以下の通り。

### Agent: claude (general-purpose subagent, spec-reviewer 観点を注入)

Verdict: PASS

- 上位文書整合: PASS — `specs/6-traversal/index.md:41-51` の上位文書整合テーブルは全行埋まっており、Design Doc / context / ADR の記述と矛盾しない。
- 未解決論点: PASS — D1-D5 (`index.md:131-135`) はすべて決定済み、未確定事項 (`index.md:147-151`) は「なし」。
- 実装対象明示: PASS — 実装対象テーブル (`index.md:157-163`) は `context/project.md:66-72` の対象ドメインと完全一致。
- template 必須節: PASS — 全必須節が存在 (`index.md:6,15,33,55,68,74,94,125,137,147,153,165,218,237,257,273,289,312,356,373,416,424,437`)。
- EARS acceptance: PASS — `index.md:118-123` の WHEN/IF/THE SYSTEM SHALL が「テスト観点」(`index.md:293-304`) と対応し観測可能。
- 正本境界: N/A — 「上位資料からの変更点」に「反映済み」行なし (sync phase 未実行)。
- 指摘: なし

### Agent: codex (GPT-5, `codex exec`)

Verdict: PASS

- 上位文書整合: PASS — `index.md:33,37-53` の対応表と Design Doc / context の依存方向 (`design/DesignDoc.md:132-138`, `context/architecture.md:10-16`) が整合。
- 未解決論点: PASS — D1-D5 決定済み (`index.md:129-135`)、未確定事項なし (`index.md:147-151`)。
- 実装対象明示: PASS — `index.md:153-164` と `context/project.md:64-72` が一致し、graph 公開 API 経由 (`index.md:176-183`) で直接依存を回避。
- template 必須節: PASS — 全節の行番号を個別提示し充足を確認。
- EARS acceptance: PASS — `index.md:116-123` と検証項目 (`index.md:206-216`, `index.md:291-304`) が対応。
- 正本境界: N/A — 「反映済み」行なし、sync/feature doc 作成時の予定として記録。
- 指摘: なし

### Agent: cursor-composer (`agent --model composer-2.5`)

Verdict: **NEEDS_WORK**

- 上位文書整合: NEEDS_WORK — 成功条件 (`index.md:108`: 「E2E fixture では S1/S2 の既知集合と CLI 出力の一致を検証できる」) と、スコープ (`index.md:91`: CLI `depwalk analyze` の引数/exit code/エラー表示は決めない) および実装分割 (`index.md:365`: P4 は E2E 照合の「土台」追加のみ) の間で、CLI 出力との一致検証を #6 の中でどこまで担保するかが三者間で揃っていない。
- 未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: PASS (根拠は claude/codex とほぼ同一の file:line)。
- 正本境界: N/A — 「反映済み」行なし。
- 指摘1: `index.md:108` の成功条件が CLI 出力との一致を要求する一方、`index.md:91` は CLI 引数/exit code/エラー表示の決定をスコープ外とし、`index.md:365` の P4 は E2E 照合の「土台」に留まる。#6 の実装で S1/S2 の E2E 照合をどこまで完了させるか (土台のみか、CLI 未確定でも既存出力形式で一致まで確認するか) を成功条件・スコープ・実装分割の間で揃える必要がある。
- 指摘2 (参考、既存の計画済み drift): `design/DesignDoc.md:246` の Q4 は依然「未決」のままで、spec 側は D2/D3 で解決済み。spec 自身が `spec-sync` 時に反映予定と明記 (`index.md:38-39,387-389`) しているため新規の齟齬ではないが、`spec-sync` 実行前に phase 11 (レビュー済) へ進めると Design Doc との一時的な不整合が残る点は運用上の注意点として記録する。

### 集約判定

**Verdict: NEEDS_WORK** (3 エージェント中 1 エージェントが具体的な file:line 根拠を伴う指摘を提示したため、多数決ではなく指摘の妥当性を優先して採用)

- 確認した指摘は cursor-composer の指摘1 (成功条件・スコープ・実装分割間の E2E 検証範囲の不整合) のみ。claude / codex はこの観点を見落としており、cursor-composer の指摘は具体的引用があり妥当と判断する。
- 指摘2 は spec が明示的に認識・計画済みの sync 待ち状態であり、新規の欠陥ではない。次 phase (`spec-sync`) 実行時に解消される想定のため、レビュー結果としては参考情報に留める。

---

## Multi-agent Review 2026-07-08 ラウンド2 (spec-review, 3 agents: spec-reviewer subagent / codex / cursor-composer)

ラウンド1の指摘 (成功条件 L108 の CLI 出力一致とスコープ / 実装分割の不整合) に対応し、`index.md:108` (成功条件) と `index.md:365` (P4) を「Traversal Engine が返す到達 node/edge 集合の一致」に統一した後の再レビュー。

### Agent: spec-reviewer (native subagent)

Verdict: PASS — 全観点 PASS (根拠は上位文書整合 `index.md:41-51,90-91,108`、未解決論点 `index.md:129-151`、実装対象明示 `index.md:157-163,178-180`、template 必須節 (全節行番号提示)、EARS acceptance `index.md:118-123`)。指摘なし。前回指摘の解消を確認。

### Agent: codex (GPT-5)

Verdict: PASS — 全観点 PASS (根拠は spec-reviewer とほぼ同一の file:line)。指摘なし。

### Agent: cursor-composer

Verdict: **NEEDS_WORK**

- 上位文書整合: NEEDS_WORK — 修正後の成功条件 (`index.md:108`: Traversal 結果レベルの一致) が、Design Doc S1/S2 (`design/DesignDoc.md:41-42`: 「既知の呼び出し元集合と CLI 出力が一致する」) および `context/testing.md:16`: 「既知の caller/callee 集合と CLI 出力の一致 (S1/S2)」という上位文書の測定方法定義と食い違っている。`## 上位文書整合` の該当行 (旧 L44, L47) は「継承」のままで、この差分が記録されていなかった。
- 他観点は PASS (未解決論点 `index.md:129-151`、実装対象明示 `index.md:157-163,178-180`、template 必須節 (全節)、EARS acceptance `index.md:118-123`)。
- 正本境界: N/A — 「反映済み」行なし。
- 指摘: Design Doc / context/testing.md の S1/S2 測定方法 (CLI 出力一致) を spec 側で書き換えるのではなく、`## 上位文書整合` の整合方針を「補足」に変更し、#6 の実装対象 (Traversal Engine のみ) では Traversal 層 E2E までを担当し、CLI 出力レベルの最終照合は CLI interface spec 完了後に完成する旨を注記すること。`## 上位資料からの変更点` の Design Doc / context 節にもこの補足を記録すること。

### 集約判定

**Verdict: NEEDS_WORK** (cursor-composer の指摘のみ妥当。ラウンド1と同様、他2エージェントが見落とした観点を cursor-composer が拾った)

### 対応 (適用済み)

- `## 上位文書整合` テーブルの Design Doc / context (`context/testing.md`) 行を「継承」→「補足」に変更し、S1/S2 の CLI 出力一致という上位定義は変更せず、#6 は Traversal 層 E2E までを担当し CLI 出力レベルの最終照合は CLI interface spec 完了後に完成する旨を注記として追加。
- `## 上位資料からの変更点 > Design Doc への影響` / `> context への影響` に、この測定方法の補足を記録する行を追加。
- Q4 (`design/DesignDoc.md:246` 未決) と spec 側 D2/D3 解決済みの関係についても、矛盾ではなく `spec-sync` 待ちであることを `## 上位文書整合` の注記に明記。

再レビュー (ラウンド3) で確認する。

---

## Multi-agent Review 2026-07-08 ラウンド3 (spec-review, 3 agents: spec-reviewer subagent / codex / cursor-composer)

ラウンド2の指摘に対応し、`## 上位文書整合` テーブル (Design Doc / context 行を「補足」に変更 + 注記追加) と `## 上位資料からの変更点` (Design Doc / context 節へ測定方法の補足行を追加) を適用した後の再レビュー。

### Agent: spec-reviewer (native subagent)

Verdict: PASS

- 上位文書整合: PASS — `index.md:44,47,53` の「補足」変更と注記を、実ファイル `design/DesignDoc.md:41-42` / `context/testing.md:16,20` の記述と突き合わせて正確性を検証済み。「CLI 出力一致という上位定義は変更せず、#6 は Traversal 層までを担当し CLI interface spec 完了後に完成する」という説明は矛盾ではなく射程の分界として成立する。Q4 (`design/DesignDoc.md:246` 未決) も `spec-sync` 待ちとして正しく記述されている。
- 他観点 (未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance): PASS。正本境界: N/A (sync 未実行)。
- 非 blocking 指摘: `index.md:38` の Design Doc 更新要否サマリが Q4 のみに言及し、S1/S2 測定方法の補足追加 (`index.md:393` の変更点行) に触れていない (記載漏れ、blocking ではない)。

### Agent: codex (GPT-5, `codex exec`)

Verdict: PASS (出力が遅延して到着。当初「出力なしでスキップ」と記録したが誤りのため訂正)

- 上位文書整合: PASS — `index.md:33-54` の上位文書整合が PRD 統合モード、Design Doc S1/S2・G1/G2・Q4、feature doc、context、ADR を列挙し、S1/S2 と Q4 の分界も注記。上位側 (`design/DesignDoc.md:41-42,136,246`, `context/architecture.md:12-16`, `adr/0001-analyzer-protocol-jsonl-spi.md:17-30`, `adr/0002-core-implementation-foundation.md:20-47`) と整合。
- 未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: PASS (根拠は spec-reviewer とほぼ同一の file:line)。
- 正本境界: N/A — 「反映済み」行なし、`spec-sync` 時の反映予定として記録。
- 指摘: なし

### Agent: cursor-composer

Verdict: NEEDS_WORK

- 指摘1: phase 3 (`index.md:23`) が「矛盾なし / 完了」なのに `index.md:38` の Design Doc 更新要否「要」・`index.md:54` の Q4 未決注記と併記されており、突合完了の粒度説明が不足。
- 指摘2: `index.md:410` の context 追記は `## 上位資料からの変更点` に記録されているのみで `context/testing.md` 本文へは未反映。
- 指摘3: Q4 が Design Doc 側で未決のまま実装分割 (P1-P4) が進行可能な状態 (ラウンド1指摘2の再掲)。

指摘2は Spec Workflow Contract の正本境界ルール (`spec-sync` 実行時にのみ design/context へ反映し、それまでは spec 側が作業正本) に沿った意図的な設計であり、defect ではないと判断した。指摘1は phase 3 の記述粒度の問題として妥当、指摘3はラウンド1で「新規欠陥ではない sync 待ち状態」として既に評価済みの内容の再掲。

### 集約判定

**Verdict: PASS** (spec-reviewer / codex が全観点根拠付きで PASS。cursor-composer の指摘は (1) 記述粒度の軽微な指摘として反映、(2)(3) は Spec Workflow Contract の意図した sync-pending 状態であり新規欠陥ではないため不採用。3 エージェント中 2 エージェントが PASS、1 エージェントの指摘も検証の上で非 blocking と判断)

### 対応 (適用済み)

- phase 3 (`index.md:23`) の備考に「Q4 と S1/S2 測定方法の分界は `spec-sync` 待ちとして注記済み」を追記。
- `index.md:38` の Design Doc 更新要否サマリに S1/S2 測定方法の補足も対象として明記。
- phase 11 (レビュー済) を「完了」に更新。次 phase は `spec-sync`。

---

## Multi-agent review (2026-07-08 07:42, skill: multi-agent-review, engine: agent-orchestrate)

`spec-review` (spec-reviewer 観点: 上位文書整合中心) とは別の rubric (上位文書整合 / 受け入れ基準の明確さ・検証可能性 / 曖昧さ・未定義参照・スコープ逸脱) で `context/ai-agents.md` の `review` ルーティング 3 エージェントへ並列委譲。出力は `.ai-out/agent-runs/20260708-074207/` に保存。

| agent  | status | retries | output                                          |
| ------ | ------ | ------- | ----------------------------------------------- |
| claude | ok     | 0       | `.ai-out/agent-runs/20260708-074207/claude.out` |
| codex  | ok     | 0       | `.ai-out/agent-runs/20260708-074207/codex.out`  |
| cursor | ok     | 0       | `.ai-out/agent-runs/20260708-074207/cursor.out` |

### 統合指摘 (重大度順)

- [medium ×2] `specs/6-traversal/index.md:189,212` (関連: `:235,296-297`) — BFS/DFS の探索順序を検証する受け入れ基準・テスト観点 (L189, L212, L296-297) と、Traversal result を「到達 node 集合 + edge 集合」という順序なしモデルで定義する D4/L235 が矛盾している / 根拠: cursor (L189 起点) と codex (L212 起点) が同一の矛盾を独立に指摘 / 提案: 順序を内部実装のみとするなら順序検証の受け入れ基準・テスト観点を削除する。順序を契約に含めるなら Traversal result に `visitOrder` 等の順序付きフィールドを追加し Interface 表に明記する。
- [low ×2] `specs/6-traversal/index.md:287` — Graph が空の場合の Traversal result status が未定義で、起点不在時の `startNotFound` との関係が曖昧 / 根拠: cursor・codex とも同一箇所 (Fallback L287 vs 起点不在定義 L245/L280) を指摘 / 提案: 空 graph + 起点不在は `startNotFound` とし、それ以外のケースの扱いを `## Error / Fallback 設計` に明記する。

### 単一エージェントのみ (未合意)

- [medium ×1, by cursor] `specs/6-traversal/index.md:236` — `depth` / 深さ上限の定義が無く、深さの起点基準 (0 か 1 か)・起点 node の到達集合への含否・`depthLimit` cutoff の対象 (node か edge か) が一意に決まらない。
- [medium ×1, by codex] `specs/6-traversal/index.md:327` — 深さ上限到達 node を到達集合に含めるかが、flowchart (深さ判定を追加前に行う) と本文 L265 (「上限到達以降の隣接 node を展開しない」) で読み方が割れている。
- [medium ×1, by cursor] `specs/6-traversal/index.md:109` — S1/S2 E2E の「既知 caller/callee 集合」との照合で、起点 node の含否・caller/callee 方向ごとに含める edge・`cycle` cutoff edge を到達 edge 集合に含めるかが未定義。
- [low ×1, by cursor] `specs/6-traversal/index.md:9` — メタ情報の `ステータス: Draft` が設計フェーズ状況 (phase 11 レビュー済 完了、次 phase `spec-sync`) と同期していない。Spec Workflow Contract の文書メタ情報同期要件に抵触。
- [low ×1, by cursor] `specs/6-traversal/index.md:278` — 探索順序 (`bfs`/`dfs`) の不正値に対するエラーケースが `## Error / Fallback 設計` の表に無い (方向・深さの validation のみ定義済み)。
- [low ×1, by claude] `specs/6-traversal/index.md#上位文書整合` — 節冒頭の `[PRD](../../PRD.md)` リンクが実在しない `PRD.md` を指す (統合モードで PRD ファイルは作成されていない)。

### エージェント間の相違

- なし (今回は verdict の相違ではなく、指摘の有無の相違のみ)

### 注記

- skipped: なし。3/3 エージェントの結果に基づく完全な結果。
- `spec-review` (spec-reviewer 観点、上位文書整合中心) の 3 ラウンドではこれらの指摘は検出されなかった。rubric の違い (受け入れ基準の検証可能性・曖昧さ・未定義参照) により新たに顕在化した内容 / データモデルレベルの gap であり、`spec-sync` 前に spec 内で解消することを推奨する。

### 対応 (適用済み)

- **BFS/DFS 順序 vs 順序なし集合モデルの矛盾 [medium×2]**: Reuse Policy / Performance / Testing / テスト観点で「探索順序 (BFS/DFS) は内部の訪問順序のみを決定し、到達 node/edge 集合は順序を保証しない」旨を明記し、順序検証を「内部訪問順序」の確認に限定 (`index.md` Reuse Policy 末尾、Performance、Testing、テスト観点)。
- **深さ (depth) の意味論未定義 [medium×2]**: 起点 depth=0、到達 node 集合は `depth <= maxDepth`、`depthLimit` cutoff の対象は edge のみ (node は対象外)、という定義を Interface 設計の Cutoff 行の直後に追記し、Performance / テスト観点 / Flowchart を同じ定義に統一。Flowchart は「深さ判定を node 展開前に行う」から「接続先 node の depth を edge ごとに判定する」形に再構成し、codex が指摘した図と本文の齟齬を解消。
- **S1/S2 E2E の到達集合包含規則未定義 [medium×1]**: テスト観点に「到達 node 集合は起点 node を含む。到達 edge 集合は探索木の edge のみで cutoff edge を含まない」と明記。
- **空 graph の status 未定義 [low×2]**: Fallback に「空 graph の場合も起点不在 (`startNotFound`) として扱う」と明記し、Error ケース #1 と同一の扱いに統一。
- **Draft/phase 不同期 [low×1]**: メタ情報のステータスを `Draft` → `Review` に更新。
- **探索順序不正値のエラーケース欠落 [low×1]**: Error ケース表に #5 として追加。
- **PRD リンク破損 [low×1]**: `[PRD](../../PRD.md)` の markdown リンクを外し、統合モードで PRD ファイルが存在しない旨を地の文で説明。

再レビュー (`spec-review`) で確認する。

---

## Multi-agent Review 2026-07-08 ラウンド4 (spec-review, 3 agents: spec-reviewer subagent / codex / cursor-composer)

`multi-agent-review` skill の指摘対応 (順序なし集合の明記、depth 意味論の統一等) 後の再レビュー。

### Agent: spec-reviewer (native subagent)

Verdict: **NEEDS_WORK**

- 上位文書整合 / 実装対象明示 / template 必須節 / EARS acceptance: PASS。prompts 自己完結性 / 正本境界: N/A。
- 未解決論点: NEEDS_WORK — 修正で追加した「探索順序は内部の訪問順序のみを制御し契約に現れない」という主張が、**合流 (ダイヤモンド型) graph では成立しない**設計ギャップを検出。
- 指摘の核心: 探索木 edge 方式 (「実際に辿った edge のみを到達 edge 集合に含める」) では、同一 node へ複数の有効経路がある場合にどの edge が探索木 edge になりどれが `cycle` cutoff になるかが BFS/DFS の選択で変わる。具体例: `O→A→A2→M` (depth=3) と `O→B→M` (depth=2)、`maxDepth=3` のとき、DFS では `A2→M` が採用され `B→M` が `cycle` cutoff、BFS では逆。呼び出しグラフでは合流が一般的なため、(1) 到達 edge 集合の内容が探索順序依存になり D4 の「順序に依存しない集合」契約が破れる、(2) 循環していない合流 edge を `cycle` と誤標識する意味論的誤りが生じる。
- 対応案として (a) cutoff 判定を常に BFS 相当 (最短 depth) で計算、(b) 到達 edge 集合を順序非依存な定義 (誘導部分グラフ等) に変更、のいずれかを新論点として解決するよう提案。

### Agent: codex (GPT-5)

Verdict: PASS — 全観点 PASS。上記ギャップは検出せず。

### Agent: cursor-composer

Verdict: PASS — 全観点 PASS (S1/S2 分界・Q4 sync 待ちの記録も含めて整合と判定)。上記ギャップは検出せず。

### 集約判定

**Verdict: NEEDS_WORK** (3 エージェント中 2 が PASS だが、spec-reviewer の指摘は具体的な反例グラフ付きで検証可能な設計バグであり、多数決ではなく指摘の妥当性を優先)

### 対応 (適用済み): D6 の新設と解決

指摘を受けて論点 D6「合流 (複数経路で同一 node へ到達) と循環の区別」を `## 設計時の論点` に追加し、次の通り解決した (案 (b) を発展させた形):

**結果モデルを「探索の副産物」ではなく「グラフの数学的性質」として定義し直す:**

1. **minDepth**: 起点から探索方向への最短距離 (起点=0)。グラフの性質であり探索順序に依存しない。
2. **到達 node 集合**: `minDepth <= maxDepth` の node (未指定時は全到達可能 node)。
3. **到達 edge 集合**: 両端が到達 node 集合に属する探索方向の全 edge (**誘導部分グラフ**)。合流 edge は呼び出し関係として保持され、失われない。
4. **`cycle`**: cutoff (除外) ではなく**注釈**に変更。到達部分グラフ内で閉路を構成する edge (self-loop または同一 SCC 内の edge) に付与し、到達 edge 集合には含めたまま。SCC 判定により「循環」の意味論が厳密になり、合流の誤標識が構造的に起こらない。
5. **`depthLimit` cutoff**: 到達 node から `minDepth > maxDepth` の node への edge。minDepth 基準なので決定的。
6. **訪問済み node 管理**: 無限ループ防止の内部機構に格下げし、結果契約から除外 (D2 の無限ループ防止判断自体は維持)。

いずれも `O(V+E)` (minDepth=BFS、誘導 edge 収集、Tarjan SCC) で D4 の性能方針と整合。案 (a) は DFS 時の cutoff だけ BFS 相当にする非対称性が残り、案 (c) (BFS 固定) は D1 を覆すため不採用。

反映箇所: 設計時の論点 / 解決済みの論点 (D2 補記 + D6 追加) / User Flow / Reuse Policy / Performance (機能仕様・設計) / Interface (Cycle 注釈・DepthLimit cutoff の分離、到達集合の定義 blockquote) / Content / Error ケース 4 / テスト観点 (合流 graph・順序非依存性の観点を追加) / Flowchart (2 段階構成に再構成) / 上位資料からの変更点 (Design Doc Q4 行・feature doc D2/D6 行) / phase 表 / レビュー表 / 変更履歴。

再レビュー (ラウンド5) で確認する。

---

## Multi-agent Review 2026-07-08 ラウンド5 (spec-review, 3 agents: spec-reviewer subagent / codex / cursor-composer)

D6 (合流と循環の区別) の新設・解決後の再レビュー。

### Agent: spec-reviewer (native subagent)

Verdict: **NEEDS_WORK** (ただし D6 の設計自体は解消を確認)

- **D6 集中評価の結論**: 「D6 の数学的定義は 4 回目の指摘 (順序依存性・合流の誤標識) を実質的に解消しており、定義に致命的な穴はない」。
  - 順序非依存性: minDepth / 誘導部分グラフ / SCC はグラフの性質であり訪問順序に依存しない。ダイヤモンド型では全 edge が保持され BFS/DFS で同一。
  - 合流の誤標識: 「edge が有向閉路上にある ⟺ 両端が同一 SCC (self-loop 含む)」は数学的に正確。非自明 SCC を持たないダイヤモンドは cycle 注釈を受けない。
  - 誘導 edge / depthLimit cutoff の分類は網羅的かつ排他的。`maxDepth=0` 境界も定義済み。
  - 節間一貫性 (User Flow / Reuse Policy / Performance / Interface / Content / Error / テスト観点 / Flowchart / E2E 包含規則) を確認。
- 残指摘 (blocking): メタ情報 (`index.md:9` 「phase 11 完了」) と phase 表 (`:31` 進行中) の矛盾 — D6 対応時の同期漏れ。
- non-blocking 推奨: (1) Flowchart 段階 1 に「minDepth は DFS 指定時も正確 (BFS 相当)」の注記、(2) 内部訪問順序の検証手段 (white-box) の明記。

### Agent: codex (GPT-5)

Verdict: PASS — 全観点 PASS (D1-D6 決定済み、上位文書整合、実装対象、必須節、EARS を根拠付きで確認)。指摘なし。

### Agent: cursor-composer

Verdict: **NEEDS_WORK**

- 指摘1: メタ情報 (`:9`) と phase 表 (`:31`) の矛盾 (spec-reviewer と同一)。
- 指摘2: 論点テーブル D2 決定欄 (`:133`) に「再訪 edge を `cycle` cutoff として記録する」という D6 前の旧記述が残留し、SCC 注釈 (`:240-241`) / 解決済み D6 (`:148`) と食い違う。
- 指摘3: 実装分割 P3 (`:385`) の「`cycle` cutoff」表記が D6 後の用語 (「`cycle` 注釈 (SCC 判定)」) と不一致。

### 集約判定

**Verdict: NEEDS_WORK → 対応済み** (D6 の中核設計は spec-reviewer が数学的に解消確認。残指摘はすべて D6 反映時の同期漏れ・旧表記残留であり、設計上の新規欠陥ではない)

### 対応 (適用済み)

- メタ情報のステータスを phase 表と同期 (「phase 11 進行中。再 review 待ち」)。
- 論点テーブル D2 決定欄を「訪問済み node で再訪を抑止して無限ループを防ぐ (循環の標識方法は D6 で SCC 注釈へ精密化)」に更新。
- 実装分割 P3 を「訪問済み node 管理 (内部機構)、`cycle` 注釈 (SCC 判定)、`depthLimit` cutoff、到達 node / edge 集合 (誘導部分グラフ)」に更新。
- non-blocking 推奨 2 件も反映: Flowchart 段階 1 に minDepth の正確性注記を追加、テスト観点 / Testing に内部訪問順序の white-box 検証を明記。

再レビュー (ラウンド6) で確認する。

---

## Multi-agent Review 2026-07-08 ラウンド6 (spec-review, 3 agents: spec-reviewer subagent / codex / cursor-composer)

ラウンド5の同期漏れ (メタ情報/phase表の矛盾、D2決定欄の旧記述、実装分割P3の旧表記、non-blocking推奨2件) への対応後の再レビュー。

### Agent: spec-reviewer (native subagent)

Verdict: PASS — 全観点 PASS。ラウンド5の4指摘すべてについて解消を個別確認: (1) メタ情報と phase 表がともに「phase 11 進行中」で一致、(2) D2 決定欄の旧記述が除去され SCC 注釈への言及に統一、(3) 実装分割 P3 が「`cycle` 注釈 (SCC 判定)」表記に更新され旧「cycle cutoff」表記は spec 全体で検出されず、(4) Flowchart 段階1への minDepth 正確性注記とテスト観点への white-box test 明記を確認。新たな矛盾なし。

### Agent: codex (GPT-5)

Verdict: PASS — 全観点 PASS。上位文書整合・未解決論点 (D1-D6 決定済み)・実装対象明示・template 必須節・EARS acceptance のすべてに file:line 根拠あり。指摘なし。

### Agent: cursor-composer

Verdict: PASS — 全観点 PASS。D1-D6 決定済み、Q4/S1-S2 の spec-sync 待ちも矛盾ではないと明示的に確認。指摘なし。

### 集約判定

**Verdict: PASS (3/3 エージェント全会一致)**

ラウンド4で発見された設計上の欠陥 (探索木 edge 方式の順序依存性・合流の誤標識) は D6 (minDepth + 誘導部分グラフ + SCC 注釈による再定義) で解消され、ラウンド5・6で数学的正しさと文書全体の同期の両方を複数エージェント独立検証で確認した。

### 総括: ラウンド1〜6 で解消した指摘

1. 成功条件の CLI 出力一致とスコープ / 実装分割の不整合 (ラウンド1)
2. 修正後の成功条件と Design Doc S1/S2 定義の分界が未記録 (ラウンド2)
3. phase 3 記述粒度、Design Doc 更新要否サマリの記載漏れ (ラウンド3)
4. multi-agent-review skill による内容レベルの gap 7 件: 順序 vs 集合モデルの矛盾、depth 境界未定義、S1/S2 包含規則未定義、空 graph status 未定義、メタ情報不同期、エラーケース欠落、PRD リンク破損 (別 rubric ラウンド)
5. **探索木 edge 方式の順序依存性・合流の誤標識という設計バグ (ラウンド4) → D6 で解消 (ラウンド5)**
6. D6 反映時の同期漏れ (メタ情報/phase表/論点テーブル/実装分割の旧記述残留) (ラウンド5 → ラウンド6 で解消)

---

## Prompts Phase Review 2026-07-08 (spec-review, spec-reviewer subagent, 2 rounds)

実装 prompts 4 件 (`P1_01_core_graph-view.md` / `P2_01_traversal_search-api.md` / `P3_01_traversal_result-model.md` / `P4_01_core_e2e-fixture.md`) の生成後レビュー。

### ラウンド1: NEEDS_WORK

- prompts 自己完結性: 必須 10 セクション完備・antipatterns 逐語一致・命名規則適合・依存関係 (P1→P2→P3→P4) が spec 実装分割と一致・設計仕様抜粋が feature doc の D6 後契約と一致・P1 の protocol フィールド名が `core/internal/protocol/types.go` の実在フィールドと一致、を確認。
- [blocking] P4 の「形式は既存の `testdata/` 配下の JSONL contract fixture の慣行に合わせた」が探索誘発表現 (実体 `testdata/analyzer-protocol/records/` が参照 path に未掲示で、絶対ルールの「参照 path を外れて探索しない」と矛盾)。
- [minor] spec の上位文書整合の注記 (旧 L55) と Design Doc 更新要否 (旧 L38) に「spec-sync 未実行」の残置記述。
- [non-blocking] 生成後の依存関係表 (並列可否列を含む一覧) が成果物として残っていない。
- 正本境界: PASS (sync 済、feature doc への正本ハンドオフ・spec 側の決定時スナップショット降格・二重正本なしを確認)。

**対応**: P4 に `### fixture 形式の慣行` をインライン化 (JSONL 1 行 1 record / camelCase key / 実例) し `testdata/analyzer-protocol/records/valid/` を参照 path に明示追加。spec の sync 記述を「反映済み (2026-07-08)」へ同期。spec の `## 実装分割 > 生成済み prompts` に一覧表を追加。

### ラウンド2: NEEDS_WORK → 対応済み

- 前回 3 指摘の解消をすべて file:line 根拠付きで確認 (P4 の camelCase key が protocol の json tag と一致することも検証)。
- prompts 自己完結性 / 正本境界 / 上位文書整合 / 未解決論点 / 実装対象明示 / EARS acceptance: PASS。
- 残指摘 [minor×2]: メタ情報のステータス注記が prompts 生成前の未来形のまま、変更履歴に prompts 生成 / 指摘対応のエントリ欠落。

**対応**: ステータスを「prompts 生成済み。実装フェーズへ進める」へ更新し、変更履歴に 2 エントリを追加。レビュー表にも prompts phase の 2 ラウンドを記録。

### ラウンド3 (最終確認): PASS

- 前回のメタ情報同期 2 件の解消を file:line 根拠付きで確認。メタ情報 / phase 表 / レビュー表 / 変更履歴 / 本文の相互照合で新たな不整合なし。
- 全 7 観点 PASS。prompts 自己完結性は必須 10 セクション・antipatterns 逐語注入・探索誘発表現の不在・命名規則準拠・依存表の成果物化を再確認。正本境界は feature doc へのハンドオフ・二重正本なし・用語規約準拠を再確認。
- **prompts phase 完了。実装フェーズ (P1_01 → P2_01 → P3_01 → P4_01 の直列実行) へ進められる状態。**

---

## Implementation Review 2026-07-10 (code-review skill, high effort: 8 angles 並列)

P1-P4 実装 diff (+1696 行) を 8 観点 (line-scan / removed-behavior / cross-file / reuse / simplification / efficiency / altitude / conventions) の並列 finder でレビュー。**コアの正確性 (Tarjan SCC・深さ境界・BFS/DFS 順序非依存性・誘導 edge / cutoff 分類) は line-scan agent が手動トレースで健全と確認**。

### 対応した指摘

- **[3観点合意] Traverse の二重走査と分岐非対称**: 到達 node 集合を常に `minDepths` から単一導出に統一。`visitOrder` は D1 の Order 意味論の実装 + white-box 検証として保持 (Traverse の走査には使わない。結果契約が順序非依存であるため観測上の差はゼロ)。buildResult への depths が常に非 nil になり、nil-map の `TargetMinDepth: 0` 潜在トラップも同時解消。
- **[3観点合意] `graph.Neighbors` の内部スライス共有**: godoc に「返却スライスは内部 adjacency と共有。変更禁止」を明記。
- **direction の不正値**: `Neighbors` を明示 switch にし、不正値は nil を返す (silent callee fallback を排除) + テスト追加。
- **AddEdge の前提条件**: endpoint が登録済み node であることを godoc に明記 (Protocol が未解決 callee edge を拒否し、record 読み込み層が保証する責務)。
- **validation error 時の Result**: zero value であり使用禁止であることを godoc に明記。
- **テストギャップ 2 件**: frontier 間 cross edge (両端が maxDepth ちょうどの node を結ぶ非探索木 edge が誘導 edge として残る) の fixture `frontier-cross.json` を追加。`maxDepth=0` + 起点 self-loop (誘導 edge + cycle 注釈として残る) の挙動固定テストを追加し、spec / feature doc の「全隣接 edge が cutoff」という不正確な記述を精密化。
- **[conventions] `context/testing.md` 違反 2 件**: BFS/DFS 同一性テストの境界値列挙を `t.Run` subtest 化。
- **効率微修正**: minDepths の queue を head-index 化 (backing array の肥大抑止)、Tarjan の adjacency map 二重 lookup を frame へのスライスキャッシュで解消、result maps に容量ヒント。

### 見送った指摘 (理由付き)

- fixture 構造体を `protocol.CallEdge` で置換 → traversal (test) から protocol への依存を作るため見送り (fixture schema は自己記述が正)。
- fixture loader の共有 test helper 化 → 新規共有インフラの導入はスコープ外 (将来 3 例目が出たら検討)。
- E2E loader での Builder 利用 → loader は意図的に raw API を使う (fixture の node 一覧を正とし、auto-registration で誤りを隠さない)。
- minDepths / visitOrder の walk 骨格共通化 → 過剰抽象のリスクが利得を上回る。
