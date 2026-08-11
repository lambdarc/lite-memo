# Data Model

この文書は Lite Memo の Room データモデルの構造と、クエリを書くときに守る制約をまとめます。
Data 層の方針とメモ画像の扱いは [`docs/architecture.md`](architecture.md) の Data / メモ画像 を正本とし、
ここでは実際のテーブル構成と、コードを読んでも導けない制約を扱います。

## データベース

- 実体は `LiteMemoDatabase`、ファイル名は `lite_memo.db`、現在の version は 1
- entity は `memos` / `tags` / `memo_tag_refs` / `memo_images` の 4 つ
- DAO は `MemoDao` / `MemoBulkDao` / `TagDao` の 3 つ
- 構築は `data/di/DatabaseModule` が行う。journal mode や PRAGMA を明示設定していないため、外部キー制約の有効化は Room の既定動作に依存する

## テーブル構成

メモを中心に、タグと画像が参照で紐づきます。

- `memos`: メモ本体。`id` が主キー。`createdAt` と `deletedAt` に index を持つ
- `tags`: タグ。`id` が主キー。`name` に unique index を持ち、同名タグを DB 層で拒否する
- `memo_tag_refs`: メモとタグの多対多。`(memoId, tagId)` が複合主キー。`(memoId, position)` の unique index でメモ内のタグ順を保つ
- `memo_images`: 添付画像のメタデータ。`id` が主キー。`(memoId, position)` の unique index で表示順を保つ。実ファイルは Room の外にあり、`fileName` だけを持つ

`memo_tag_refs` と `memo_images` は `memos` へ、`memo_tag_refs` は `tags` へ、いずれも `ON DELETE CASCADE` の外部キーを持ちます。
メモを物理削除すると、タグ参照と画像メタデータは DB 側で自動的に消えます。

## 論理削除とごみ箱

ごみ箱は独立したテーブルではなく、`memos.deletedAt` の null 判定で表します。

- `deletedAt IS NULL` が通常のメモ、`IS NOT NULL` がごみ箱内のメモ
- ほぼ全てのクエリがこの述語で分岐する。新しいクエリを足すときも、どちらを対象にするか必ず決める
- `deletedAt` に index があるのはこの分岐のため
- ごみ箱への移動と復元は `deletedAt` の UPDATE で、更新行数を返す。対象がすでにその状態なら 0 行になり、呼び出し側はこれを失敗として扱う
- 物理削除は原則として、ごみ箱内メモの完全削除と 30 日経過分の一括削除に限る

この原則の例外がひとつあります。`MemoDao.discardMemo` は `deletedAt` を問わず物理削除する唯一のクエリです。
新規メモの編集セッションが内容を持たないまま終了したときだけ使い、自動保存が作った空行をごみ箱へ出さないためのものです。
既存メモを空にして戻った場合はごみ箱へ移動し、この経路は通りません。

## 画像ファイルと Room の整合

画像の実ファイルはアプリ専用領域にあり、Room は `fileName` の参照だけを持ちます。
両者がずれないよう、削除は必ず次の順序で行います。

- transaction 内で参照を更新し、参照が外れたファイル名を戻り値として集める
- transaction の commit 後に、集めたファイル名を実ファイル削除へ渡す

`...AndCollectRemovedFileNames` / `...AndCollectImageFileNames` という名前の DAO メソッドはこの用途です。
Repository 側の削除は `NonCancellable` で囲み、commit 後にキャンセルされて参照だけが消えた状態を作らないようにします。

現状の例外を 2 つ記録します。

- import 経路（`RoomMemoImportRepository`）だけは commit 後の削除を `NonCancellable` で囲んでいない。commit とファイル削除の間でキャンセルされると未参照ファイルが残る
- 未参照ファイルの回収は `StagingMemoImportArchiveRepository` が起動時に行うが、対象は中断した import セッションの接頭辞を持つファイルに限られる。全ファイルを走査して Room と突き合わせる汎用の回収処理は無い

## クエリを書くときの制約

- `IN (:ids)` を使うクエリは、呼び出し側で 900 件ずつ chunk する。SQLite の変数上限を超えないための措置で、`SQLITE_QUERY_PARAMETER_BATCH_SIZE` を使う
- 期間で絞るクエリは `createdAt >= :from AND createdAt < :to` の半開区間にする。終端と同じ時刻のメモは含めない
- SQL で一覧の順序を確定するクエリは、同じ優先キーの行順も契約に含める場合、`id` を最後の並び順に加える。現状では `observeRecentActiveMemos` がこの tie-break を持つ。その他のメモ一覧は Domain 層で並べ替えるか、ごみ箱のように同一時刻内の順序を規定していない
- 検索は `LIKE :pattern ESCAPE '\'` を使う。ユーザー入力の `%` `_` `\` は `RoomMemoRepository` の `toEscapedLikePattern` でエスケープしてから渡す

半開区間の境界と `observeRecentActiveMemos` の `id` tie-break は instrumented test で固定しています。挙動を変えるとテストが落ちます。

## 楽観的ロック

複数メモをまとめて更新する経路だけ、`updatedAt` を version として使った競合検出を行います。

- 対象は一括のお気に入り切り替えとタグ付け / タグ外し
- 読み出し時の `updatedAt` を期待値として渡し、書き込み時に一致しなければ `IllegalStateException` を送出して transaction ごと中止する
- ごみ箱への一括移動と、単一メモの保存・お気に入り切り替えはこの経路を通らず、競合検出をしない
- 送出される例外は専用型ではないため、UI では他の失敗と区別していない

## DAO の構成

- `MemoDao`: 単一メモの参照・更新と、画像ファイル名の収集
- `MemoBulkDao`: `MemoDao` を継承し、複数 ID を扱う一括操作を足す。継承しているのは、chunk と画像ファイル名の収集を単一操作側と共有するため
- `TagDao`: タグの参照・更新

クエリ結果は用途ごとに projection を分けます。
`MemoWithRefs` はタグ参照と画像参照を伴う完全な取得、`MemoSummaryProjection` はウィジェット向けの最小限、
`MemoVersionProjection` は楽観的ロックの version 読み出しに使います。

## schema export と migration

- schema は `app/schemas/` へ export し、変更した version の JSON を同じ commit に含める
- `LiteMemoMigrations.ALL` は空。version 1 から上げたことがないため
- migration の instrumented test は version 1 のスキーマ生成と unique 制約の確認のみで、version 間の遷移はまだ検証対象に無い
