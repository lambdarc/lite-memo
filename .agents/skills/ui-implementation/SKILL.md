---
name: ui-implementation
description: 画面の表示、操作、遷移、表示文言、アプリロック UI、ホーム画面ウィジェット（Glance）を変える依頼で使う。Lite Memo の UI 層の Compose screen、Route、ViewModel、UI state、UI event、Navigation Compose、Glance ウィジェット、strings.xml を担当する。
---

# 目的

Lite Memo の UI 層を、既存の Compose / MVVM 構成に沿って変更する。

# 最初に確認するもの

- [`docs/architecture.md`](../../../docs/architecture.md)
- [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
- [`app/src/main/kotlin/com/lambdarc/litememo/ui/`](../../../app/src/main/kotlin/com/lambdarc/litememo/ui/)（変更対象周辺のコード）
- [`app/src/main/res/values/strings.xml`](../../../app/src/main/res/values/strings.xml)（日本語・デフォルト）と [`app/src/main/res/values-en/strings.xml`](../../../app/src/main/res/values-en/strings.xml)（英語）
- テストを追加・修正する場合は [`docs/unit-test.md`](../../../docs/unit-test.md)（JVM Unit Test）または [`docs/instrumented-test.md`](../../../docs/instrumented-test.md)（Compose UI Test / instrumented test）
- メモ編集画面を変える場合は [`docs/memo-edit-lifecycle.md`](../../../docs/memo-edit-lifecycle.md)
- アプリロックを変える場合は [`docs/app-lock.md`](../../../docs/app-lock.md)
- ホーム画面ウィジェットを変える場合は [`docs/widget.md`](../../../docs/widget.md)
- 必要に応じて [`app/src/test/kotlin/com/lambdarc/litememo/ui/`](../../../app/src/test/kotlin/com/lambdarc/litememo/ui/) または [`app/src/androidTest/kotlin/com/lambdarc/litememo/ui/`](../../../app/src/androidTest/kotlin/com/lambdarc/litememo/ui/)

# 役割クラスと参照先

変更対象の役割クラスを見分け、該当する reference だけを読んでから実装する。
薄い役割は reference を作らず docs に委ねる。

| 役割クラス | 参照 |
| --- | --- |
| screen / component（`XxxRoute` / `XxxScreen` / 再利用部品） | [`references/screen.md`](references/screen.md) |
| viewmodel | [`references/viewmodel.md`](references/viewmodel.md) |
| UI state / UI model | [`references/ui-state.md`](references/ui-state.md) |
| UI event（UI state / UI callback / Channel の使い分け） | [`references/one-shot-event.md`](references/one-shot-event.md) |
| navigation | [`references/navigation.md`](references/navigation.md) |
| Compose UI Test | [`references/compose-ui-test.md`](references/compose-ui-test.md) |
| Preview / strings / theme / util | reference なし。`docs/implementation-guidelines.md` の UI / Localization 方針と `docs/architecture.md` の `ui` 構成に従う（`XxxRoute` の配線は screen 行を参照） |

# 手順

1. 変更対象の役割クラスを判定し、該当 reference を読む。
2. `ui -> domain <- data` の依存方向と、ViewModel 経由の domain アクセスを保つ。
3. reference の観点に沿って実装する。
4. 変更に応じて ViewModel test または Compose UI Test の要否を判断する。
5. 変更した振る舞いと影響範囲に応じて検証を選ぶ。選択方針と実行コマンドは [`docs/development-setup.md`](../../../docs/development-setup.md) の「ローカルでの検証」に従う。
6. 変更内容、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- `ui` から data model、Room entity、Repository implementation を直接参照しない。
- UI 変更が Domain、Data、DB へ波及するときは、変更対象に対応する Skill を併用する。
- ViewModel に Android View や Context 依存を直接持ち込まない。
- 固定色の直書きを避け、Material 3 theme とライト / ダーク対応を優先する。
- 表示文言を追加・変更するときは日本語（`values/strings.xml`）と英語（`values-en/strings.xml`）の双方を更新する。
- Navigation 追加時は route 名、引数、戻る挙動を明確にする。
