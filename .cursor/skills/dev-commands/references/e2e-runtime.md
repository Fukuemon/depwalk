# E2E Runtime

E2E の env contract と対象側 script contract。プロジェクトが E2E を持つ場合に参照する。具体的な対象名・port・コマンドは [context/project.yml](../../../../context/project.yml) を正本とする。

## env contract の考え方

- 対象 (どの module を検証するか)、port、base URL を env で切り替えられるようにする。
- 既定の対象と port を `context/project.yml` の `commands` / テスト節に定義し、対象切替時のみ上書きする。

## 対象側 script 契約

E2E 対象 module は、テストランナーが依存する最小の起動契約 (例: build → 配信 → 起動エントリ) を持つ。ランナー側の root config は module 固有の起動コマンドを直書きせず、共通の起動エントリだけを呼ぶ。

## アンチパターン

- root config に module 固有の起動コマンドを直書き → 対象切替で破綻
- 新しい対象を追加する際に port を割り当てず衝突させる
- 対象側の起動契約を欠いたまま E2E 対象に加える
