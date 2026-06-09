# Testing Conventions

> 最終更新: YYYY-MM-DD

テストの横断規約。feature 固有のテスト観点は各 [design/features/](../design/features/) に置く。プロジェクト固有のテストコマンドは [context/project.md](project.md)。

## テスト責務の分担

| 種別 | 配置 | 主担当範囲 |
| ---- | ---- | ---------- |
| Unit test |  |  |
| E2E |  |  |

## テスト runtime contract

- E2E / 統合テストの起動契約 (env 変数 / 対象選択 / port 等)。新しい対象を追加する手順を含める。

## 横断テスト方針

- 公開 / リリース判定に含めるテスト観点。
