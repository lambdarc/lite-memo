# Project Overview

この文書は Lite Memo の概要、採用方針と重要な制約、主要な設定ファイルと技術文書の確認先をまとめます。

## 概要

Lite Memo は Android 向けの軽量メモアプリです。

- パッケージ名: `com.lambdarc.litememo`
- ルートプロジェクト名: `Lite Memo`
- メインモジュール: `:app`
- ビルドフレーバー: `dev` / `prod`（端末での手動の動作確認は `dev` を使う。検証コマンドの variant は [`docs/development-setup.md`](development-setup.md) に従う）
- 構造方針: Clean Architecture + MVVM（詳細は [`docs/architecture.md`](architecture.md)）

## 採用方針と重要な制約

- **未導入技術の扱い**: 導入済みの依存関係・プラグイン・バージョンは必ず [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) と [`app/build.gradle.kts`](../app/build.gradle.kts) で確認し、未導入の技術を実装済みとして扱わない
- **既存構成の優先**: 変更前に Gradle とソースを確認し、既存構成を優先して必要以上に大きな再編を行わない
- **UI / ローカライズ**: Kotlin / Jetpack Compose / Material 3 を軸にし、ライト / ダーク対応と日本語 / 英語ローカライズを前提とする（表示文字列の扱いは [`docs/implementation-guidelines.md`](implementation-guidelines.md)）
- **永続化と Export / Import**: 構造化データは Room、軽量設定は DataStore（Preferences）を使う。Export / Import は画像を含む ZIP 形式（内部の manifest JSON に `kotlinx.serialization` を使用）のみ対応し、単独 JSON 形式の Export / Import は非対応（詳細は [`docs/data-model.md`](data-model.md) / [`docs/export-import-format.md`](export-import-format.md)）
- **広告（AdMob）**: ボトムナビゲーション上部に全タブ共通のアンカー型アダプティブバナーを表示する。アプリ ID / 広告ユニット ID は flavor 別の `strings.xml` で管理する（`dev` は `app/src/dev/res` のテスト ID、`prod` は `app/src/prod/res` の本番 ID）
- **Firebase / リリースビルド**: Firebase 設定は flavor ごとに `app/src/<flavor>/google-services.json` へ配置する。release ビルドは R8 minify とリソース圧縮を有効にし、署名鍵・パスワード等の秘密情報はコミットしない（詳細は [`docs/development-setup.md`](development-setup.md)）

## 主要な確認先

### アプリコード・リソース

- `app/src/main/kotlin/com/lambdarc/litememo/`: Kotlin ソース（`ui/`、`domain/`、`data/`、`di/` の責務は [`docs/architecture.md`](architecture.md)）
- `app/src/main/res/`: Android リソース（日本語デフォルト `values/strings.xml`、英語 `values-en/strings.xml`）
- `app/schemas/`: Room のエクスポート済みスキーマ
- `app/src/dev/google-services.json` / `app/src/prod/google-services.json`: flavor 別 Firebase 設定

### ビルド・依存・署名

- [`gradle/libs.versions.toml`](../gradle/libs.versions.toml): 導入ライブラリとプラグインのバージョン管理
- [`app/build.gradle.kts`](../app/build.gradle.kts): SDK レベル、flavor、署名、Lint、Kover 集計対象、依存関係の正本
- [`keystore.properties.example`](../keystore.properties.example): リリース署名設定のサンプル

### 静的解析・Git フック・ローカル検証

- [`.editorconfig`](../.editorconfig): KtLint / Kotlin フォーマット設定
- [`config/detekt/detekt.yml`](../config/detekt/detekt.yml): detekt 静的解析ルール
- [`.githooks/pre-commit`](../.githooks/pre-commit): コミット前の整形・検査フック
- [`fastlane/Fastfile`](../fastlane/Fastfile): ローカル / CI で実行する検証 lane

### CI・セキュリティ・依存更新

- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml): CI ワークフロー（job 構成と実行条件）
- [`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml): CodeQL 静的セキュリティ解析
- [`.github/dependabot.yml`](../.github/dependabot.yml): 依存関係の自動更新設定

## 領域別の正本ドキュメント

- 構造・実装規約: [`docs/architecture.md`](architecture.md) / [`docs/implementation-guidelines.md`](implementation-guidelines.md)
- ドメイン仕様: [`docs/data-model.md`](data-model.md) / [`docs/export-import-format.md`](export-import-format.md) / [`docs/memo-edit-lifecycle.md`](memo-edit-lifecycle.md) / [`docs/app-lock.md`](app-lock.md) / [`docs/widget.md`](widget.md)
- テスト方針: [`docs/unit-test.md`](unit-test.md) / [`docs/instrumented-test.md`](instrumented-test.md)
- 開発環境・CI: [`docs/development-setup.md`](development-setup.md) / [`docs/ci.md`](ci.md)
