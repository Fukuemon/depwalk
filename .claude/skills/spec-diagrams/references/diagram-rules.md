# Diagram Rules

`spec-diagrams` で Mermaid 図を生成するときに従うルール。
project 固有の framework (e.g. Casbin / PostgreSQL / S3) は **書かない**。spec の Appendix に合わせて participants を選ぶ。

## Flowchart (ユーザー操作起点)

ユーザーがアプリを触ったときに起きる流れを描く。バックエンド内部処理は sequence diagram に任せる。

```text
必須要素:
- 開始: ユーザーのアクション (ボタンクリック、ページ遷移 等)
- (必要なら) 権限チェック: 表示制御 / 認可分岐
- (必要なら) フロントバリデーション: 入力チェック → エラー → 再入力ループ
- API / Action 呼び出し: 呼び先を明記
- レスポンス分岐: 成功 / 既知の業務エラー / ネットワークエラー
- 終了: 成功 UI 更新、または失敗時の表示
```

Mermaid 構文の注意:

- ラベル内の括弧はダブルクォートで囲む: `-->|"テキスト (補足)"|`
- 日本語テキストはそのまま使用可
- ノード ID には英数字を使用する

## Sequence Diagram (システム内部処理)

システム / 外部サービス間のやり取りを描く。spec の Appendix で扱う層に合わせて participants を選ぶ。

```text
participants の付け方:
- actor User as ユーザー
- participant FE as Frontend
- participant API as Backend
- participant DS as Datastore     # database appendix を取り込んでいる場合
- participant AuthZ as AuthZ      # authorization appendix を取り込んでいる場合
- participant Ext as ExternalService  # 第三者 API を呼ぶ場合のみ
```

下記のいずれかが spec の機能仕様 / appendix に書かれていれば描く:

- 認可 / 権限チェック (`appendices/authorization.md` を取り込んでいる)
- トランザクション境界 (`appendices/database.md` を取り込んでいる)
- 楽観 / 悲観ロック、UPSERT, COMMIT/ROLLBACK
- エラー分岐: spec の「エラーコード一覧」全コードに対する alt 分岐

## 生成後の検証

生成した図が以下と整合しているか確認する:

- spec の `## 要件の解釈` (EARS 風受け入れ基準)
- 各 appendix (API / Database / Authorization / Screen)
- 既存の `## フロー / シーケンス` (差分更新の場合)

未確定論点が残っている場合は **描かない**。spec-resolve で確定させてから戻る。
