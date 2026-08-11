# CLAUDE.md

Lite Memo で Claude Code が最初に読む入口です。
ここには地図だけを書き、詳細は `docs/` 配下の文書へ分けます。

## 技術文書

- [`docs/project-overview.md`](docs/project-overview.md): プロジェクト概要 / 主要な確認先 / 技術スタック
- [`docs/architecture.md`](docs/architecture.md): Clean Architecture / MVVM の構造方針
- [`docs/data-model.md`](docs/data-model.md): Room のテーブル構成 / 論理削除 / クエリの制約
- [`docs/export-import-format.md`](docs/export-import-format.md): ZIP アーカイブの形式 / version / 検証と衝突解決
- [`docs/app-lock.md`](docs/app-lock.md): アプリロックの発動条件 / 認証経路 / 設定の切り替え
- [`docs/widget.md`](docs/widget.md): Glance ウィジェットの構成 / データ取得 / 更新の契機
- [`docs/implementation-guidelines.md`](docs/implementation-guidelines.md): 実装時の基本方針
- [`docs/development-setup.md`](docs/development-setup.md): 開発環境セットアップ / Git フック / 静的解析 / ローカル検証
- [`docs/ci.md`](docs/ci.md): CI の実行条件 / job 構成 / カバレッジ / キャッシュ / 命名規約
- [`docs/unit-test.md`](docs/unit-test.md): Unit Test の方針
- [`docs/review.md`](docs/review.md): コードレビューの形式

## AI作業用 Skill

作業の種類に応じて、必要な Skill を最初に確認します。
Claude Code 向け skill は `.claude/skills/` で管理する。Codex 向けの `.agents/skills/` とは自動同期しない。

- `ui-implementation`: Compose / ViewModel / UI state / 画面テスト
  - [`.claude/skills/ui-implementation/SKILL.md`](.claude/skills/ui-implementation/SKILL.md)
- `domain-implementation`: model / value object / UseCase / Repository interface
  - [`.claude/skills/domain-implementation/SKILL.md`](.claude/skills/domain-implementation/SKILL.md)
- `data-implementation`: Repository 実装 / mapper / DataStore / export-import
  - [`.claude/skills/data-implementation/SKILL.md`](.claude/skills/data-implementation/SKILL.md)
- `db-implementation`: Room entity / DAO / migration / schema
  - [`.claude/skills/db-implementation/SKILL.md`](.claude/skills/db-implementation/SKILL.md)
- `test-implementation`: Unit Test / androidTest / coroutine / Flow 検証
  - [`.claude/skills/test-implementation/SKILL.md`](.claude/skills/test-implementation/SKILL.md)
- `implementation-review`: 実装後レビューと指摘整理
  - [`.claude/skills/implementation-review/SKILL.md`](.claude/skills/implementation-review/SKILL.md)

## コーディング規約

実装前に、対象へ該当する規約を確認します。

- [`.claude/rules/comments.md`](.claude/rules/comments.md): コメントの方針（本番コードは読み取れない制約のみ / テストは AAA ラベルと検証観点）

## 最低限の前提

- Lite Memo は Android 向けの軽量メモアプリ
- パッケージ名は `com.lambdarc.litememo`
- メインモジュールは `:app`
- UI は Kotlin / Jetpack Compose / Material 3 を軸にする
- 構造は Clean Architecture + MVVM を軸にする
- 未導入の技術は、実装済みとして扱わない
