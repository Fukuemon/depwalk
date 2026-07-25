# ファイル分割 (package 内のファイル構成)

目次: 判断基準 / 5 つの標準パターン / 構成例 / テストファイル / やってよい分割・悪い分割

## 判断基準

- **巨大な単一ファイル (数千行) も、極小ファイルの乱立も避ける**。基準は「保守者がどのファイルに何があるかを判断でき、すぐ見つけられること」(Google Go Style Guide — Best Practices: Package size)
- 分割は機能・関連コードのまとまり単位。参考例は標準ライブラリ `bytes` パッケージ
- 1〜2 行のためにファイルを作らない。ファイル数を目的にしない

公式が定めるのはこの基準までで、ファイル構成の形は定めていない。以下の 5 パターンは
この基準を満たすために本規約が定める標準形 (本規約独自)。機械的な適用が
「見つけやすさ」と衝突する場合は、パターンでなく基準に従う。

## 5 つの標準パターン

### 1. `<packagename>.go` — パッケージの入口

パッケージの公開 API と主たる入口を置く。初見の読者がこのファイルだけで「どう使うか」を掴めるようにする。

- コンストラクタ (`New` / `NewXxx`)
- 軽量パッケージなら stateless な公開関数・型定義・定数もここでよい

```go
func New(config Config) (*Service, error) { /* 初期化 */ }
```

### 2. `types.go` — 複数ファイルから参照される共有型

複数ファイルにまたがって使う型・定数だけを置く。

```go
type Status int

const (
    StatusActive Status = iota
    StatusInactive
)
```

> **注意**: 公式は「内容でなく種類で切る」構成に否定的で、`types` / `interfaces` は
> パッケージ名としては明示的に禁忌 (CodeReviewComments — Package Names)。`types.go` を
> 「型なら何でも入れる置き場」にすると同じ問題を起こす。**単一ファイルでしか使わない型はそのファイルに置き**、
> ここには本当に共有される契約だけを残す。共有型が 1 つの機能に偏ってきたら、機能名のファイルへ移す。

### 3. `<interfacename>.go` — interface とその実装

interface 定義と、その実装・実装専用の型を同じファイルに置く (大きければ実装は分離してよい)。
ファイル名は interface 名の小文字 (例: `Logger` → `logger.go`)。契約と実装が近くにあり、探し回らずに済む。

```go
type Logger interface {
    Log(msg string) error
}

type loggerImpl struct{ writer io.Writer }

func (l *loggerImpl) Log(msg string) error { /* 実装 */ }
```

### 4. `<funcname>.go` — 関心事ごとの関数・メソッド

関数を目的・関心事で束ねる。ファイル名は役割を表す (例: `fetcher.go` / `validator.go` / `processor.go`)。
そのファイルでしか使わない型・定数はここに置く (types.go に送らない)。

### 5. `<struct>_<concern>.go` — struct 別のメソッドファイル (最後の手段)

まず前提: **1 つの struct のメソッドは 1 ファイルにまとめるのが原則** (パターン 3 / 4)。
1 struct のメソッドが複数ファイルに割れるほど大きいのは、struct の責務過多のサインであることが多く、
ファイル分割の前に責務分離 (`responsibility-separation.md` のパッケージ分割ルール) を先に検討する。

それでも割る場合 — 複数の struct がそれぞれ複数のメソッド群を持ち、`fetch.go` のような名前が
どの struct のものか曖昧になるとき — は、アンダースコア区切りで struct にスコープする
(例: `repository_fetch.go` / `cache_get.go`)。複数語のファイル名にアンダースコアを使うのは
Go エコシステムの実勢 (Kubernetes / golang/tools 等) と一致する。

> **命名の罠 (公式仕様)**: ファイル名の末尾要素が `GOOS` / `GOARCH` (`_linux` / `_arm64` /
> `_windows` 等) に一致すると build constraint として解釈され、`_test` も特別扱いされる。
> struct 名・関心事名がこれらに一致するファイル名は避ける (例: `runner_linux.go` は不可)。

## 構成例

単一 struct / 名前が曖昧にならない場合 — パターン 4 まで:

```text
cms/
├── cms.go           # New と軽量な公開関数
├── types.go         # 共有型 (Config, Status 等)
├── repository.go    # Repository interface と実装
├── fetcher.go       # 取得系の関数
└── validator.go     # 検証系の関数
```

複数 struct × 複数メソッド群 — パターン 5 を併用:

```text
store/
├── store.go               # New と軽量な公開関数
├── types.go               # 共有型 (Record, Config 等)
├── repository.go          # Repository interface 定義
├── cache.go               # Cache interface 定義
├── repository_fetch.go    # Repository の取得系メソッド
├── repository_update.go   # Repository の更新・削除系メソッド
├── cache_get.go           # Cache の取得系メソッド
└── cache_set.go           # Cache の設定・失効系メソッド
```

読者の導線: 「使い方は?」→ 入口ファイル / 「データ構造は?」→ types.go / 「Repository の取得は?」→ repository_fetch.go。

## テストファイル (公式)

- テストは同一ディレクトリの `foo_test.go` に置く
- **exported API だけを検証する black-box テストは `package foo_test`** にする (Google Go Style Guide — Decisions: Test packages)。unexported の検証が必要なときだけ同一パッケージでテストする

## やってよい分割・悪い分割

**よい分割**: interface が自分のファイルを持つ / 共有型が types.go にある / 関数が関心事で束なっている / コンストラクタが入口にある

**悪い分割 (判断基準への違反)**:

- 1〜2 行のためのファイル作成、ファイル数を目的化する
- 関連する関数を多数のファイルへ散らす / 型を見つけにくいファイルに隠す
- 明確な理由なく 1 つの関心事を複数ファイルに割る
- `types.go` を種類別の置き場 (dumping ground) にする
