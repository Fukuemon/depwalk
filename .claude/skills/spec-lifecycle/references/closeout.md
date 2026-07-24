# Closeout: issue close 後に spec を削除する

spec は **issue 単位の作業文書** であり、issue が閉じたら役目を終える。残しておくと design 側に
spec への索引が積もって認知負荷が上がり、現行実装と食い違った古い spec がドリフトの温床になる。
durable な情報は sync で design / ADR / context に救出済みであることを確認した上で、spec dir を削除する。
削除後も経緯は issue / PR / git history (`git log --all -- 'specs/<issue-id>-*'`) で追える。

## 実行タイミング

実装 PR の merge で issue が close した後 (`workflow-git` の PR workflow から案内される)。

## 前提条件 (すべて満たすまで削除しない)

- [ ] sync 完了: spec の `## 上位資料からの変更点` に未反映の「変更提案」行がない
- [ ] 対象 issue が closed (実装 PR merge 済み)
- [ ] この spec を変更元とする派生 issue (`quality:*`) が open で残っていない
- [ ] `prompts/` の実装作業がすべて完了している (未実行 prompt が残っていない)

## 手順

1. **最終刈り取り** (取りこぼしの最後の検出点):
   - `## 解決済みの論点` を全行走査し、各行に「反映先 (design / ADR) または spec で閉じる」の
     判定が付いているか確認する。**選択肢を比較して決めた判断で ADR 未起票のものがあれば、
     削除前に ADR に起こす** (spec 削除後、「なぜこうしたか」を再現できる場所は ADR だけになる)
   - 横断的な知見 (ハマりどころ / 暗黙の仕様) が残っていれば `context-harvest` で書き戻す
2. **参照の後始末**:
   - `context/impact-index.yaml` の `source_refs:` から当該 spec のパスを除去し、
     ADR / feature doc に差し替える (`read:` は design 側を指しているはずなので通常変更不要)
   - feature doc / ADR に spec への決定経緯リンクがあれば、issue へのリンクに差し替える
3. **削除の承認**: 削除対象・刈り取り結果・参照差し替えを提示し、承認を取る (contract の「承認の取り方」)
4. **削除と commit**: `git rm -r specs/<issue-id>-<slug>/` し、削除 commit のメッセージに
   issue 番号を含める (例: `chore(spec): closeout #<N> — durable 成果は <反映先> へ反映済み`)

## 停止条件

- 前提条件のいずれかが未達 (特に sync 未完 — 先に sync phase を実行する)
- 最終刈り取りで ADR 未起票の意思決定が見つかった (ADR 化してから再開する)
- 削除の承認が得られていない
- 参照の差し替え先 (ADR / feature doc) が存在しない — 反映漏れの兆候。sync に戻る
