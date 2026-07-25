# Toolchain

> 最終更新: YYYY-MM-DD

採用する標準 toolchain。採否の根拠は [adr/](../adr/) を参照する。プロジェクト固有のコマンドは [context/project.md](project.md) の Quick Commands を正本とする。

## 標準スタック

| 区分 | ツール | 備考 |
| ---- | ------ | ---- |
| Package manager |  |  |
| Task runner |  |  |
| Language |  |  |
| Linter |  |  |
| Formatter |  |  |
| Unit test |  |  |
| E2E |  |  |

## 採用方針

- 採用候補を先行固定する場合は、その根拠と確定タイミング (どの issue / ADR で確定するか) を記す。

## Scaffold Policy

- 新規モジュールの初期 scaffold 手順 (公式 create command を優先する等)。
- 生成後にプロジェクトの contract (命名 / root scripts / 共有 config) へ寄せる手順。
