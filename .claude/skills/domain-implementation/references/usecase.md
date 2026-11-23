# UseCase

1 操作 1 UseCase で、validation と business rule を UI / data へ漏らさない。

## 確認する対象

- `domain/usecase/` の既存 UseCase と命名パターン
- 依存する `domain/repository/`・`domain/provider/`・`domain/model/`・`domain/exception/`

## 実装時の注意

- 1 つの UseCase は 1 つの明確な操作を表し、`@Inject` コンストラクタと `operator fun invoke` で公開する。引数が多い書き込み操作などは `domain/model/` の `*Command` にまとめてよい。
- 戻り値に `Result` ラッパーは使わず値を直接返す。業務ルール違反や検証エラーは `require` / `requireNotNull`、または呼び出し側で区別が必要な場合に `domain/exception/` の専用例外を送出する。
- 観測系（`Flow`）、非同期実行系（`suspend`）に加え、メモリ上の変換・整形・パス解決などの同期（非 `suspend`・非 `Flow`）UseCase も許容する。1 つの UseCase に観測系と実行系を混ぜない。
- 式形式・ブロック形式や戻り値型の明示などの記述規約は [`docs/implementation-guidelines.md`](../../../../docs/implementation-guidelines.md) に従う。
- UI entry point から Repository interface や provider を直接呼ばせず、単純委譲でも UseCase を境界に置く（[`docs/architecture.md`](../../../../docs/architecture.md#usecase)）。
- business rule を UI / data 側へ再実装しない。

## テスト判断

- 分岐・境界・失敗経路を JVM Unit Test で押さえる。
- Repository interface / provider は fake / MockK で差し替える。

## 検証観点

- UI の入口（ViewModel / Glance）が Repository interface や provider を直接持たず、UseCase 経由になっているか。
- 同じ操作の UseCase を重複させていないか。
- 例外・空・境界時の振る舞いが定義されているか。
