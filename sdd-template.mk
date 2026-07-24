.PHONY: generate

generate: ## rulesync 生成と provider 別の正規化をまとめて実行
	@bash scripts/generate.sh
