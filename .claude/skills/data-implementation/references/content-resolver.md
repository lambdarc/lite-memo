# ContentResolver / URI

画像添付やファイル入出力の Android 依存（`ContentResolver` による読み書き）を data 層に閉じる。
URI 取得と画像パスの基本方針は [`docs/architecture.md`](../../../../docs/architecture.md) の「メモ画像」を正本とする。

## 確認する対象

- `data/image/`、`data/export/`、`data/repository/` 配下のファイル・URI 入出力実装
- `domain/model/value/` にある URI 参照用の値オブジェクト（`ImageSourceReference`、`ExportFileReference` 等）と対応する domain interface

## 実装時の注意

- URI 取得（Activity Result）は `ui/route` に置き、`ContentResolver` による読み書きは data 層に閉じる。ViewModel や domain に `android.net.Uri` や `ContentResolver` を持ち込まない。
- `ContentResolver.openOutputStream` で既存ファイルへ上書き出力する際は `"wt"` モードを明示して truncate する（モード未指定だと既存末尾のゴミが残る provider がある）。
- `openInputStream` / `openOutputStream` が `null` を返した場合は握り潰さず `IOException` を送出する。
- `ContentResolver` の読み書きやファイルコピーなどの blocking I/O は `@IoDispatcher` 上で実行する。
- 画像コピーの失敗やサイズ上限超過（`MemoImageTooLargeException`）時は、途中作成したファイルを削除して壊れたファイルを残さない。

## テスト判断

- URI / `ContentResolver` を跨ぐ読み書きや `"wt"` による truncate の検証は instrumented test で押さえる（詳細は [`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）。
- サイズ上限や拡張子・参照値の検証など Android 非依存の部分は JVM Unit Test に寄せる。

## 検証観点

- 権限不足・存在しない URI・`null` ストリーム・読み書き失敗時の例外と途中ファイル削除が定義されているか。
- 上書き出力で古いファイル末尾が残らないか。
