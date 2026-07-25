# Issue の status ラベル遷移と状態遷移コメント

設計対象 issue の進捗を issue 側からも追えるようにする規律。ラベル値と色の正本は
`context/project.yml` の `labels` (`status:*` 軸)。spec 内の `## 設計フェーズ状況` 表が
phase 単位の詳細、issue の `status:*` はその粗い要約という関係 (二重管理しない)。

## 遷移表 (常に 1 件。遷移は下記の節目でのみ行う)

| 遷移                                                | 節目 (実行主体)                                         |
| --------------------------------------------------- | ------------------------------------------------------- |
| (なし) → `status:designing`                         | 設計開始 = `spec-lifecycle` の scaffold 開始時          |
| `status:designing` → `status:ready-to-implement`    | 最終 gate (prompts) レビュー済 = 実装 prompt 生成完了時 |
| `status:ready-to-implement` → `status:implementing` | 実装セッション開始 = 最初の実装 prompt のステップ 0     |
| `status:implementing` → (close)                     | PR の `Closes #N` による自動 close (ラベル操作は不要)   |

- 遷移を飛ばさない (designing を経ずに ready-to-implement にしない)
- 既に目的の状態なら何もしない (冪等)

## 状態遷移コメント

ラベルを付け替えるたびに、issue へ次の形式でコメントを 1 件残す
(いつ・どの節目で・なぜ遷移したかを issue 上で追えるようにする):

```markdown
状態遷移: {旧 status → 新 status}

- 節目: {scaffold 開始 / 最終 gate レビュー済 / 実装開始}
- spec: specs/<issue-id>-<slug>/
- 補足: {レビュー結果や次の作業など 1 行。無ければ省略}
```

## 実行手順

1. `context/project.yml` の `tracker` (cli / project) を読む
2. 現在の `status:*` ラベルを確認する (無ければ初回遷移)
3. **承認の取り方に従う**: ラベル変更 + コメント投稿は副作用のある操作なので、
   実行前に 3 択 (進める / 修正してから進める / 進めない) で承認を取る。
   `spec-lifecycle` の phase 完了報告と同時に承認を取ってよい (2 度聞きしない)
4. ラベル付け替えとコメント投稿を 1 セットで実行する (片方だけにしない)

## 停止条件

- 対象 issue が特定できない (spec のメタ情報に issue 番号が無い)
- `status:*` ラベルが tracker 側に未作成で、作成の承認が得られていない (`gh label create` を先に提案)
- 遷移が遷移表に無いパターンになる (例: designing → implementing の飛ばし)
