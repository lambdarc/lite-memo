# UseCase

1 操作 1 UseCase で、validation と business rule を UI / data へ漏らさない。

## 確認する対象

- `domain/usecase/` の既存 UseCase と命名（`Observe` / `Get` / `Save` / `Delete` / `Apply` など）
- 依存する Repository interface / provider / value object

## 実装時の注意

- 1 つの UseCase は 1 つの明確な操作を表す。
- 公開する操作は `operator fun invoke` で表す。
- 処理が単一式で、その式の評価結果をそのまま返す場合は式形式を使う。
- validation、ローカル変数、分岐、早期 return を含む場合や、実行した式とは異なる値を返す場合はブロック形式を使う。
- `Unit` 以外を返す場合は戻り値型を明示し、`Unit` は明示しない。
- UI から Repository 実装を直接呼ばせず、UseCase を境界に置く。
- Flow を返す観測系と、suspend の実行系を混ぜない。
- business rule を UI / data 側へ再実装しない。

## テスト判断

- 分岐・境界・失敗経路を JVM Unit Test で押さえる。
- Repository interface は fake / MockK で差し替える。

## 検証観点

- UI の入口（ViewModel / Glance）が Repository interface や provider を直接持たず、UseCase 経由になっているか。
- 同じ操作の UseCase を重複させていないか。
- 例外・空・境界時の振る舞いが定義されているか。
