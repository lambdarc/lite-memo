# Memo Edit Lifecycle

この文書はメモ編集セッションの復元、autosave、終了、画像 cleanup の契約をまとめます。
UI / MVVM の配置は [`docs/architecture.md`](architecture.md) を、Room と画像ファイルの整合は
[`docs/data-model.md`](data-model.md) を正本とし、ここでは編集画面に固有の状態遷移を扱います。

主な実装は [`MemoEditRoute`](../app/src/main/kotlin/com/lambdarc/litememo/ui/route/MemoEditRoute.kt) と
[`MemoEditViewModel`](../app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/MemoEditViewModel.kt) です。

## 責務

- `MemoEditRoute` は画面 lifecycle、戻る操作、画像 picker、Navigation、エラー表示を ViewModel へ接続する
- `MemoEditViewModel` は編集セッション、autosave、終了条件、一時画像の所有状態を管理する
- `SaveMemoUseCase` は空メモの拒否、タグの存在確認、id と timestamp を含む保存内容の確定を行う
- `MemoRepository` は Room transaction と、保存済み画像の参照差分に基づくファイル削除を行う

Compose のレイアウトや表示文言は、この文書の対象に含めません。

## セッションの種類と識別子

編集セッションは、開始時点で新規か既存かを確定します。

- 既存メモは Navigation の `memoId` で Room から読み込む
- 新規メモは ViewModel の生成時に `MemoId` を発行し、`SavedStateHandle` の `generatedMemoId` に保持する
- カレンダーから作成する場合は Navigation の `createdAt` を初回保存へ渡す
- `sessionStartedAsNew` は開始時点の分類を保持し、最初の autosave 後も既存セッションへ切り替えない

新規メモは一度 autosave されると Room 上に行を持ちます。
その後に内容を空へ戻して終了した場合も、新規として開始したセッションなら物理削除します。
プロセス再生成後にこの判定を失わないよう、`sessionStartedAsNew` を `SavedStateHandle` に保持します。

## 初期状態の復元

初期状態は次の優先順位で決めます。

1. `SavedStateHandle` に編集中の内容があれば、その内容を復元する
2. 編集中の内容がなく `memoId` があれば、Room から既存メモを読み込む
3. どちらもなければ、生成済み id を持つ空の新規メモとして開始する

編集中のタイトル、本文、選択タグ、お気に入り、画像 id、画像ファイル名、画像の保存済み状態を
`SavedStateHandle` に保持します。これは画面状態の復元用であり、永続データの正本は Room です。

タグ一覧はセッション中も監視します。削除済みのタグ id が編集中の選択へ残っていた場合は、
現在存在するタグだけへ絞り込みます。

## 編集と autosave

タイトル、本文、タグ、お気に入り、画像が変わると、次の順序で処理します。

1. `MemoEditUiState` を更新する
2. 編集中の状態を `SavedStateHandle` に保存する
3. 既存の autosave job をキャンセルする
4. 最後の変更から 1 秒後に保存する

タイトルと本文が空白だけで、画像もない状態は空とみなします。
タグやお気に入りだけでは保存対象にならず、空の状態を autosave して Room の行を作成・更新しません。

画面が `ON_STOP` になった場合は、保留中の autosave をキャンセルして即時保存を試みます。
ただし、終了処理または削除処理がすでに始まっている場合は重複して保存しません。

## 保存と終了の直列化

autosave、終了時保存、破棄、ごみ箱への移動は、同じ `persistMutex` で直列化します。
これにより、進行中の保存より先に破棄や削除が完了し、保存処理がメモを復活させる競合を防ぎます。

- 終了または削除の開始時に、保留中の autosave job をキャンセルする
- `isFinishing` または `isDeletePending` の間は、編集、画像追加、画像削除、新しい autosave を受け付けない
- 保存対象の画像 id は `activePersistImageIds` に保持し、保存中の未保存画像を同時に削除しない
- `CancellationException` は再送出し、通常の操作失敗として扱わない

## 終了時の処理

| 操作と状態 | 処理 | 成功後の遷移 |
| --- | --- | --- |
| 内容がある状態で戻る | 最新状態を保存する | 編集状態を消去して前画面へ戻る |
| 空の新規セッションで戻る | `discardMemo` で物理削除する | 前画面へ戻る |
| 空の既存メモで戻る | メモをごみ箱へ移動する | 削除済み id を通知して前画面へ戻る |
| 削除操作を実行する | メモをごみ箱へ移動する | 削除済み id を通知して前画面へ戻る |
| 保存または削除に失敗する | 編集状態を保持してエラーを通知する | 画面に残る |

空の判定はタイトル、本文、画像だけで行います。
新規セッションの物理削除は、autosave が作成した空行をごみ箱へ残さないための例外です。
既存メモを空にした場合は履歴を失わないよう、ごみ箱へ移動します。

## 画像ファイルの lifecycle

画像を選択すると、Room へ保存する前にアプリ専用領域へコピーします。
UI state の `isPersisted` は、その画像参照が Room へ保存済みかを表します。

- コピーに成功した画像だけを編集状態へ追加し、一部が失敗した場合は成功分を残してエラーを通知する
- コピー完了時に終了・削除処理が始まっていた場合は、追加せずにコピー済みファイルを削除する
- Room 保存前の画像を編集状態から外した場合は、保存処理中でなければ即時にファイルを削除する
- 保存に成功した画像は `isPersisted = true` へ更新する
- Room 保存済み画像を外した場合は、Repository が transaction 後に参照差分のファイルを削除する

未保存画像の cleanup は ViewModel、保存済み画像の差分 cleanup は Repository が担当します。
この境界を Repository 側だけへ寄せると、Room に一度も保存されなかった画像を回収できません。

## イベントと失敗

Navigation と操作エラーは `Channel.BUFFERED` で通知し、collector の一時的な停止で結果を失いにくくします。

- 保存失敗は `SaveFailed`、削除失敗は `DeleteFailed`、画像追加の一部または全部の失敗は `ImageAttachFailed` として通知する
- 保存または削除に失敗した場合は `SavedStateHandle` を消去せず、再試行できる状態を保つ
- Navigation event は保存、破棄、ごみ箱移動が成功してから送る
- 画像ファイルの best-effort cleanup 失敗は、Room の状態や画面遷移を巻き戻さない

## 変更時に守ること

- autosave と終了・削除を別々の同期境界へ分けない
- Room に保存されたかどうかだけで、新規セッションと既存セッションを再分類しない
- 終了処理の開始後に `updateEditState` から autosave を再予約しない
- 一時画像と保存済み画像の cleanup 担当を混同しない
- 保存成功前に Navigation event を送らない
