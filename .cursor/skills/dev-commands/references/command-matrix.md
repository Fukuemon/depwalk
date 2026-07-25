# Command Matrix

コマンドの **分類と用途**。具体的なコマンド文字列は [context/project.yml](../../../../context/project.yml) の `commands` を正本とし、ここでは「どの分類をいつ使うか」を解説する。

## Contents

- Development
- Build
- Quality (lint / format / typecheck)
- Test
- Analysis
- Dependency management

## Development

- 開発サーバの起動。全体起動と特定 module 起動を使い分ける (monorepo の場合)。

## Build

- 公開物の生成。全体ビルドと特定 module ビルドを使い分ける。

## Quality

- lint / typecheck / format。format は破壊的な書き込み系と非破壊の確認系を区別し、レビュー前は確認系を使う。

## Test

- unit test と e2e。e2e の env は `e2e-runtime.md` を参照。

## Analysis

- dead code 検出・依存境界検査など、プロジェクトが持つ健全性検査。pre-commit hook で自動実行される場合がある。

## Dependency management

- 依存追加は対象 module のスコープを明示する (`filter-scope.md`)。repo-wide な toolchain / devDependency を除き、ルートへ無差別に追加しない。
