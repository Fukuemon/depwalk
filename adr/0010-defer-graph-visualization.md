# ADR-0010: 可視化出力 (DOT / Mermaid) をスコープから外し、解析精度と永続化を優先する

## 状態

承認

## 決定日

2026-08-01

## 背景

`design/DesignDoc.md` は当初から出力形式として Console / JSON / DOT / Mermaid の 4 つを掲げ、ロードマップでも「グラフ出力 (DOT / Mermaid)」を独立した段として置いていた。実装は Console / JSON まで進み、DOT / Mermaid は `Format` の定数だけが存在して formatter は未登録である (`--format dot` は許容値エラーになる)。

一方、Phase1 の実装を通して**残っている弱点は出力形式の数ではない**ことがはっきりした。

- 呼び出しグラフに **edge が 1 本欠けると答えが間違う**。影響調査で最も危険なのは偽陰性であり、Java/Spring ではアノテーション駆動の呼び出し (`@Scheduled` / `@EventListener`)、Mapper interface (MyBatis / Spring Data JPA)、lambda / method reference のように framework 由来の暗黙の edge が取りこぼしの温床になる
- **解析が重い**。Gradle daemon の起動と SootUp の索引構築を毎回行うため、繰り返しクエリを投げる使い方に耐えない
- **「この PR で影響範囲がどう変わったか」に答えられない**。結果は request ごとに in-memory で捨てており、2 時点の比較ができない (ADR-0002 / `context/architecture.md` の State Boundary)

出力形式を増やしても、これらは 1 つも解けない。

## 決定

### 1. DOT / Mermaid をスコープから外す

Goal / 成功条件 / スコープから DOT / Mermaid を除き、対応形式を **Console / JSON の 2 つ**とする。`design/features/output/DesignDoc_output.md` の「DOT / Mermaid の I/F 要件」節も削除する。

可視化そのものを永久に否定するのではなく、**形式を未定のままロードマップの後方へ置く**。後述のとおり DOT / Mermaid が最適な形式とは限らないためである。

### 2. 可視化の形式は再導入時に決め直す

DOT は Graphviz、Mermaid は対応ビューアという **外部レンダラへの依存**を利用者に負わせる。再導入するなら、自己完結した単一 HTML を出す案 (`--format html`) を含めて比較し直す。いま形式を決めておく利益はない。

### 3. 提供形態は CLI に限定したままとする

Web Viewer は現行の Non Goals (「IDE Plugin / Web UI の提供 (本ツールは CLI に限定する)」) と正面から衝突する。今回は Non Goals を維持し、採用するときは提供形態そのものを別 ADR で決め直す。

### 4. 優先する対象

ロードマップの前方を次の順に置き換える。

| 順  | 対象                             | 解けること                                               |
| --- | -------------------------------- | -------------------------------------------------------- |
| 1   | 解析精度の強化                   | framework 由来の暗黙の edge を拾い、偽陰性を減らす       |
| 2   | グラフの永続化 (commit SHA 単位) | 再解析の回避と、2 時点の差分 (= PR による影響範囲の変化) |
| 3   | CI 連携 (Artifact / PR コメント) | 2 の上に乗る。単体では毎回フル解析になり実用に耐えない   |

2 は ADR-0002 の「永続ストアは現時点で持たない」と `context/architecture.md` の State Boundary を変える。着手時に別 ADR を要する。

## 代替案

### 却下: DOT / Mermaid を残したまま優先度だけ下げる

文書の変更量は最小になるが、Goal と成功条件に「達成する予定のない項目」が残り続ける。成功条件 S3 は「4 形式で出力できる」ことを要求しており、これを満たさないまま放置すると成功条件が形骸化する。

### 却下: DOT / Mermaid の代わりに `--format html` を即座に採用する

可視化の受け皿としては筋がよいが、いま決める必要がない。解析精度と永続化を進めた後のほうが、何を可視化すべきか (グラフ全体か、差分か) がはっきりする。差分を見せる UI が要るなら、そもそも DOT / Mermaid では表現しきれない。

### 却下: Web Viewer を提供形態に加える

CLI 限定という前提はアーキテクチャ全体 (単一バイナリ配布、プロセス境界、状態を持たない設計) の根拠になっている。これを覆すのは可視化のための判断としては影響が大きすぎる。必要になった時点で提供形態そのものの ADR として扱う。

## 影響

### 良い影響

- Goal / 成功条件が実際に目指すものだけになる
- 出力形式の追加より先に、答えの正しさ (解析精度) と実用速度 (永続化) に資源を向けられる
- 可視化の形式を将来の要件に合わせて選び直せる

### 悪い影響 / トレードオフ

- 可視化の口が当面なくなる。グラフを図で見たい利用者は JSON から自前で変換する必要がある
- `core/internal/output` に `FormatDOT` / `FormatMermaid` の定数が残る。formatter は未登録で `--format dot` は既に許容値エラーになるため**利用者から見た挙動は変わらない**が、コードと文書の対応は崩れる。定数の削除は挙動を変えない cleanup として別 issue で扱う ([issue #40](https://github.com/Fukuemon/depwalk/issues/40) はコードの挙動変更を対象外としている)

### 影響範囲

- 対象モジュール / package: `output` / `core`。文書は `design/DesignDoc.md` と `design/features/output/DesignDoc_output.md`

## 実装・運用への反映

- spec 更新要否: 否。[issue #40](https://github.com/Fukuemon/depwalk/issues/40) は文書整備の issue であり、本 ADR は同 issue の推敲作業中に生じたスコープ判断として独立に記録する
- context / AI 向け設定更新要否: 否
- 後続: `FormatDOT` / `FormatMermaid` 定数の削除を別 issue で起票する (実施: [#64](https://github.com/Fukuemon/depwalk/issues/64) / PR #65。あわせて feature doc と context の記述も PR #70 で落とした)

## 関連ドキュメント / チケット

- [ADR-0002](0002-core-implementation-foundation.md): 「永続ストアは現時点で持たない」。決定 4 の 2 が変更を要する
- [ADR-0009](0009-design-doc-describes-current-state.md): ロードマップを定めるのは DesignDoc とする決定
- [design/DesignDoc.md](../design/DesignDoc.md): Goal / 成功条件 / スコープ / ロードマップを定める
- [design/features/output/DesignDoc_output.md](../design/features/output/DesignDoc_output.md): 出力形式を定める
- issue / PR: [#40](https://github.com/Fukuemon/depwalk/issues/40)
