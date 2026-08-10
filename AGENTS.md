# AGENTS.md

Lite Memo で Codex が最初に読む入口です。
ここには地図だけを書き、詳細は `docs/` 配下の文書へ分けます。

## 技術文書

- [`docs/project-overview.md`](docs/project-overview.md): プロジェクト概要 / 主要な確認先 / 技術スタック
- [`docs/architecture.md`](docs/architecture.md): Clean Architecture / MVVM の構造方針
- [`docs/implementation-guidelines.md`](docs/implementation-guidelines.md): 実装時の基本方針
- [`docs/development-setup.md`](docs/development-setup.md): 開発環境セットアップ / Git フック / 静的解析 / ローカル検証
- [`docs/ci.md`](docs/ci.md): CI の実行条件 / job 構成 / カバレッジ / キャッシュ / 命名規約
- [`docs/unit-test.md`](docs/unit-test.md): Unit Test の方針
- [`docs/review.md`](docs/review.md): コードレビューの形式

## AI作業用 Skill

作業の種類に応じて、必要な Skill を最初に確認します。
Codex 向け skill は `.agents/skills/` で管理する。Claude Code 向けの `.claude/skills/` とは自動同期しない。

- `ui-implementation`: Compose / ViewModel / UI state / 画面テスト
  - [`.agents/skills/ui-implementation/SKILL.md`](.agents/skills/ui-implementation/SKILL.md)
- `domain-implementation`: model / value object / UseCase / Repository interface
  - [`.agents/skills/domain-implementation/SKILL.md`](.agents/skills/domain-implementation/SKILL.md)
- `data-implementation`: Repository 実装 / mapper / DataStore / export-import
  - [`.agents/skills/data-implementation/SKILL.md`](.agents/skills/data-implementation/SKILL.md)
- `db-implementation`: Room entity / DAO / migration / schema
  - [`.agents/skills/db-implementation/SKILL.md`](.agents/skills/db-implementation/SKILL.md)
- `test-implementation`: Unit Test / androidTest / coroutine / Flow 検証
  - [`.agents/skills/test-implementation/SKILL.md`](.agents/skills/test-implementation/SKILL.md)
- `implementation-review`: 実装後レビューと指摘整理
  - [`.agents/skills/implementation-review/SKILL.md`](.agents/skills/implementation-review/SKILL.md)

## 最低限の前提

- Lite Memo は Android 向けの軽量メモアプリ
- パッケージ名は `com.lambdarc.litememo`
- メインモジュールは `:app`
- UI は Kotlin / Jetpack Compose / Material 3 を軸にする
- 構造は Clean Architecture + MVVM を軸にする
- 未導入の技術は、実装済みとして扱わない
