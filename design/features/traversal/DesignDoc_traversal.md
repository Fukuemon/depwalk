---
type: feature-design
title: "Traversal (Caller / Callee 探索)"
description: 呼び出しグラフの探索意味論と、深さ・訪問順・結果構造の契約
status: 完了
keywords: [traversal, caller, callee, depth, minDepth]
governs:
  - core/internal/traversal
verified_commit: 9b9d79d
---

# Feature 設計: Traversal (Caller / Callee 探索)

Traversal Engine の設計正本。

## 探索とは

[Graph Engine](../graph/DesignDoc_graph.md) が保持する呼び出しグラフを、起点メソッドから **edge を辿って関係するメソッドを集める**のが探索である。方向が 2 つある。

- **caller 方向** = 「このメソッドを呼んでいるのは誰か」を遡る。変更の影響範囲を知りたいときに使う
- **callee 方向** = 「このメソッドが何を呼んでいるか」を下る。処理の流れを追いたいときに使う

いずれも 1 段で止まらず再帰的に辿るため、「A を変えると最終的に誰が影響を受けるか」が分かる。ただし呼び出しは循環しうる (A が B を呼び B が A を呼ぶ) ので、無限に辿らない打ち切りが要る。その打ち切りをどう定義するかが本 doc の中心である。Graph Engine が保持する node / edge を入力に、caller / callee 方向の到達集合を計算する探索エンジンの API・結果モデル・打ち切り意味論を定義する。

本 doc は Traversal result の契約 (到達 node / edge 集合、`cycle` 注釈、`depthLimit` cutoff) の正本である。全体像は [DesignDoc](../../DesignDoc.md)、Core の package 境界は [architecture](../../../context/architecture.md) を参照する。

## 背景・要件解釈

depwalk は、指定メソッドの caller / callee を探索し、既知の呼び出し関係集合と一致する結果を返すことを成功条件にしている ([DesignDoc](../../DesignDoc.md) の S1「呼び出し元の網羅的な列挙」と S2「呼び出し先の列挙」)。

この成功条件は 3 つの層を経て満たされる。まず Analyzer Protocol / SPI (analyzer-protocol feature) が `methodSymbol` / `callEdge` を Core 側へ渡す境界を提供する。次に Graph Engine がそれらから呼び出しグラフを構築する。最後に Traversal Engine がそのグラフを入力として、caller / callee 方向の到達集合を計算する。

循環呼び出し・再帰の打ち切り条件は本 doc が定める。探索 API、探索結果モデル、循環 / 深さ上限の意味論はいずれも本 doc を正本とする。

## スコープ

### やること

- caller 方向 / callee 方向の探索 API を提供する。
- 探索方向、起点メソッド、深さ上限、探索順序 (BFS / DFS) を受け取る。探索順序は受け付けて検証するだけで、結果にも探索の実行にも影響しない (下記「探索順序の位置づけ」)。
- 循環呼び出し / 再帰を検出し、無限ループせず観測可能な形で結果に含める。
- 深さ上限到達を検出し、打ち切り情報を結果に含める。
- Graph Engine が公開する読み取り API 経由で探索する (Graph の内部構造に依存しない)。

### やらないこと

- Java ソースの解析、型解決、DI 解決は行わない (`java-analyzer` の責務)。
- Analyzer Protocol / SPI / Model schema は再定義しない (正本は analyzer-protocol feature doc と ADR-0001)。
- Output Engine の表現形式は決めない (正本は [output feature doc](../output/DesignDoc_output.md))。
- CLI `depwalk analyze` の引数、exit code、エラー表示は決めない ([CLI feature doc](../cli/DesignDoc_cli.md) の対象)。
- 永続ストア、キャッシュ、並列探索、分散処理は扱わない。

## 設計

### データ構造 / コンテンツモデル

Traversal は起点 method ID、方向 (`caller` / `callee`)、深さ上限 (任意)、探索順序 (`bfs` / `dfs`、未指定時 `bfs`) を受け取る。返すのは到達 node 集合、到達 edge 集合、status、`cycle` 注釈、`depthLimit` cutoff である。

| 概念              | 主な field / 値                                                                                                                        | 備考                                                                                                                                                   |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Traversal request | 起点 method ID、方向、深さ上限 (任意、未指定時は無制限)、探索順序 (未指定時は `bfs`)                                                   | CLI 引数名は [CLI feature doc](../cli/DesignDoc_cli.md) が定める                                                                                       |
| Traversal result  | 到達 node 集合 (**node ごとの `minDepth` を保持**)、到達 edge 集合、status (`ok` / `startNotFound`)、`cycle` 注釈、`depthLimit` cutoff | Output Engine が consumer。tree は保持しない。到達 node / edge 集合は順序を保証しない                                                                  |
| Cycle 注釈        | 対象 edge の集合                                                                                                                       | 到達部分グラフ内で閉路を構成する edge (self-loop、または同一 SCC 内の edge)。**到達 edge 集合にも含まれる** (呼び出し関係として実在するため除外しない) |
| DepthLimit cutoff | 対象 edge の集合、接続先 node の minDepth                                                                                              | 到達 node から `minDepth > maxDepth` の node への edge。**到達 edge 集合には含まれない**。対象は edge のみで node 自体は cutoff 対象にしない           |

#### 到達集合の定義

結果は探索の副産物 (どの edge を辿ったか) ではなく、グラフの性質として定義する。これにより結果は探索順序 (BFS / DFS) に一切依存せず決定的になる。

- **minDepth**: 起点から探索方向に沿った最短距離。起点自身は 0。合流 (複数経路で同一 node へ到達するダイヤモンド型構造) がある場合、最短の距離を採る。**Traversal result は到達 node ごとの minDepth を公開する**。consumer の JSON 出力が利用する。公開する値は到達判定に使う内部値と同一であり、到達集合 / `cycle` / `depthLimit` の意味論には影響しない。
- **到達 node 集合**: `minDepth <= maxDepth` を満たす node (maxDepth 未指定時は全到達可能 node)。起点を含む。
- **到達 edge 集合**: 両端が到達 node 集合に属する、探索方向に沿った全 edge (誘導部分グラフ)。合流 edge も `cycle` 注釈付き edge も含む。
- **`maxDepth=0`**: 起点 node のみを到達集合に含み、起点の隣接 edge は `depthLimit` cutoff になる。ただし起点自身への self-loop は両端が到達 node のため、誘導 edge (+ `cycle` 注釈) として到達 edge 集合に残る (誘導部分グラフ定義からの帰結)。

呼び出しグラフでは、共有メソッドが複数箇所から呼ばれる合流構造が一般的である。探索木 edge 方式 (実際に辿った edge のみを結果に含める) では、この合流構造において BFS / DFS の選択によってどの edge が結果に残るかが変わってしまい、かつ循環していない合流 edge を誤って循環と標識してしまう。誘導部分グラフ + SCC 判定による定義は、この両方の問題を構造的に解消する。

訪問済み node 管理 (再展開の抑止) は無限ループ防止のための内部実装機構であり、結果契約には現れない。

#### 探索順序の位置づけ

探索順序 (`bfs` / `dfs`) は request が受け取るが、**探索の実行にも結果にも影響しない**。到達集合はグラフの性質として定義されており、その計算には最短距離の走査 1 本があれば足りるためである。順序の指定は値として妥当かどうかだけ検証し、不正なら error にする。

BFS / DFS の走査そのものは実装に存在するが、現時点ではどの本番経路からも呼ばれていない。将来 Console の tree 出力のように順序付き展開を要する consumer が現れたときのために残してある。

したがって「順序を変えても結果が同じ」は、2 通りの走査を比べた結果ではなく、**そもそも走査が 1 本しかないこと**から従う。

### 画面・デザイン

非該当。depwalk は CLI ツールであり、本 feature は Web UI / IDE Plugin を提供しない。

### コンポーネント構成 (C4 L3)

```mermaid
flowchart TD
    UseCase["Analyze Use Case"] --> Graph["Graph Engine<br/>(core/internal/graph)"]
    UseCase --> Traversal["Traversal Engine<br/>(core/internal/traversal)"]
    Traversal -->|"読み取り専用 API"| Graph
    Traversal --> Output["Output Engine<br/>(consumer, #6 では参照のみ)"]
```

Traversal Engine は `core/internal/traversal` に閉じ、Graph の node / edge 管理には関与しない。Graph が公開する読み取り API 経由でのみ探索する。Analyzer 固有情報や Java 固有 metadata を分岐条件にしない。

### フロー / シーケンス

処理は「到達 node 集合の確定」→「誘導 edge 集合と注釈の構築」の 2 段階で行う。段階 1 の訪問順序 (BFS / DFS) は結果に影響しない。

```mermaid
flowchart TD
    A["起点メソッドと探索方向を受け取る"] --> B{"起点は graph に存在するか"}
    B -- "No" --> C["空結果 + startNotFound status を返す"]
    B -- "Yes" --> D["段階1: 到達 node 集合の確定<br/>起点 (minDepth=0) から探索方向に minDepth を計算し<br/>minDepth <= maxDepth の node を到達 node 集合とする<br/>(maxDepth 未指定時は全到達可能 node。<br/>訪問済み node は再展開しない = 無限ループ防止。<br/>minDepth は最短距離であり、DFS option 指定時も<br/>BFS 相当の距離計算で正確に求める)"]
    D --> E["段階2: edge の分類<br/>到達 node の探索方向の全 edge を走査"]
    E --> F{"接続先 node は到達 node 集合に含まれるか<br/>(minDepth <= maxDepth か)"}
    F -- "No" --> G["depthLimit cutoff に記録<br/>(到達 edge 集合には含めない)"]
    F -- "Yes" --> H["到達 edge 集合へ追加 (誘導部分グラフ)"]
    H --> I{"edge は到達部分グラフ内で閉路を構成するか<br/>(self-loop または同一 SCC 内)"}
    I -- "Yes" --> J["cycle 注釈を付与<br/>(到達 edge 集合からは除外しない)"]
    I -- "No" --> K["注釈なし"]
    G --> L{"未走査の edge があるか"}
    J --> L
    K --> L
    L -- "Yes" --> E
    L -- "No" --> M["到達 node 集合 / 到達 edge 集合 /<br/>cycle 注釈 / depthLimit cutoff を返す"]
```

```mermaid
sequenceDiagram
    participant UseCase as Analyze Use Case
    participant Graph as Graph Engine
    participant Traversal as Traversal Engine
    participant Output as Output Engine

    UseCase->>Graph: methodSymbol / callEdge を登録
    UseCase->>Traversal: Traverse(graph, request)
    Traversal->>Graph: 起点 node を取得
    Graph-->>Traversal: node found / not found
    Traversal->>Graph: 方向に応じた隣接 edge を取得
    Graph-->>Traversal: caller / callee edges
    Traversal-->>UseCase: Traversal result
    UseCase->>Output: result を出力形式へ渡す
```

## 主要シナリオ / フロー

- 呼び出し側は起点メソッド、探索方向、深さ上限、探索順序を指定して Traversal Engine を呼び出す。探索順序を未指定にした場合は BFS、深さ上限を未指定にした場合は無制限として扱う。
- 起点メソッドが graph に存在しない場合、Traversal Engine は空の到達集合と `startNotFound` status を返す。Graph が空の場合も同様に `startNotFound` として扱う。
- Traversal Engine は caller または callee 方向に graph を辿り、到達 node 集合 (minDepth <= maxDepth の node)、到達 edge 集合 (到達 node 間の誘導 edge)、`cycle` 注釈 (閉路を構成する edge)、`depthLimit` cutoff を返す。
- Output Engine は Traversal 結果を受け取り、Console / JSON へ変換する。Console tree が必要な場合も、tree 構築は Output 側で行う (Traversal は tree 表現を保持しない)。

## テスト観点

横断規約は [context/testing.md](../../../context/testing.md)。本 feature 固有の観点を記す。

- caller / callee 方向で既知の呼び出し元 / 呼び出し先集合を返せること。
- 探索順序の走査そのものが、未指定時に BFS、`dfs` 指定時に DFS の訪問順を返すこと (本番経路からは呼ばれないため white-box test で検証する)。
- 不正な request (未知の探索方向、負の深さ上限、未知の探索順序) が探索を実行せず error になること。
- 自己再帰 / 相互再帰 (SCC) を含む graph で、閉路を構成する edge が `cycle` 注釈を持ちつつ到達 edge 集合にも含まれること。
- 合流 (ダイヤモンド型) graph で、同一 node への複数経路の edge がすべて到達 edge 集合に含まれ、`cycle` と誤標識されないこと。
- BFS / DFS のどちらを指定しても、到達 node / edge 集合・`cycle` 注釈・`depthLimit` cutoff の内容が同一であること (順序非依存性)。
- 深さ上限指定時に `minDepth <= maxDepth` の node を到達集合に含め、`minDepth > maxDepth` の node への edge を `depthLimit` cutoff として記録できること (`maxDepth=0` で起点への self-loop が誘導 edge + `cycle` 注釈として残る境界ケースを含む)。
- 両端が `maxDepth` ちょうどの到達 node を結ぶ frontier 間 cross edge (探索木の edge でないもの) が誘導 edge として到達 edge 集合に含まれること。
- 到達 node ごとに公開される `minDepth` が起点からの最短距離に一致すること (合流 graph で最短経路側の値を採ること)。
- 起点メソッドが存在しない場合、および Graph が空の場合に panic せず空結果 + `startNotFound` status を返すこと。
- Traversal が Analyzer 実装や Output format に依存しないこと。
