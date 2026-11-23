# value object

Domain の意味を持つ値を、意図と制約を持つ型に閉じる。

## 確認する対象

- `domain/model/value/` の既存 value object と、`domain/model/` 直下に置かれた value class
- その値を受け渡す `domain/model/`・`domain/usecase/` と、`data/mapper/` の変換

## 実装時の注意

- 不変条件を持つ値（例: `TagName` などの制約付き value object）は生成時に `require` で検証し、不正な値を後段へ流さない。
- 制約検証を持たず正規化や型安全ラッパーのみを担う型（例: `MemoTitle` の前後空白 trim、`MemoBody` の型安全ラップ）もあるため、無条件に空文字拒否などの制約を足さず仕様に合わせる。
- 意味のある値は primitive のまま UI / data へ広げず、必要に応じて value object に寄せる（正本は [`docs/architecture.md`](../../../../docs/architecture.md#value-object)）。
- `require` の例外メッセージを UI 表示文言と混ぜない。

## テスト判断

- 制約付き value object は生成の成功・境界・失敗を、正規化・ラッパーのみの型は正規化や値保持の挙動を JVM Unit Test で押さえる。
- primitive との往復変換が必要な場合、mapper 側のテストは data 層で扱う。

## 検証観点

- 制約付きの型は不正値で確実に生成失敗し、正規化・ラッパーのみの型は許容値を誤って拒否していないか。
- 既存の等価な value object と役割が重複していないか。
