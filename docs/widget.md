# Home Screen Widget

この文書は Lite Memo のホーム画面ウィジェット（Jetpack Glance）の構成と更新の仕組みをまとめます。
ウィジェットは ViewModel も Route も持たない別の UI entry point で、他の画面と読み方が違います。
配置の方針は [`docs/architecture.md`](architecture.md) の `ui/widget` を正本とします。

## 2 つのウィジェット

- **新規メモ**: タップで新規メモの編集へ入るだけのウィジェット。1×1 セル、リサイズ不可。動的な表示を持たない
- **最近のメモ**: 直近のメモを一覧するウィジェット。初期 3×2 セル、縦横ともリサイズ可能

最近のメモだけが `updatePeriodMillis` を持ち、30 分に設定しています。新規メモ側には設定していません。

## データの取得

最近のメモは `WidgetMemoLoader` が取得します。ViewModel を挟まず、UseCase を直接呼びます。

- 取得は `ObserveRecentMemosUseCase` 経由で、件数は `RECENT_MEMOS_LIMIT` の 8 件
- ごみ箱のメモは含めない。お気に入りを優先し、更新日時、作成日時、`id` の順で並べる
- クエリの並び順と tie-break は [`docs/data-model.md`](data-model.md) の制約に従う。変更すると instrumented test が落ちる

キャッシュは持ちません。`provideGlance` が最初に 1 回だけ同期的に読み込んで初期値にし、
そのあとは Room の Flow をそのまま購読します。初期値を先に用意しているのは、最初の描画が空になるのを避けるためです。

## 依存の受け取り

ウィジェットでは `@AndroidEntryPoint` を使えません。
`GlanceAppWidget` は Glance が管理するクラスで、receiver は manifest 経由でウィジェットホストが生成するため、
Hilt の生成コードによる注入経路に乗らないからです。

代わりに `ui/widget/di` の `@EntryPoint` から `EntryPointAccessors.fromApplication` で取り出します。
公開しているのは `ObserveRecentMemosUseCase` の 1 つだけで、取得箇所も最近のメモの `provideGlance` 1 箇所です。
新規メモ側はデータ依存が無いため、この経路を使いません。

## 更新の契機

ここが最も分かりにくい部分です。更新の経路は 2 つあり、性質が違います。

**アプリのプロセスが生きている間の更新**は `LiteMemoApplication` が担います。
最近のメモと同じ Flow を購読し、次の順で加工してから更新を掛けます。

- 失敗時は警告を出して 500 ms 待ってから購読し直す
- 最初の 1 件を捨てる。アプリの起動それ自体で更新が走らないようにするため
- 同じ内容の連続を除く
- 500 ms 静まるまで待ってから通す。短時間の連続変更を 1 回にまとめるため

この購読は application scope で動き、プロセスの生存期間を通じて生きています。
画面が表示されているかどうかとは無関係です。

実際の更新は `WidgetRefresher` が行い、配置済みのウィジェットが無ければ何もせず、
あれば最近のメモウィジェットへ `updateAll` を掛けます。
`WidgetRefresher` の呼び出し元は `LiteMemoApplication` のこの 1 箇所だけで、ViewModel からは呼びません。
新規メモウィジェットはこの経路の対象外です。

**プロセスが死んでいる間の更新**は OS 側の `updatePeriodMillis` に委ねます。
おおよそ 30 分ごとに `provideGlance` が呼ばれ、その都度読み直します。
ただし Android は発火時刻を保証せず、端末の状態によっては後ろへずれます。
30 分を上限として必ず追いつくとは考えず、それ以上遅れうる前提で扱います。

なお 500 ms という値は、再購読までの待ちと変更のまとめの両方に同じ定数を使っています。
この値を選んだ理由はコードに残っていません。

## タップ時の遷移

deep link ではなく、`MainActivity` を宛先にした明示的な `Intent` で遷移します。

- 共通の action を持たせ、`widget_target` に `new_memo` か `open_memo` を入れる
- メモを開く場合は `widget_memo_id` にメモ ID を入れる
- `MainActivity` は `onCreate`（初回のみ）と `onNewIntent` でこれを読み、`MainViewModel` へ渡す
- `MainViewModel` は `Channel.CONFLATED` で `LiteMemoApp` へ送り、`LaunchedEffect` が `navController` を動かす

メモを開く Intent には `litememo://memo/...` の URI も設定されていますが、
遷移の判定に使っているのは extras の方で、URI は参照していません。

`MainActivity` は `singleTop` のため、アプリが起動中のタップでは Activity は作り直されません。

## 表示の組み立て

`WidgetItem` は Glance 表示用のモデルで、ID、タイトル、抜粋、お気に入りの 4 つを持ちます。
Room の projection から直接は作らず、domain の `MemoSummary` を経由します。

タイトルと抜粋の切り出しは `WidgetMemoLoader` の中で行います。

- タイトルがあればそれを使い、本文の空でない行をつないで抜粋にする
- タイトルが空なら、本文の最初の空でない行をタイトルに繰り上げ、残りを抜粋にする
- タイトルは 50 文字、抜粋は 80 文字で切る。走査する本文の先頭も一定文字数で打ち切り、長い本文でも処理量が増えないようにする

行はお気に入りのとき星を出し、タイトルが空なら代替の文言を出します。抜粋は空なら行ごと出しません。
1 件も無いときは、タップで新規メモへ入れる空状態を出します。

## 既知の制約

最近のメモウィジェットは、初期読み込みと購読のどちらでも、キャンセル以外の失敗を空リストへ潰します。
クエリが継続的に失敗しても、ウィジェット上は「メモが無い」状態と区別が付きません。
再購読を行うのは `LiteMemoApplication` の経路だけで、これは更新を掛けるためのものです。
ウィジェット自身の描画経路には再試行がありません。
