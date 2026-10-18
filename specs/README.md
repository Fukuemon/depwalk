# Specs

`specs/` は、issue ごとの要求、設計、確かめ方を置く **作業文書** の置き場である。
spec は `specs/<issue 番号>-<短い主題>/` に置き、issue を閉じるときに削除する（closeout）。長く残す内容は、削除の前に次の場所へ移す。

| 残すもの                   | 移す先                                                                       |
| -------------------------- | ---------------------------------------------------------------------------- |
| 選択肢を比較して決めた判断 | [adr/](../adr/)                                                              |
| 現在の設計                 | Design Doc。全体は [design/](../design/)、機能ごとはコードのディレクトリの隣 |
| 技術規約・運用契約         | [context/](../context/)                                                      |
| 変更の経緯                 | git history と PR                                                            |

spec に書くことと closeout の手順は、agent-harness のスキル write-design-docs の `references/spec.md` が定める。

## closeout を確かめる仕組み

- `.github/workflows/spec-closeout.yml` は、issue を閉じたときに spec が残っていれば、spec を削除する pull request を作る。削除してよいかは、その pull request をマージするかどうかで人が決める。
- `.github/workflows/spec-residue.yml` は、閉じた issue の spec が残っていないかを、`scripts/check-specs-residue.sh` で確かめる。既定のブランチへの push と、毎週月曜の定期実行で動く。

`spec-closeout.yml` を動かすには、リポジトリに次の設定が要る。

- Settings → Actions → General で、「Allow GitHub Actions to create and approve pull requests」を有効にする。
- secret `CLOSEOUT_PR_TOKEN` に、`contents:write` と `pull-requests:write` を持つ PAT を置く。任意である。置かないと、既定の `github.token` で作った pull request で CI が動かない。

現在 spec はない。
