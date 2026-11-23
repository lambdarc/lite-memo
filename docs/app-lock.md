# App Lock

この文書は Lite Memo のアプリロック（生体認証 / デバイス認証）の挙動をまとめます。
実装が `MainActivity`、`MainViewModel`、`ui/auth`、設定画面、DataStore に跨るため、
どこで何が決まるかをここへ集約します。

## ロックが掛かる条件

- アプリロックが有効なとき、`MainActivity.onStop()` でロック状態へ移す
- 猶予時間もタイムアウトも無い。1 秒で戻っても再認証が要る
- 画面回転などの構成変更では掛からない。`onStop()` が `isChangingConfigurations` で除外する
- 認証中に停止した場合もロック状態へは移さず、認証中のまま据え置く
- 復帰時は `onStart()` が動き、ロック中か利用不可のときだけ認証を要求する
- 起動直後は、設定値の最初の読み出しで有効だった場合に認証を要求する

「一定時間バックグラウンドにいたらロック」ではなく、離れたらロックです。
画像ピッカー、ファイル選択、共有シートなどの外部 Activity を起動した場合も、`MainActivity.onStop()` で例外なくロックが掛かります。
ただし構成変更と認証中の 2 つは除外されるため、あらゆる停止でロックが掛かるわけではありません。
画面消灯そのものは条件ではありません。Activity が停止しない限りロックは掛かりません。

## ロック状態の置き場所

- ロック中かどうかは `MainViewModel` の `StateFlow` にだけ持ち、永続化しない
- 永続化するのは設定の on / off だけで、DataStore の `app_lock_enabled` に持つ。既定は無効
- プロセス再生成後は状態が読み込み中から始まり、設定が有効なら改めてロックされる
- DataStore の読み出しが `IOException` で失敗したときは、未設定と区別できないため有効として扱いロックを維持する。他の例外は握り潰さず送出する。テーマや並び順が既定値へ落ちるのとは扱いが異なる
- 画面回転では ViewModel が生き残り、かつ上記の除外があるため再ロックしない

## ロック中の画面

`AppLockScreen` は Navigation の遷移先ではありません。
`MainActivity` が解錠済みかどうかで分岐し、ロック中は NavHost 自体を composition に載せません。
背後にメモが透けることはなく、戻る操作で回り込むこともできません。

composition から外す前に、`LiteMemoApp` の保存可能な状態を `SaveableStateHolder` に退避します。
解錠後はバックスタックと各画面の ViewModel、Activity Result のランチャーが元に戻り、ロック前の画面から再開します。
設定画面からファイル選択を開いた間にロックが掛かっても、選んだ ZIP のインポート確認を続けられます。

`FLAG_SECURE` は「ロック中かどうか」ではなく「アプリロックが有効かどうか」で切り替えます。
有効にしている間は、解錠済みで操作している最中もスクリーンショットと recents のプレビューが抑止されます。

最近のメモウィジェットも「ロック中かどうか」ではなく設定値で表示を切り替えます。
アプリロックが有効な間は、アプリ内を解錠して操作している最中もタイトル、本文の抜粋、お気に入りを表示しません。
設定値の読み込みが完了するまでは表示せず、読み出しに失敗した場合も有効として扱います。

## 認証の要求先

端末と API レベルで 3 経路に分かれます。

- API 30 以上: `BiometricPrompt` に `BIOMETRIC_STRONG` と `DEVICE_CREDENTIAL` の両方を許可する。`DEVICE_CREDENTIAL` を含めると negative button を設定できないため、設定していない
- API 30 未満で `BIOMETRIC_WEAK` が使える: `BiometricPrompt` を `BIOMETRIC_WEAK` で使い、negative button を出す。文言は端末に認証情報があればデバイス認証への切り替え、無ければキャンセル
- それ以外: `KeyguardManager` の確認画面を直接開く

生体認証を登録していなくても、端末に PIN / パターン / パスワードが設定されていれば解錠できます。

事前判定 `canAuthenticate()` は、API 30 以上では `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` が成功するか、API 30 未満では `BiometricManager.canAuthenticate(BIOMETRIC_WEAK)` の成功または `KeyguardManager.isDeviceSecure` を満たすかを見ます。
失敗理由は区別しておらず、未登録・ハードウェア非搭載・一時的な利用不可を含めて判定に失敗した場合はすべて認証情報なし（`NO_DEVICE_CREDENTIAL`）扱いとなり、再試行ではなくセキュリティ設定を開くボタンを出します。

## 守れる範囲

アプリロックは、解錠済みの端末を操作できるが再認証はできない誰か（端末を渡された、解錠したまま置かれたなど）が、
停止したアプリを開き直すことを防ぐための UX 上のゲートです。
ロックは `onStop()` で掛かるため、アプリを前面に表示したまま渡された場合など、停止していない間のメモは保護しません。
端末のプロセスへ介入できる攻撃者に対する防御ではありません。

メモの本文、画像、DataStore はいずれも平文で保存しています。
`BiometricPrompt` へ `CryptoObject` を渡していないのはこのためです。
`KeyStore` の鍵で `onAuthenticationSucceeded` を裏付けても、
プロンプトを迂回できる攻撃者はそもそも `databases/` を直接読めるため、守れる対象が増えません。

導入しようとした場合の制約も残しておきます。
`CryptoObject` は `BIOMETRIC_WEAK` と併用できず、`KeyguardManager` の確認画面にも存在しないため、
掛かるのは API 30 以上の経路だけです。
`setUserAuthenticationRequired` の鍵は生体の再登録で無効化されるため、復旧の導線も別に要ります。

CodeQL の `java/android/insecure-local-authentication` はこの理由で dismiss しています。
平文保存をやめるなら、この判断ごと見直します。

## 失敗の扱い

認証結果は成功 / 失敗 / キャンセル / 認証情報なし / 利用不可に分かれます。

- 失敗とキャンセルはロック状態のまま、文言を変えて再試行ボタンを出す
- 認証情報なしのときだけ、再試行ではなくセキュリティ設定への導線を出す
- 利用不可（`ERROR_HW_UNAVAILABLE` / `ERROR_HW_NOT_PRESENT` / `ERROR_SECURITY_UPDATE_REQUIRED`）は状態を `UNAVAILABLE` にし、専用の文言と再試行ボタンを出す。復帰時の `onStart()` はこの状態でも認証を要求する
- 生体認証のロックアウト（`ERROR_LOCKOUT` / `ERROR_LOCKOUT_PERMANENT`）は個別に扱っておらず、一般の失敗と同じ表示になる

ロックアウトを区別していないのは既知の制約です。
API 30 以上は同じプロンプト内でデバイス認証へ切り替えられるため詰まりにくいものの、
API 30 未満の生体認証のみの経路では、利用者にロックアウトだと分かる表示が出ません。

## 設定の切り替え

有効化と無効化で扱いが違います。

- 有効化は認証の成功が要る。成功以外の結果では設定値を書き換えない
- 有効化に成功した直後は、現在のセッションを即座にロックせず `UNLOCKED` のまま維持する（`FLAG_SECURE` とウィジェットの秘匿表示は即座に切り替わる）。ロックが掛かるのは次回の `onStop()` から
- 無効化は認証を要求せず、トグル操作だけで即座に反映する

結果として、端末が解錠されていて設定画面まで開ける状態なら、アプリロックは確認なしで外せます。
設定画面へ到達するには一度アプリを解錠している、という前提に依存した設計です。

なお、端末に認証情報が無い状態では有効化の経路が認証成功に到達しないため、設定を on にできません。

## 実装上の制約

- `BiometricPrompt` が `FragmentActivity` を、`KeyguardManager` のデバイス認証確認画面が `ActivityResultLauncher` を要求するため、`AppLockAuthenticator` は ViewModel ではなく `ui/auth` に置く。ViewModel からは要求イベントを送り、Activity 側が受けて認証を実行する
- `AppLockAuthenticator` は `onCreate` で作り直すが、`BiometricPrompt` 経路では認証シートと結果の受け渡しを androidx 側が Activity 再生成をまたいで保つ
- ごみ箱の期限切れ削除は、解錠の成功時と、アプリロックが無効だと分かった時点が入口になる。画面の描画完了とは連動しない
- この削除の 1 回制限は `MainViewModel` のフィールドで持つ。保証されるのは ViewModel インスタンス単位であり、プロセス単位ではない
- `TrashViewModel` の生成時と再試行時にも同じ削除を実行する。こちらに 1 回制限は無い
- `MainActivity` は `singleTop` のため、既存 `MainActivity` がタスクのトップにある状態でのウィジェット起動では `onNewIntent` が呼ばれる（コールドスタート時は `onCreate`）。ロックの状態機械は Activity ではなく `MainViewModel` の生存期間に紐づくため、`onNewIntent` 経路では初期化されない

`BiometricPrompt` 経路では、認証中に Activity が再生成されても認証はやり直しになりません。

`AppLockAuthenticator` は `BiometricPrompt` を `authenticate()` の中で生成するため、
再生成後の新しいインスタンスには前の認証の情報が残りません。
それでも androidx の `BiometricPrompt` が内部で認証シートを復元し、
結果は再生成前に登録されたコールバックへ届きます。
`MainViewModel` は再生成をまたいで生きているため、そこから状態も更新されます。

画面回転で確認しています。`BiometricPrompt` の認証シートを表示した状態で回転させると、
横画面向けのシートへ切り替わったうえで解錠まで進みます。
この経路では認証中の状態が取り残されて解錠できなくなることはありません。

コードだけを読むと、`AppLockAuthenticator` が認証中の情報を持たないまま作り直されるため、
`onAppStarted()` も `requestUnlock()` も認証中は何もしないことと合わせて、
解錠できなくなるように見えます。`BiometricPrompt` 経路では実際には androidx 側が復元するため、その状態にはなりません。
