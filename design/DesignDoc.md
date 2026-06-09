# Design Doc (未生成プレースホルダ)

> このファイルは `design-doc` skill が [templates/design-doc/template.md](../templates/design-doc/template.md) から生成・更新する。

本リポジトリはまだプロダクト未確定のため、Design Doc 本体は未生成です。新規プロダクトへ適用するときは:

1. `design-doc` skill を起動し、PRD 要否 (分離 / 統合モード) を判定する。
2. テンプレートから本ファイル (`design/DesignDoc.md`) を生成し、概要 → Goal → アーキテクチャ概観 (C4 L1/L2) → モジュール責務 → 委譲先の順に埋める。
3. feature 単位の詳細は [design/features/](features/)、横断規約は [context/](../context/)、個別判断は [adr/](../adr/) へ委譲する。

文書品質基準は `technical-writing` skill、ドキュメント階層は [README.md](../README.md) を参照。
