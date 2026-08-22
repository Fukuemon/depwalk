# ADR

`adr/` は本プロジェクトの恒久的な意思決定ログを保存する場所である。仕様検討の途中メモや一時的な比較は `specs/` に置き、長期参照価値のある判断だけを ADR に昇格する。

## 命名規約

- ファイル名: `adr/NNNN-<title>.md`
- 例:
  - `adr/0001-<topic>.md`
  - `adr/0002-<topic>.md`

## ADR にする判断

**本プロダクト (depwalk) の技術判断だけを置く。**

- 技術選定や rendering / runtime 方針を固定した。
- モジュール / package の責務境界を固定した。
- 共有方針 (UI / config / 共通基盤) を固定した。
- 将来拡張 (新 app / API / 認証など) の採否を決めた。
- 代替案比較を経て、採用方針を明示的に残す必要がある。

## ADR にしない判断

SDD の仕組み・文書構造・表現に関する判断は、本リポジトリに置かない。開発プロセスの資産は sdd-template リポジトリが定めて全消費リポジトリへ配るため、判断を消費側に持つと配布元と食い違う。

対象は次のようなものである。

- 文書のメタ情報・鮮度・索引の仕組み (frontmatter / `governs` / `verified_commit` / 読み取りマップ)
- 文書のレイアウトと分割の方針、文体・用語・参照の書き方
- 文書に対する機械検査 (リンク検査 / 一文の長さ / 生成物の drift) を入れるかどうかの判断
- skill / rule / subagent の設計

これらは sdd-template の `decisions.md` へ書く。本リポジトリ側に残すのは、その決定を**この repo でどう運用するか**だけであり、置き場は `context/` である。

- [context/README.md](../context/README.md) の 文書メタ情報と鮮度: frontmatter の schema と鮮度検査の運用
- [context/engineering.md](../context/engineering.md) の Repository Quality Gate: 文書検査の実行点と強度

## 番号

`0001` から欠番なく連番で振る。文書運用の判断を扱っていた旧 `0008` / `0009` / `0011` を sdd-template へ移したとき、残る 2 本を繰り上げて連番へ戻した。

| 旧番号 | 新番号 | ADR                                          |
| ------ | ------ | -------------------------------------------- |
| 0010   | 0008   | 可視化出力をスコープから外す                 |
| 0012   | 0009   | framework 由来の暗黙呼び出し解決と型伝播救済 |

**2026-08-22 より前の commit message / PR / issue に現れる ADR 番号は、この繰り上げ前のものである。** 当時の本文は git history から辿れる。

テンプレートは `templates/adr/template.md` を `adr/NNNN-<title>.md` にコピーして使う。`templates/` は sdd-template から symlink で繋がっており本 repo では追跡しない (未接続なら `bash scripts/doctor.sh`)。
