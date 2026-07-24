# Review Routing — コード差分で回す観点の選択

コード差分レビューの観点の正本は観点別 subagent 定義 (`.rulesync/subagents/review-*.md`)。
本表は「どの観点をどの差分で回すか」だけを決める (観点の中身をここに再記述しない)。
小さな差分で全観点を回さない (token 節約)。ユーザーが `security,quality` 等で観点を指定したら本表より優先する。

## ルーティング表

| 観点 id        | subagent              | 対象差分シグナル (変更パス・内容が次に合致したら回す)                                      |
| -------------- | --------------------- | ------------------------------------------------------------------------------------------ |
| `quality`      | `review-quality`      | **常時** (すべてのコード差分)                                                              |
| `security`     | `review-security`     | 認証 / 認可 / 入力処理 / 外部 IO / API endpoint / 設定・env・secret 系ファイルに触れる差分 |
| `architecture` | `review-architecture` | 新規ディレクトリ・module の追加、import / 依存関係の変更、複数 module にまたがる差分       |
| `performance`  | `review-performance`  | ループ / クエリ / 一括処理 / キャッシュ / 大きなデータ構造に触れる差分                     |

- 判定は差分の変更パスと内容から行い、迷ったら回す側に倒す (見逃しより過剰実行を許容)。
- spec / 文書対象には本表を使わない (観点の正本は `spec-reviewer` subagent)。

## 観点の注入と実行形態

選んだ各観点について、実行形態ごとに次のとおり観点を解決する:

- **外部 CLI エージェント** (`agent-orchestrate` 経由): subagent を spawn できないため、
  該当 `review-*.md` の「検証観点」節のみをプロンプトの rubric としてインライン注入する (レビュー姿勢は雛形の固定前文が担う。二重注入しない)
  (`review-prompt.md` の雛形に埋める)。定義ファイルが読めない場合のみ同雛形のフォールバック rubric を使う。
- **Claude subagent** (degrade 時): `review-*` subagent に Task で直接委譲する
  (差分ファイル path を渡す。複数観点は並列起動)。

## degrade 規則 (孤立しても壊れない)

1. 外部 CLI が 2 台以上 → 従来どおり `agent-orchestrate` で並列 (観点はインライン注入)
2. 外部 CLI が 1 台 → その 1 台で実行 (クロスチェックにならない旨をレポートに注記)
3. 外部 CLI が 0 台 → **Claude の `review-*` subagent に degrade** し、観点別レビューだけは必ず回す
4. subagent 定義も見つからない → フォールバック rubric で 1 回レビューし、その旨を注記
