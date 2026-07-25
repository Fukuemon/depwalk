## Review 2026-06-15 16:33

Verdict: NEEDS_WORK
Reviewer: spec-review (fresh-context, subagent=default, id=019eca31-28a9-7bc3-9b92-a31fddcf230e)

### 観点別評価

- 上位文書整合: PASS — `specs/8-analyzer-protocol/index.md:37-50`, `design/DesignDoc.md:116-174`, `context/architecture.md:9-15`。Protocol 境界・JSONL・Core 非依存方針と整合。
- 未解決論点: NEEDS_WORK — `specs/8-analyzer-protocol/index.md:132-136`。未確定事項が残っているが、期限 / 決定者付き管理になっていない。
- 実装対象明示: PASS — `context/project.md:56-63`, `specs/8-analyzer-protocol/index.md:138-147`。対象 domain と実装有無・責務が一致。
- template 必須節: PASS — `hooks/spec/validate_document.sh:21-44`, `specs/8-analyzer-protocol/index.md:6-618`。validator は `bash hooks/spec/validate_document.sh` で exit 0。
- EARS acceptance: PASS — `specs/8-analyzer-protocol/index.md:110-118`。WHEN / IF / THE SYSTEM SHALL の観測可能な acceptance がある。
- prompts 自己完結性: N/A — `specs/8-analyzer-protocol/index.md:547-551`。prompts 生成方針のみで、`prompts/` は未生成。
- 正本境界: NEEDS_WORK — `specs/8-analyzer-protocol/index.md:563-570` に「反映済」行がある一方、`specs/8-analyzer-protocol/index.md:4`, `specs/8-analyzer-protocol/index.md:136`, `design/DesignDoc.md:174` は durable 詳細の handoff が未完了であることを示している。

### 指摘

- `specs/8-analyzer-protocol/index.md:132-136` — `## 未確定事項` に Core 実装言語 / package manager / test framework の未確定が残っているが、rubric 要件の期限 / 決定者がない。
- `specs/8-analyzer-protocol/index.md:563-570` — `反映済` 行があるため sync 済扱いになるが、schema / SPI / versioning など durable 成果はまだ spec が詳細正本を保持している。feature doc / ADR への正本ハンドオフ、または `反映済` 表現の整理が必要。

## Remediation 2026-06-15 17:15

- `## 未確定事項` を期限 / 決定者付きの表へ更新し、Spec8 の下流 phase をブロックしない未確定事項として整理した。
- `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` を作成し、Protocol / SPI / Model schema の正本としてハンドオフした。
- `adr/0001-analyzer-protocol-jsonl-spi.md` を作成し、JSONL over STDIN/STDOUT、process SPI、versioning 方針を ADR 化した。
- `context/testing.md` に Protocol contract test の横断観点を反映した。
- `specs/8-analyzer-protocol/index.md` の durable 節を決定時スナップショットとして明示し、feature doc / ADR / context への正本リンクを追加した。

Status: 再 review 待ち。

## Review 2026-06-15 17:20

Verdict: PASS
Reviewer: spec-review (fresh-context, subagent=default, id=019eca5b-f395-7922-ae92-4ecbdbda2720)

### 観点別評価

- 上位文書整合: PASS — `specs/8-analyzer-protocol/index.md:33-52` が Design Doc / feature doc / context / ADR との整合と反映済み状態を記録し、`design/DesignDoc.md:167-175`、`design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md:47-66`、`adr/0001-analyzer-protocol-jsonl-spi.md:17-30` と矛盾しない。
- 未解決論点: PASS — `specs/8-analyzer-protocol/index.md:122-142` で D1-D5 は解決済み、残る未確定事項は決定者 / 期限 / Spec8 影響付きで管理されている。
- 実装対象明示: PASS — `specs/8-analyzer-protocol/index.md:144-153` の target は `context/project.md:56-63` の対象ドメインと一致し、境界は `context/architecture.md:9-15` と整合する。
- template 必須節: PASS — `hooks/spec/validate_document.sh:21-44` の必須節に対し、`specs/8-analyzer-protocol/index.md:6-635` が該当節を保持。validator も exit 0。
- EARS acceptance: PASS — `specs/8-analyzer-protocol/index.md:112-120` に WHEN / IF / THE SYSTEM SHALL の観測可能な受け入れ基準があり、検証観点は `specs/8-analyzer-protocol/index.md:426-460` に展開されている。
- prompts 自己完結性: N/A — `specs/8-analyzer-protocol/index.md:565-569` は prompts 生成方針のみで、指定どおり `prompts/` は存在しないため評価対象外。
- 正本境界: PASS — `specs/8-analyzer-protocol/index.md:3-5`、`specs/8-analyzer-protocol/index.md:196-199`、`specs/8-analyzer-protocol/index.md:362-365`、`specs/8-analyzer-protocol/index.md:571-616` が sync 済み成果を feature doc / ADR / context 正本へハンドオフし、spec 側を決定時スナップショットとしている。
