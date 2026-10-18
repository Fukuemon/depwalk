# depwalk

depwalk は、メソッドの呼び出し元（caller）と呼び出し先（callee）を静的解析で求める CLI である。メソッドを直す前の影響範囲の調査に使い、手元でも CI でも同じように実行できる。

- 対象は Java と Spring Boot である。interface を経由する呼び出しは、Spring の DI を解いて実装のメソッドまで辿る。
- 結果は Console か JSON で出力する。

> **Status:** Java と Spring Boot 向けの中核の機能まで実装した。v1 はまだリリースしていない。

## 必要なもの

- [mise](https://mise.jdx.dev/)。Go と JDK のバージョンは `mise.toml` にある。Java Analyzer は JDK 25 で動く。

```sh
mise install
```

## 使い方

コマンドはリポジトリのルートで実行する。

1. Java Analyzer の jar と CLI をビルドする。

   ```sh
   (cd analyzers/java && ./gradlew shadowJar)
   (cd core && go build -o ../dist/depwalk ./cmd/depwalk)
   ```

2. 解析する workspace と、起点のメソッドを指定して実行する。

   ```sh
   ./dist/depwalk analyze <workspace-root> \
     --language java \
     --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" \
     --method "com.example.UserService#findById(java.lang.Long)" \
     --direction caller \
     --format json
   ```

主な flag は次のとおり。全体は `./dist/depwalk analyze --help` で確かめる。

| flag             | 意味                                                                    |
| ---------------- | ----------------------------------------------------------------------- |
| `--method`       | 起点のメソッド。`<binary-type>#<method>[(<argument-types>)]` の形で書く |
| `--direction`    | `caller`（既定）か `callee`                                             |
| `--max-depth`    | 辿る深さの上限。既定は上限なし                                          |
| `--format`       | `console`（既定）か `json`                                              |
| `--source-root`  | 解析する source root。繰り返して指定できる                              |
| `--analyzer-cmd` | Analyzer の起動のコマンド。環境変数 `DEPWALK_ANALYZER_CMD` でも渡せる   |

`--source-root` を省くと、Java Analyzer は Gradle の build model から source root と classpath を取得する。このとき、解析する workspace の build のロジックが動き、ネットワークへのアクセスと Gradle の cache の更新が起きることがある。`--source-root` を指定すると、build model を使わない。このときは、classpath と Java の言語のレベルを `--analyzer-meta classpath=<path>` と `--analyzer-meta javaLanguageLevel=<version>` で渡す。classpath がないときは `--analyzer-meta classpath=` と空の値を渡す。

## 文書

- [PRD.md](PRD.md): depwalk が解く課題、目標、作らないもの
- [design/DesignDoc.md](design/DesignDoc.md): 全体の構成とモジュールの責務
- [context/index.md](context/index.md): 規約と設計の文書の目次

## 開発に参加する

開発の準備、文書のチェック、ブランチ、issue と pull request の進め方は [CONTRIBUTING.md](CONTRIBUTING.md) にある。
