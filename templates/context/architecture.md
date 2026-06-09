# Codebase Architecture

> 最終更新: YYYY-MM-DD

コードベースの **package / runtime / state boundary と依存方向**。全体像 (system landscape, モジュール責務) は [design/DesignDoc.md](../design/DesignDoc.md) を正本とし、本書は境界規約を扱う。プロジェクト固有の構成は [context/project.md](project.md) を参照する。

## Package Boundary

- モジュール間の依存方向を定める (どこからどこへ依存してよいか / 禁止する経路)。
- 共有コードの昇格条件 (いつローカルから共有 package へ移すか)。
- 循環依存・未宣言依存の扱い ([engineering.md](engineering.md) の quality gate で検査)。

## Runtime Boundary

- runtime / 配信モデルの前提 (静的 / サーバ / edge 等)。
- build-time と runtime の env 分離方針。
- 秘密情報を client へ露出させない原則 ([infrastructure.md](infrastructure.md))。

## State Boundary

- server state / client state / URL state の分離方針 (該当する場合)。
