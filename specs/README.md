# Specs

`specs/` は issue 単位で要求・設計・テスト観点を整理する **作業文書** の置き場。
issue が close したら削除する (closeout)。長く残る判断は削除前に別の場所へ移す。

| 残すもの                   | 移す先                  |
| -------------------------- | ----------------------- |
| 選択肢を比較して決めた判断 | [adr/](../adr/)         |
| 現在の設計                 | [design/](../design/)   |
| 技術規約・運用契約         | [context/](../context/) |
| 変更の経緯                 | git history と PR       |

手順は `spec-lifecycle` skill の `references/closeout.md` が定める。
テンプレートは `templates/specs/template.md` (sdd-template から symlink で繋がる)。

現在 spec はない。#40 の spec は 2026-08-03 に closeout した。
