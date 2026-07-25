# ADR

`adr/` は本プロジェクトの恒久的な意思決定ログを保存する場所である。仕様検討の途中メモや一時的な比較は `specs/` に置き、長期参照価値のある判断だけを ADR に昇格する。

## 命名規約

- ファイル名: `adr/NNNN-<title>.md`
- 例:
  - `adr/0001-<topic>.md`
  - `adr/0002-<topic>.md`

## ADR にする判断

- 技術選定や rendering / runtime 方針を固定した。
- モジュール / package の責務境界を固定した。
- 共有方針 (UI / config / 共通基盤) を固定した。
- 将来拡張 (新 app / API / 認証など) の採否を決めた。
- 代替案比較を経て、採用方針を明示的に残す必要がある。

テンプレートは [templates/adr/template.md](../templates/adr/template.md) を `adr/NNNN-<title>.md` にコピーして使う。
