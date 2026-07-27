# Skill 共通契約

skill / rule / subagent を **書くときの契約**。skill を新設・変更するとき、`rulesync-sync` / `skill-feedback` から読む。

機械検査 (`make check` = `scripts/check-skills.sh` + 生成物 drift 検査) は **sdd-template repo 側で実行する**。
消費 repo には `Makefile` / `scripts/check-skills.sh` が配布されないため、消費 repo で `make check` は成立しない
(消費 repo での検証は `make -f sdd-template.mk generate` → 生成物の差分確認まで)。

参照するときのパス: `rulesync-sync` skill の `references/skill-contract.md`
(Claude Code では `.claude/skills/rulesync-sync/references/skill-contract.md`)。

## 適用範囲

- `.rulesync/skills/` (全プロジェクト共通) と選択導入型 `skills/` の **両方**に適用する。外部から移植した skill も取り込み時に本契約へ書き直す
- skill 内に project 固有名 (path / CLI / framework) を直書きしない (`context/project.yml` と各契約を読ませる)

## 構成

- SKILL.md 本体は **200 行未満**、詳細は `references/<topic>.md` へ 1 階層深さで分離。100 行を超える reference は冒頭に目次を置く
- 必須セクション (正規名のみ使用): `いつ使うか` / `先に読むもの` / `実行フロー` / `停止条件`。`実行手順` / `生成手順` / `終了条件` 等の同義異名を使わない
- 任意セクション: `入力` / `中核原則` / `禁止事項` 等は追加してよい。手順の全体像は ASCII 図でなく `実行フロー` 内の番号付きステップ (複雑なら冒頭にコピー可能なチェックリスト) で示し、`ワークフロー` 節を重複して置かない
- **例外**: 品質基準のみを提供する reference 型 skill (例: `styleguide-documents` / `effective-go`) は `先に読むもの` / `実行フロー` / `停止条件` を省略してよい

## description / frontmatter

- `name` は kebab-case、`anthropic` / `claude` を含めない (Anthropic 規約)
- description は日本語・third-person で「何をする + いつ起動」を含め、発動トリガー語を引用符で列挙する (1024 字以内)。`いつ使うか` は description の言い換えにせず、追加のトリガー語 / 文脈のみ書く
- `targets: ["*"]` を frontmatter に置く

## 文書を書く skill

- 文書を書く skill は `styleguide-documents` skill を「先に読むもの」で参照する
- 読み物として読ませる文章 (記事 / 解説 / 章立ての文章) を生成・推敲するときは `styleguide-documents` の `references/prose-rhythm.md` (緩急・認知リズムの規範) を **必ず読む** (spec / ADR / context 等の技術文書には適用しない)

## 重複と記録

- 同じ規範を複数ファイルに再掲しない。正本を 1 箇所に置き、他は 1 行で参照する
- 常時ロードされる root rule には規範の本文を置かず、ポインタだけを置く (本文はオンデマンド Read。理由は `decisions.md`)
- skill の連鎖挙動 (呼び出し関係 / phase gate / 状態遷移) を変えたら、同 PR で `architecture.md` のシーケンスを更新する
- 非自明な著作判断 (token コスト / ツール制約 / スコープの線引き) は `decisions.md` に 1 判断 1 セクション (`背景 / 判断 / 理由 / 今後`) で記録し、逆戻しの前に読む
- `architecture.md` / `decisions.md` は **sdd-template repo の文書**。消費 repo には配布されないため、更新は
  正本 (`.rulesync/`) を持つ sdd-template repo 側で行う (消費 repo で symlink 経由に編集した場合も同じ)
