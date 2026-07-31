.PHONY: generate check-specs

generate: ## rulesync 生成と provider 別の正規化をまとめて実行
	@bash scripts/generate.sh

check-specs: ## closed issue の spec 残存を検査 (closeout 契約の機械検査。gh 認証が必要)
	@bash scripts/check-specs-residue.sh
