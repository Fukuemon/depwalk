# ADR-0003: Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する

## Status

承認

## Context

Core と Analyzer は JSONL over STDIN/STDOUT の process SPI で結合すると決めてある。  
ADR-0001: [Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する](0001-analyzer-protocol-jsonl-spi.md)

Core 側には、どの Analyzer をどう起動するかを決める配線が無かった。  
Analyzer process の runner は、起動コマンドを呼び出し側から受け取るだけだった。  
Java Analyzer を最初の Analyzer として Core から実行するには、起動コマンドを解決する配線が必要になる。

新しい言語の Analyzer を追加するとき、**2 つ目以降**では Core のモジュールへ差分が発生しないことを、成功条件「新しい言語の Analyzer を追加するとき Core を変更せずに済む」としている。  
Design Doc: [design/DesignDoc.md](../design/DesignDoc.md)

最初の Analyzer (Java) の導入に伴う言語非依存な初回の配線そのものは、この成功条件の対象外である。  
ただし初回の配線を言語固有の作り (例: `java` コマンドや jar の path を Core へ埋め込む) にすると、2 つ目以降の Analyzer の追加時に Core への分岐の追加を避けられなくなる。  
したがって初回の配線の設計が、この成功条件を保証できるかを左右する。

## Decision

Analyzer の起動コマンドを、Core が意味を解釈しない**言語非依存な文字列**として解決する。

- 解決の順序は 3 段とする。
  - 1 段目は CLI flag `--analyzer-cmd` (例: `"java -jar analyzers/java.jar"`)、2 段目は環境変数 `DEPWALK_ANALYZER_CMD` とする。
  - どちらも無ければ、実行前に validation error で拒否する。
- Core は解決した文字列を、shell を介さず shell-word 分割して exec する。
  - shell injection を避けるためである。
  - Core は `java` / jar / JVM の存在を知らない。
- metadata passthrough (`--analyzer-meta key=value`) も同じ原則に従う。
  - Core は `analysisRequest.metadata` へ素通しするだけで、key と value の意味 (例: Java の `classpath`) を解釈しない。
- 規約の path による既定の解決 (binary の隣を探すなど) は Phase1 では導入しない。
  - 必要になった時点で、validation error で拒否する段の前段として追加できる形にしておく。
- shell-word 分割では、変数展開、glob 展開、コマンド置換を一切行わない。
- 起動コマンドの解決は use case ではなくコンポジションルートの責務とする。

flag と環境変数の具体名と、metadata の合成規則は、この ADR では定めない。  
起動契約と metadata 契約の具体名は、Java Analyzer の Design Doc が持つ。  
Java Analyzer: [analyzers/java/DesignDoc.md](../analyzers/java/DesignDoc.md)

## Considered Options

| 選択肢                                                                                                       | 強み                                                                                                                | 弱み                                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** 起動コマンドを言語非依存な文字列として flag、環境変数の順に解決する                                | Core は起動対象の言語ランタイムを知らずに済む。fake analyzer へ差し替えられ、JVM の無い環境でも Core の test が回る | 利用者は起動コマンドを毎回 flag か環境変数で明示する必要がある。shell-word 分割の実装を Core が持つ                                                 |
| 規約の path で解決する (例: `analyzers/<language>/build/libs/analyzer.jar` を、指定が無いときに Core が探す) | —                                                                                                                   | 言語ごとに build 成果物の path 規約が異なり、Core が言語別の path 規則を知ることになる。Analyzer を追加するたびに、Core 側の探索規則が増える        |
| Core へ Analyzer を go:embed などで同梱する                                                                  | —                                                                                                                   | Core が JVM を含む言語ランタイムやビルド成果物を抱え、Core を言語非依存に保つ設計原則に反する。Core の配布サイズと release 境界も、言語ごとに膨らむ |
| 言語別の flag を用意する (例: `--java-analyzer-cmd`、`--kotlin-analyzer-cmd`)                                | —                                                                                                                   | Analyzer を追加するたびに Core の CLI 定義へ新しい flag を追加する必要があり、Analyzer の追加で Core を変更しない成功条件に直接反する               |

## Consequences

### Positive

- Core は起動対象の言語ランタイムを知らずに済み、2 つ目以降の Analyzer の追加時に Core のモジュールへ差分が発生しない。
- fake analyzer (任意の実行可能ファイル) へ差し替えられるため、JVM を持たない環境でも Core 側の unit / contract test が回る。
- 起動コマンドと metadata の両方を文字列と passthrough で扱うため、Core の CLI 定義が言語の追加に対して安定する。

### Negative

- 利用者は Analyzer の起動コマンドを毎回 flag または環境変数で明示する必要がある。
  - 規約の path による自動発見のような利便性は、Phase1 では提供しない。
- shell-word 分割の実装 (quote の処理など) を、Core 側で持つ必要がある。
