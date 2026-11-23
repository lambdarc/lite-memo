# Data Model

この文書は Lite Memo の Room データモデルの構造と、クエリを書くときに守る制約をまとめます。
Data 層の方針とメモ画像の扱いは [`docs/architecture.md`](architecture.md) の Data / メモ画像 を正本とし、
ここでは実際のテーブル構成と、コードを読んでも導けない制約を扱います。

## データベース

- 実体は `LiteMemoDatabase`、ファイル名は `lite_memo.db`（schema version は `LiteMemoDatabase` を正本とする）
- entity は `data/local/entity/`、DAO は `data/local/dao/` に配置する
- 構築は `data/di/DatabaseModule` が行う。journal mode や PRAGMA を明示設定していないため、外部キー制約の有効化は Room の既定動作に依存する

## テーブル構成

メモを中心に、タグと画像が参照で紐づきます。

- `memos`: メモ本体。`id` が主キー。`createdAt` と `deletedAt` に index を持つ
- `tags`: タグ。`id` が主キー。`name` に unique index を持ち、SQLite の既定（BINARY collation）で大文字・小文字を区別したうえで同名タグを DB 層で拒否する。`(createdAt, id)` の index を持ち、タグ一覧はこの順で返す。取り込みでタグ間の名前の移動や入れ替えが起きるため、`TagDao.insertOrUpdateAllTags` は名前が変わる行を一時名へ退避してから追加・更新する
- `memo_tag_refs`: メモとタグの多対多。`(memoId, tagId)` が複合主キー。`(memoId, position)` の unique index でメモ内のタグ順を保ち、`tagId` の index でタグ側からの参照を引く
- `memo_images`: 添付画像のメタデータ。`id` が主キー。`(memoId, position)` の unique index で表示順を保つ。実ファイルは Room の外にあり、`fileName` だけを持つ

`memo_tag_refs` と `memo_images` は `memos` へ、`memo_tag_refs` は `tags` へ、いずれも `ON DELETE CASCADE` の外部キーを持ちます。
メモを物理削除すると、タグ参照と画像メタデータは DB 側で自動的に消えます。

## 論理削除とごみ箱

ごみ箱は独立したテーブルではなく、`memos.deletedAt` の null 判定で表します。

- `deletedAt IS NULL` が通常のメモ、`IS NOT NULL` がごみ箱内のメモ
- ほぼ全てのクエリがこの述語で分岐する。新しいクエリを足すときも、どちらを対象にするか必ず決める
- `deletedAt` に index があるのはこの分岐のため
- 単体のごみ箱移動・復元（`MemoTrashDao.moveMemoToTrash` / `restoreMemoFromTrash`）と単体の完全削除（`MemoTrashDao.deleteMemoPermanently` / `deleteMemoPermanentlyAndCollectImageFileNames`）は、対象が想定状態に無ければ更新行数が 0 になり、`check` で失敗（例外）として扱う
- 一括のごみ箱移動は、`ApplyMemoBulkActionUseCase` が先に `getActiveMemos` で対象を取得し、通常のメモでないものがあれば `requireNotNull` で例外にする。`MemoBulkDao.moveMemosToTrash` が読み飛ばすのは、その確認の後に通常のメモでなくなったもの（並行変更）だけ
- 一括の復元と一括の完全削除（`MemoBulkDao.restoreMemosFromTrash` / `deleteMemosPermanentlyAndCollectImageFileNames`）は事前確認が無い。ごみ箱に無いメモと存在しない id は読み飛ばして成功する
- ごみ箱へ移す UPDATE は `deletedAt` に保存時点の `updatedAt` 以上の値を書く。呼び出し側が時刻を決めてから書き込むまでに別の更新が入っても、domain の `deletedAt >= updatedAt` を満たす行だけが残る
- 物理削除は原則として、ごみ箱内メモの完全削除と期限切れ分の一括削除（`deletedAt <= :cutoff` の閉区間判定）に限る

この原則の例外がひとつあります。`MemoDao.discardMemo` は `deletedAt` を問わず物理削除する唯一のクエリです。
どの条件で呼ぶかは [`docs/memo-edit-lifecycle.md`](memo-edit-lifecycle.md) を正本とし、通常の削除経路からは使いません。

## 画像ファイルと Room の整合

画像の実ファイルはアプリ専用領域にあり、Room は `fileName` の参照だけを持ちます。
両者がずれないよう、削除は必ず次の順序で行います。

- transaction 内で参照を更新し、参照が外れたファイル名を戻り値として集める
- transaction の commit 後に、集めたファイル名を実ファイル削除へ渡す

`...AndCollectRemovedFileNames` / `...AndCollectImageFileNames` という名前の DAO メソッドはこの用途です。
Repository 側の削除は `NonCancellable` で囲み、commit 後にキャンセルされて参照だけが消えた状態を作らないようにします。

未参照ファイルの起動時回収は `StagingMemoImportArchiveRepository` が担当しますが、対象は中断した import セッションの接頭辞を持つファイルに限られます。
全ファイルを走査して Room と突き合わせる汎用の回収処理は持たず、通常経路の整合は上記の transaction 後削除に依存します。

## クエリを書くときの制約

- `IN (...)` を使うクエリ（id のほか、タグ名や画像ファイル名の照合を含む）は、呼び出し側で 900 件ずつ chunk する。SQLite の変数上限を超えないための措置で、`SQLITE_QUERY_PARAMETER_BATCH_SIZE` を使う
- 期間で絞るクエリは `createdAt >= :from AND createdAt < :to` の半開区間にする。終端と同じ時刻のメモは含めない
- SQL で一覧の順序を確定するクエリは、同じ優先キーの行順も契約に含める場合、`id` を最後の並び順に加える（例: `observeRecentActiveMemos`、`TagDao.observeTags` / `getAllTags` の `createdAt ASC, id ASC`）。その他のメモ一覧は Domain 層で並べ替えるか、ごみ箱のように同一時刻内の順序を規定していない
- 検索は `LIKE :pattern ESCAPE '\'` を使う。ユーザー入力の `%` `_` `\` は `RoomMemoRepository` の `toEscapedLikePattern` でエスケープしてから渡す
- 検索やカレンダーなどの絞り込み条件は、DAO と UseCase で二重定義しない
- DAO メソッドの戻り値は、継続監視する Flow 観測系と単発の suspend 実行系を用途で分ける

半開区間の境界、`observeRecentActiveMemos` の `id` tie-break、タグ一覧の `createdAt` → `id` 順は instrumented test で固定しています。挙動を変えるとテストが落ちます。

## 楽観的ロック

読み出し時のメモ本体・タグ参照・画像参照を保存時に比較して競合を検出します。

- 対象は選択メモへの一括操作で、お気に入り切り替えとタグ付け / タグ外しがある。編集画面にお気に入り変更操作は無く（既存値の引き継ぎのみ）、単一メモのお気に入り変更もこの一括操作を通る
- `ActiveMemoBulkWrite.expectedMemo` に読み出し時の内容を渡す。全対象が有効で内容も一致することを同一 transaction 内で確認し、不一致なら `IllegalStateException` を送出して全件中止する
- 時計の巻き戻りなどで `updatedAt` が変わらなくても、本文や参照の変更を検出する。参照は相対的な順序を比較し、タグ削除で生じる position の欠番は無視する
- 検出のため、対象メモの本体とタグ参照・画像参照を保存直前に読み直す。内容を変えない `CheckOnly` も同じ読み直しを伴うため、選択件数に比例して読み込み量が増える。正確な検出を優先してこのコストを許容する
- ごみ箱への一括移動はこの経路を通らず、競合検出をしない
- 送出される例外は専用型ではないため、UI では他の失敗と区別していない

編集画面の保存も、同じ比較で競合を検出します。

- `SaveMemoCommand.expectedMemo` に、編集セッションが最後に読み込んだか保存したメモを渡す。新規メモは `null` で、まだ有効なメモが存在しないことを期待する
- `MemoDao.upsertMemoWithRefsIfUnchangedAndCollectRemovedFileNames` が同一 transaction 内で現在の有効なメモと比較し、不一致なら書き込まない。`RoomMemoRepository` はこれを `MemoEditConflictException` として送出する
- 比較の対象は一括操作と同じで、本体と、相対順序で見たタグ参照・画像参照
- 空になった既存メモのごみ箱移動も、`MemoTrashDao.moveMemoToTrashIfUnchanged` で同じ比較と移動を同一 transaction 内で行う。不一致なら移動せず、`MemoEditConflictException` を送出する
- インポートは画像を新しいファイル名で取り込むため、編集中のメモがインポートで上書きされると必ず不一致になる。古いファイル名を書き戻して、取り込んだ画像を参照差分として削除することを防ぐ

## DAO の構成

- `MemoDao`: 単一メモの参照・更新と、画像ファイル名の収集
- `MemoTrashDao`: `MemoDao` を継承し、単一メモのごみ箱移動・復元・完全削除と、期限切れのごみ箱の削除を足す。継承しているのは、照合付きの移動や完全削除で、有効なメモの読み出しと画像ファイル名の収集を同一 transaction 内で使うため
- `MemoBulkDao`: `MemoDao` を継承し、複数 ID を扱う一括操作を足す。継承しているのは、chunk と画像ファイル名の収集を単一操作側と共有するため
- `TagDao`: タグの参照・更新

クエリ結果は用途ごとに projection を分けます。
`MemoWithRefs` は競合検出にも使うタグ参照と画像参照を伴う完全な取得、
`MemoSummaryProjection` はウィジェット向けの最小限の取得に使います。

## schema export と migration

- schema は `app/schemas/` へ export し、変更した version の JSON を同じ commit に含める。過去 version の schema JSON は変更しない
- schema を変更するときは `LiteMemoDatabase` の version を上げ、`LiteMemoMigrations.ALL` へ migration を追加する（追加漏れは起動時クラッシュになる）
- migration では既存データの保持、default 値、index、外部キー、論理削除済みデータの扱いを明示し、破壊的 migration（fallback）に安易に頼らない
- migration と schema 制約は `MigrationTestHelper` を使った instrumented test で検証する（詳細は [`docs/instrumented-test.md`](instrumented-test.md) を参照）
