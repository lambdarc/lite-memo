# export / import

ZIP export / import でメモ・タグと画像を一体のarchiveとして入出力する。

## 確認する対象

- `data/export/` のarchive codec / session、`data/model/export/` のmanifest DTO、`ExportDataMapper`
- `ExportMemosUseCase` / `ImportMemosUseCase` と domain の `ExportData`

## 実装時の注意

- 画像本体はBase64化せず、独立したZIP entryとしてstream処理する。
- AndroidのURI、実path、streamはData層に閉じ、Domain / ViewModelにはopaque tokenとreferenceだけを公開する。
- Exportはapp-private archiveを完成・検証してから保存先へ書き、成功・失敗・cancelで一時fileをcleanupする。
- Importは画像staging、DB反映、rollback / recoveryを一連の操作として扱う。
- version、entry path、size、checksum、重複IDをarchive codecで検証する。

## テスト判断

- DTO ↔ domain 変換と、ZIP往復（export → import）の一致をUnit Testで押さえる。
- ContentResolverのtruncateとfilesystem cleanupはandroidTestで押さえる。

## 検証観点

- 破損・欠損画像や不正manifestで部分的なarchive / importを残さないか。
- 画像ID、順序、metadata、byte列を往復で維持するか。
