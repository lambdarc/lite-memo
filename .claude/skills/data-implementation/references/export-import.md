# export / import

ZIP export / import でメモ・タグと画像を一体のアーカイブとして入出力する。
ZIP 構造・manifest 形式・version 互換・上限・衝突解決の仕様は [`docs/export-import-format.md`](../../../../docs/export-import-format.md) を正本とする。

## 確認する対象

- `data/export/` の archive codec・validator・session 管理、`data/model/export/` の manifest DTO、`data/mapper/` の変換関数
- `data/repository/` の export / staging import / Room import 実装と、`di/` の serialization / archive 上限設定
- 対応する `domain/usecase/`・`domain/repository/`・`domain/exception/`

## 実装時の注意

- 検証責務の分担を崩さない。
  - archive codec / validator（`data/export/`）: ZIP 署名・構造、エントリ名安全性、manifest 形式・version、サイズ上限・実サイズ・SHA-256 checksum、識別子重複の検証。
  - import archive repository（`data/repository/`）: staging・画像配置・rollback / 起動時回収と、`MemoArchiveException` / `IOException` から `MemoImportException` へのエラー変換。
  - UseCase / Room import 側（`domain/usecase/`・`data/repository/`）: 空メモ拒否、存在しないタグ参照の整理、アーカイブ内および既存 DB とのタグ名衝突の検出。
- Android の URI・実パス・stream は data 層に閉じ、domain / ViewModel には opaque token と reference だけを公開する。
- export は app-private 領域でアーカイブを完成・自己検証してから `"wt"` モードで保存先へ出力し、成功・失敗・キャンセルで一時ファイルを cleanup する。
- import は staging、DB 反映、rollback / 起動時回収を一連のセッションとして扱い、画像取り込み時は既存画像との衝突回避と回収識別のためにセッション接頭辞付きで `fileName` を再採番する。

## テスト判断

- DTO ↔ domain 変換、archive codec / validator の検証、ZIP 単体往復（export → import）は JVM Unit Test で押さえる。
- `ContentResolver` の `"wt"` truncate、staging / rollback / 起動時回収、Room import の実 DB 反映は instrumented test で押さえる（[`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）。

## 検証観点

- 破損・欠損画像や不正 manifest で部分的なアーカイブや未参照の取り込み画像を残さないか。
- ZIP codec 単体の往復で manifest・画像 metadata（ID、順序、`fileName`、`archiveEntry`、サイズ、checksum）・byte 列が一致するか。
- アプリ取り込み時に画像 `fileName` がセッション接頭辞付きで新規採番され、既存画像ファイルと衝突しないか。
