# ADR-0003: Analyzer 起動コマンドを言語非依存な文字列として解決する

## 状態

承認

## 決定日

2026-07-11

## 背景

ADR-0001 で Core と Analyzer を JSONL over STDIN/STDOUT の process SPI で結合すると決めたが、Core 側には「どの Analyzer をどう起動するか」を決める配線がまだ無かった (`core/internal/cli/root.go` に analyze command は無く、`core/internal/analyzer/runner.go` は起動コマンドを呼び出し側から受け取るだけ)。Java Analyzer を初号機として Core から実行するには、この起動コマンド解決の配線が必要になる。

Design Doc の成功条件 S5 は「**2 つ目以降**の言語 Analyzer 追加時に Core モジュールへ差分が発生しないこと」を求める。初号機 (Java) 導入に伴う言語非依存な初回配線自体は S5 の対象外だが、この初回配線を言語固有の作り (例: `java` コマンドや jar path を Core に埋め込む) にしてしまうと、2 つ目以降の Analyzer 追加時に Core への分岐追加が避けられなくなる。したがって初回配線の設計そのものが S5 の担保方法を左右する。

## 決定

Analyzer 起動コマンドを、Core が意味を解釈しない **言語非依存な文字列**として解決する。

- 解決順序: ① CLI flag `--analyzer-cmd` (例: `"java -jar analyzers/java.jar"`) → ② 環境変数 `DEPWALK_ANALYZER_CMD` → ③ どちらも無ければ実行前に validation error で拒否する。
- Core は解決した文字列を **shell を介さず shell-word 分割して exec する** (shell injection を避ける)。Core は `java` / jar / JVM の存在を知らない。
- metadata passthrough (`--analyzer-meta key=value`) も同様の原則に従う。Core は `analysisRequest.metadata` へ素通しするだけで、key / value の意味 (例: Java の `classpath`) を解釈しない。
- 規約 path による既定解決 (binary の隣を探す等) は Phase1 では導入しない。必要になった時点で ③ の前段として追加できる形にしておく。

具体名 (`--analyzer-cmd` / `DEPWALK_ANALYZER_CMD` / `--analyzer-meta`) と metadata 合成規則は [Java Analyzer feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md) を正本とする。決定経緯は [spec #9](../specs/9-java-analyzer/) に残す。

## 代替案

- 規約 path (例: `analyzers/<language>/build/libs/analyzer.jar` を Core が既定で探す)。
  - 却下理由: 言語ごとに build 成果物の path 規約が異なり、Core が言語別の path 規則を知ることになる。将来の Analyzer 追加のたびに Core 側の探索規則が増える。
- Core に Analyzer を go:embed 等で同梱する。
  - 却下理由: Core が JVM を含む言語ランタイムやビルド成果物を抱えることになり、Core を言語非依存に保つ設計原則 (P1-P3) と Runtime Boundary (別プロセス) に反する。Core の配布サイズと release 境界も言語ごとに膨らむ。
- 言語別 flag (例: `--java-analyzer-cmd`, `--kotlin-analyzer-cmd`)。
  - 却下理由: Analyzer を追加するたびに Core の CLI 定義へ新しい flag を追加する必要があり、S5 (2 つ目以降の Analyzer 追加時に Core 無変更) に直接反する。

## 影響

### 良い影響

- Core は起動対象の言語ランタイムを知らずに済み、2 つ目以降の Analyzer 追加時に Core モジュールへ差分が発生しない。
- fake analyzer (任意の実行可能ファイル) に差し替えられるため、JVM を持たない環境でも Core 側の unit / contract test が回る。
- 起動コマンドと metadata の両方が文字列 / passthrough で扱われるため、Core の CLI 定義が言語追加に対して安定する。

### 悪い影響 / トレードオフ

- 利用者は Analyzer 起動コマンドを毎回明示 (flag または環境変数) する必要があり、規約 path による自動発見のような利便性は Phase1 では提供しない。
- shell-word 分割の実装 (quote 処理等) を Core 側で持つ必要がある。

### 影響範囲

- 対象モジュール / package: `core`, `java-analyzer`

## 実装・運用への反映

- spec 更新要否: 要。spec #9 の durable 成果を feature doc / ADR 正本へハンドオフし、spec 側は決定時スナップショットへ降格する。
- context / AI 向け設定更新要否: 要。`context/project.md` の Quick Commands (開発起動 / E2E) を本決定に沿って更新する。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): 成功条件 S5、設計原則 P1-P4
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): 起動契約 / metadata 契約の具体名の正本
- [adr/0001-analyzer-protocol-jsonl-spi.md](0001-analyzer-protocol-jsonl-spi.md): JSONL over STDIN/STDOUT の process SPI
- [specs/9-java-analyzer](../specs/9-java-analyzer/): 決定経緯と issue 単位の作業記録
