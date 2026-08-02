# ContentResolver / URI

画像添付やファイル入出力の Android 依存（URI / ContentResolver）を data 層または app entry 側に閉じる。

## 確認する対象

- `data/image/MemoImageFileDataSource`、`data/repository/FileSystemMemoImageStore`、`data/repository/FileSystemMemoExportArchiveRepository`、`data/export/MemoImportArchiveExtractor`
- domain の `ImageSourceReference` / `ExportFileReference`（data 層が変換・実装で扱う contract）

## 実装時の注意

- URI / ContentResolver を ViewModel や domain に持ち込まず、data 層 / app entry へ閉じる。
- 画像の実パスは UI で組み立てず、UseCase 経由で解決する。
- 未保存画像の cleanup と、保存済み画像の repository 差分削除を分けて扱う。

## テスト判断

- URI / ContentResolver を跨ぐ経路は androidTest を検討し、変換・検証部分は JVM Unit Test に寄せる。

## 検証観点

- 権限・存在しない URI・読み取り失敗時の扱いが定義されているか。
