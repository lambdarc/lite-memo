# Lite Memo の追加レビュー

2026年11月21日に `develop` の [`07030d5`](https://github.com/lambdarc/lite-memo/commit/07030d51d249d411a449811526b371d397a8dbb7) を対象に確認した追加指摘です。

## Critical（3件）

### 1. インポートと編集が重なると添付画像が失われる

1. 既存の画像付きメモを含む ZIP を設定からインポートする。
2. インポートが完了する前にボトムナビでホームへ移動し、復元対象と同じメモを開く。
3. インポートが完了してから本文を変更し、保存する。

インポートは画像を新しいファイル名で取り込み、古いファイルを削除します。編集画面は開いた時点のメモを保持しているため、保存すると古いファイル名が DB に書き戻されます。さらに保存処理は、保存前に参照されていて保存後に参照されなくなったファイルを削除するため、インポートで取り込んだ新しい画像ファイルも削除されます。結果として古い画像も新しい画像も失われ、添付画像が表示できなくなり、以降のエクスポートも失敗します。

戻る操作で設定画面を閉じた場合は `SettingsViewModel` が破棄されてインポート自体がキャンセルされます。ボトムナビでの移動では状態が保存されるため、インポートが続きます。

編集開始時の状態と DB の現在値を保存トランザクション内で照合し、競合した場合は上書き前に再読込を促します。一括操作で使っているスナップショット照合（`upsertActiveMemosWithSnapshotCheckAndCollectRemovedFileNames`）を参考にできます。[編集データの保存](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/RoomMemoRepository.kt#L85)・[保存時の参照外ファイルの収集](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/local/dao/MemoDao.kt#L181)・[インポート時のファイル名の採番](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/StagingMemoImportArchiveRepository.kt#L126)・[復元後の旧画像の削除](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/RoomMemoImportRepository.kt#L51)

### 2. 保存できる画像のサイズがバックアップの上限を超える

32 MiB を超える画像を添付してメモを保存すると、全件エクスポートが保存先の選択前に失敗します。1枚ずつは上限内でも、添付画像の合計が256 MiBを超えると同じ結果になります。画像の合計枚数が1000枚を超える場合と、manifest が5 MiBを超える場合も、同じくエクスポートの準備段階で失敗します。

添付時のサイズ制限とバックアップ形式で扱えるサイズをそろえます。既に保存されている大きな画像にも対応し、合計サイズの制限を超える場合は複数の ZIP に分けるなど、全件を復元可能な形で書き出せるようにします。[添付処理](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/image/MemoImageFileDataSource.kt#L24)・[アーカイブの上限](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/export/MemoArchiveLimits.kt#L29)・[エクスポート時の合計サイズ判定](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/FileSystemMemoExportArchiveRepository.kt#L108)・[エクスポート時の1枚ごとのサイズ判定](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/FileSystemMemoExportArchiveRepository.kt#L151)

### 3. アプリロック有効時に ZIP の選択からインポートへ戻れない

アプリロックを有効にした状態で設定から ZIP インポートを開始し、全画面のファイル選択によって `MainActivity` が停止する経路で起きます。ロック画面への切り替えで設定画面を含む `NavHost` が破棄されるため、解錠後はホームが開き、選んだ ZIP のインポート確認を続けられません。

ファイル選択の結果は、画面の再構成より先に配送されるため、破棄される前の `SettingsViewModel` に届くと考えられます。ただし、その ViewModel は以降どの画面からも参照されません。エクスポートの保存先選択も同じ経路をたどり、書き込みが成功しても完了の通知が表示されない可能性があります。

ロックをまたいで元の遷移先とファイル選択結果を保持し、解錠後に設定画面でインポート確認を再開します。`rememberNavController()` を `MainActivity` のロック分岐より外へ移すと、バックスタックと `SettingsViewModel` を保持できます。[ロックによる画面の切り替え](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/MainActivity.kt#L138)・[NavController の生成位置](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/navigation/LiteMemoApp.kt#L82)・[ZIP 選択結果の受け取り](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/route/SettingsRoute.kt#L63)

## Suggestion（3件）

### 4. フィルターで非表示になったメモに一括操作が届く

ホームでお気に入り登録していないメモを長押しし、「お気に入り」へ切り替えます。メモが一覧から消えても選択は残り、ごみ箱ボタンを押すとそのメモが移動します。同じ状態で共有ボタンを押すと、表示中の一覧から選択対象を見つけられず無反応になります。

通常一覧でフィルターを変更したときに選択を解除し、選択件数・共有・一括操作の対象を一致させます。[フィルター変更](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/HomeViewModel.kt#L104)・[共有と一括操作](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/HomeViewModel.kt#L170)

### 5. カレンダーの切り替え直後に見出しとデータがずれる

別の日を選んだ直後、DB からその日の結果が届くまで、新しい日付の見出しの下に前の日のメモが表示されることがあります。月を切り替えたときも、同じ仕組みで新しい月の見出しと前の月の日付グリッドが組み合わさります。

DB の取得結果に対象の日付・月を紐づけ、その時点の選択と一致する結果を表示します。[日付・月ごとの取得](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/CalendarViewModel.kt#L61)・[選択と取得結果の合成](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/CalendarViewModel.kt#L91)

### 6. 検索結果が届く前に「検索結果がありません」と表示される

ホームとカレンダーで一致するメモがある語を入力しても、250 ms の入力待ちと DB 検索の間に「検索結果がありません」と表示されます。検索結果の到着前と検索完了後の0件を同じ空リストで表しているためです。既に結果が表示されている状態で文字を追加入力した場合も、いったん同じメッセージに切り替わります。

現在の検索語の結果待ちを UI state で表し、その検索語の取得が完了して0件と確定した時点でメッセージを表示します。[検索結果の反映](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/SearchMemoResults.kt#L54)・[ホームの0件表示](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/screen/HomeScreen.kt#L207)・[カレンダーの0件表示](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/screen/CalendarScreen.kt#L196)

## Nitpick（1件）

### 7. インポート後の旧画像の削除がキャンセルで中断される

インポートの DB 更新が完了した直後にキャンセルされると、旧画像の削除が実行されずにファイルが残ります。他の Repository は削除を `NonCancellable` で実行していますが、インポートだけは囲んでいません。

旧画像の削除を `withContext(NonCancellable)` で実行し、他の Repository とそろえます。[復元後の旧画像の削除](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/RoomMemoImportRepository.kt#L51)・[編集データの保存時の削除](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/data/repository/RoomMemoRepository.kt#L91)

## Issue #168 の追加検証

API 28〜29で端末 PIN 認証画面を開いた後、認証結果が返る前に `MainActivity` を再生成するケースも確認します。例えば、生体認証を登録していない API 29 の端末で PIN を使ってアプリロックを解除します。開発者オプションの「アクティビティを保持しない」を有効にすると、再生成を確実に起こせます。

再生成後は新しい Activity が結果を受け取ります。結果を処理する `pendingCallback` が以前の `AppLockAuthenticator` にしかないため、成功やキャンセルが通知されず `AUTHENTICATING` のままになる可能性があります。`MainViewModel` は `AUTHENTICATING` の間、停止・復帰・解錠ボタンのいずれでも状態を変えないため、プロセスが終了するまで解錠できなくなります。実機またはエミュレーターで、成功時に解錠できることと、キャンセル時に再試行できることを確認します。[Issue #168](https://github.com/lambdarc/lite-memo/issues/168)・[認証結果の受け取り](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/auth/AppLockAuthenticator.kt#L144)・[認証中の状態遷移](https://github.com/lambdarc/lite-memo/blob/07030d51d249d411a449811526b371d397a8dbb7/app/src/main/kotlin/com/lambdarc/litememo/ui/viewmodel/MainViewModel.kt#L72)

[`docs/app-lock.md`](app-lock.md) の「認証中に Activity が再生成されても、認証はやり直しになりません」は、androidx が復元する `BiometricPrompt` の経路にだけ当てはまります。`KeyguardManager` の確認画面を直接開く経路は含まれないため、検証結果に合わせて記述を見直します。

## エミュレーターでの再現結果

API 37 のエミュレーターで `devDebug` ビルドを使い、2026年11月22日に確認しました。

- 指摘1: 再現しました。画像3枚を添付したメモを54 MBの ZIP にエクスポートし、インポート開始直後にボトムナビでホームへ移動してメモを開きました。インポートは約1秒で完了し、その後に本文を変更して保存すると画像ディレクトリが空になりました。DB には古いファイル名が残り、エクスポートは「Export failed」になりました。
- 指摘2: 再現しました。40 MBの PNG を添付して保存でき、エクスポートは保存先の選択前に「Export failed」になりました。
- 指摘4: 再現しました。選択中に「Favorites」へ切り替えると、空の一覧のまま「1 selected」が残りました。共有は無反応で、ごみ箱ボタンで非表示のメモがごみ箱へ移動しました。
- 指摘5: 目視では確認できませんでした。ずれは数フレーム程度で、スクリーンショットでは捉えられませんでした。
- 指摘6: 再現しました。一致する語を入力した直後に「No search results」が表示され、その後に結果が表示されました。
- 指摘3と Issue #168: 未確認です。指摘3には端末の画面ロックの設定が、Issue #168 には API 28〜29 の端末が必要です。

## 対応状況

`feature/review-findings` で次のとおり対応しました。

- 指摘1: 編集画面の保存で、最後に読み込んだか保存したメモと現在値を同一 transaction 内で照合します。不一致なら保存せず、再読込のダイアログを出します。
- 指摘2: 添付時に画像1枚の上限（32 MiB）を適用し、超えた画像は専用のメッセージで拒否します。既に保存されている大きな画像と、合計サイズ・枚数の超過は未対応です。
- 指摘3: ロック中も `LiteMemoApp` の保存状態を保持し、解錠後にロック前の画面とファイル選択結果から再開します。
- 指摘4: 通常一覧でフィルターを変更したときに選択を解除します。
- 指摘5: 取得結果を対象の日付・月と組にし、選択と一致するまで前の表示を保ちます。
- 指摘6: 現在の検索語の結果待ちを `isAwaitingResults` で表し、その間は「検索結果がありません」を表示しません。
- 指摘7: インポート後の旧画像の削除を `NonCancellable` で実行します。
- Issue #168 の追加検証は未実施です。

## 確認範囲と結果

- 編集・添付、インポート・エクスポート、アプリロックと画面遷移、ホームの一括操作、カレンダー、検索をソースコードで確認しました。
- 指摘は Critical 3件、Suggestion 3件、Nitpick 1件です。PR 全体のマージ可否は対象外です。
- 関連する ViewModel のユニットテストは、AAPT2 の起動エラーでビルドが停止したため実行できませんでした。
