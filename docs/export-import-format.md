# Export / Import Format

この文書は Lite Memo の export / import が扱う ZIP アーカイブの形式をまとめます。
アーカイブはユーザーの端末に残り、書き出した版と読み込む版が一致するとは限らないため、
ここでの取り決めは外部データとの契約として扱います。
Data 層の実装方針は [`docs/architecture.md`](architecture.md) の Data を正本とします。

## アーカイブの構造

エントリは 2 種類だけです。

- `manifest.json`: アーカイブ直下に 1 つ。**必ず先頭のエントリ**として書き、読み込み側も先頭に無ければ不正として扱う
- `images/00000001` 形式: 画像本体。`images/` の下に 8 桁ゼロ埋めの連番で、拡張子もサブディレクトリも持たない。番号は 1 から始まり、manifest に現れる画像の順で振る

画像エントリは method を `DEFLATED` のまま圧縮レベル 0（`Deflater.NO_COMPRESSION`）で書き、`STORED` にはしません。
`manifest.json` だけは通常の圧縮が掛かります。

LiteMemo 独自のマジックバイトはありません。アーカイブの識別は 2 段階で行います。

- ZIP のローカルファイルヘッダ（`PK\x03\x04`）で ZIP であることを確認する
- 先頭エントリが `manifest.json` として復号でき、`version` が一致することを確認する

したがって「LiteMemo のアーカイブか」は、中身を読むまで確定しません。

## manifest.json

フィールド名は Kotlin の宣言と同じで、`@SerialName` による別名はありません。
`prettyPrint` を有効にしているため整形済みで書かれます。

```json
{
  "version": 1,
  "exportedAt": 1234567890123,
  "tags": [
    { "id": "tag-1", "name": "Work", "colorArgb": 4284955806, "createdAt": 1234567890123 }
  ],
  "memos": [
    {
      "id": "memo-1",
      "title": "Title",
      "body": "Body",
      "createdAt": 1234567890123,
      "updatedAt": 1234567890123,
      "isFavorite": false,
      "tagIds": ["tag-1"],
      "images": [
        {
          "id": "image-1",
          "fileName": "photo.jpg",
          "archiveEntry": "images/00000001",
          "sizeBytes": 12345,
          "sha256": "0123...64桁の小文字16進"
        }
      ]
    }
  ]
}
```

タグはメモから `tagIds` で id 参照し、画像はメモへ埋め込みます。この非対称は意図したものです。
`tagIds` と `images` は既定値を持ちますが、`encodeDefaults` を有効にしているため常に出力します。

## version の扱い

`version` の正本は `ExportData.CURRENT_VERSION` です。ここが最も注意の要る箇所です。

- 読み込み側は `version` の**完全一致だけ**を受け付ける。古い版も新しい版も同じく拒否する
- Export / Import の UseCase と archive 読み込み時の検証は、同じ `ExportData.CURRENT_VERSION` を参照する
- JSON は `ignoreUnknownKeys` を有効にしているため、未知のフィールドがあっても復号自体は通る。ただし `version` が違えば手前で弾かれるため、フィールド追加だけの前方互換は実質的に効かない

つまり形式を変えて version を上げると、旧版のアプリは新しいアーカイブを一切読めません。
フィールドを足すだけの変更で version を据え置くか、上げて互換を切るかは、この前提のうえで決めます。

## 上限

上限値は `MemoArchiveLimits` に定義し、既定値をそのまま注入しています。

| 対象 | 上限 |
| --- | --- |
| 圧縮済みアーカイブの入力バイト数 | 320 MiB |
| `manifest.json` の展開後バイト数 | 5 MiB |
| 画像 1 件の展開後バイト数 | 32 MiB |
| 展開後画像の合計バイト数 | 256 MiB |
| エントリ総数（manifest を含む） | 1,001 |

圧縮率そのものの上限はありません。`ArchiveSizeLimitedInputStream` は `ZipInputStream` の前段で
圧縮済みアーカイブの入力バイト数を数え、`maxArchiveBytes` の超過時点で打ち切ります。
展開後の画像は、`MemoArchiveManifestValidator` が画像 1 件の宣言値を `maxImageBytes`、
宣言値の合計を `maxTotalImageBytes` と照合します。コピー時は `MemoArchiveImageCopier.copyVerified` が
実際の展開後バイト数を画像ごとの宣言値と照合します。エントリ数は `maxEntryCount` で別に制限します。

メモへ画像を添付するときも、`FileSystemMemoImageStore` が同じ `maxImageBytes` を上限としてコピーします。
上限を超えた時点でコピーを打ち切り、`MemoImageTooLargeException` を送出します。
合計サイズとエントリ数は添付時には制限していないため、画像の多いメモを貯めるとエクスポートが失敗することがあります。

## 読み込み時の検証順序

読み込みは次の順で検証し、いずれかで失敗した時点で中断します。

1. 圧縮済みアーカイブの入力バイト数が全体上限を超えないか（ストリーム中に計測）
2. ZIP のローカルファイルヘッダで始まるか
3. 先頭エントリが `manifest.json` か
4. manifest のバイト数が上限を超えないか（ストリーム中に計測）
5. manifest が JSON として復号できるか
6. manifest の内容が妥当か（version、id の非空と重複、値オブジェクトの制約、`updatedAt >= createdAt`、画像のサイズと SHA-256 の書式）
7. 以降のエントリごとに、エントリ数の上限と名前の安全性を確認し、manifest が宣言した画像エントリと一致するか
8. manifest が宣言した画像がすべて現れたか

エントリ名は、空文字・空白のみ（`isBlank()`）、`\`、`:`、制御文字、空セグメント、`.`、`..` のいずれかを含むと拒否します。
これで絶対パスと `..` によるディレクトリ脱出を防ぎます。

manifest が宣言していないエントリが 1 つでもあれば不正とします。未知のエントリは無視しません。

画像はコピーと同時に検証します。8 KiB ずつ読みながら SHA-256 を計算し、
宣言サイズを超えた時点で打ち切り、読み終えたサイズと SHA-256 の一致まで確認します。

## 失敗の分類

Domain の失敗理由（`MemoImportFailureReason`）と UI のエラー表示（`SettingsImportErrorDialogUiState`）は 1 対 1 ではありません。
UI 側では `MemoImportFailureReason` の各理由に加えて、タグ名衝突（`ImportTagNameConflictException`）と汎用エラーを独立して区別します。

`MemoImportFailureReason` では、次のように分類します。

- version 不一致、サイズ上限超過、画像の破損（サイズ / SHA-256 不一致）、`IOException` から判定するストレージ不足（`ENOSPC` 等）はそれぞれ独立した理由として残す
- それ以外のアーカイブ起因の失敗（ZIP 署名の不一致、ZIP 構造の破損、不正な UTF-8 を含むエントリ名の不正、識別子や画像 id の重複、空メモ、`ENOSPC` 以外の `IOException` など）は、すべて `INVALID_ARCHIVE`（不正なアーカイブ）へ集約する

利用者は `INVALID_ARCHIVE` の内訳を区別できません。切り分けが必要な場合はログか実装を確認します。

## import の衝突解決

- メモ id とタグ id が既存と重複する場合は、アーカイブ側の内容で**上書き**する。skip も id の振り直しもしない。メモのタグ参照と画像参照は一度削除してから入れ直す
- 同じ id の既存メモは、ごみ箱内かどうかを問わず上書きする。import するメモの `deletedAt` は NULL のため、ごみ箱内のメモは通常のメモに戻る
- タグ**名**の衝突は上書きせず、例外を送出して import 全体を中断する。対象は次の 2 つ。自動のリネームや統合はしない
  - アーカイブ内で同じ名前に異なる id が付いている
  - その名前を、import に含まれない既存タグ（id が import 対象のタグ id に無いもの）が持っている
- import に含まれるタグ同士で名前を移す・入れ替えるのは衝突にならない。`TagDao.insertOrUpdateAllTags` が一時名へ退避してから更新する
- メモが manifest に存在しないタグ id を参照している場合は、その参照だけを黙って取り除く。エラーにはしない
- タイトル・本文がともに空または空白のみ（`isBlank()`）で画像も無いメモ、および画像 id の重複は不正として拒否する

## import のステージングと画像ファイル名

- 取り込み時の画像 `fileName` は `manifest.json` の名前をそのまま再利用せず、`StagingMemoImportArchiveRepository` がセッション接頭辞付きのファイル名へ再採番する（既存画像との衝突回避と中断時回収のため）
- import はセッション単位で画像を staging 領域へ展開・移動してから Room の transaction をコミットする。検証や DB 更新に失敗した場合は、そのセッション接頭辞を持つ未参照画像を `NonCancellable` でロールバック削除する
- 処理の途中でプロセスが終了して残った未参照画像は、アプリ起動時に `LiteMemoApplication` 経由でセッション接頭辞をもとに回収する

## export の対象と出力

ごみ箱のメモは含めません。除外は後段の絞り込みではなく、
`WHERE deletedAt IS NULL` のクエリで取得する時点で行います。

export は先にアプリ内の一時ファイルへ ZIP アーカイブを書き出し、`MemoArchiveReader` で再読込して整合を検証します。
検証後に確定したファイルだけを `ContentResolver.openOutputStream(..., "wt")` で出力先へ上書きコピーします（`"w"` では truncate しないプロバイダでも末尾の古いバイトが残らないようにするため）。
中断や画面離脱で取り残された一時ファイルは、破棄処理またはアプリ起動時のクリーンアップで回収します。
