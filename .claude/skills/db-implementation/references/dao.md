# DAO

`data/local/dao` の DAO で、既存の絞り込みと矛盾しない query を提供する。

## 確認する対象

- `data/local/dao/`（`MemoDao`、`MemoBulkDao`、`TagDao`）と `data/local/model/`（`MemoWithRefs`、`MemoSummaryProjection`）
- active / trash / search / calendar など既存の絞り込み条件

## 実装時の注意

- 既存の絞り込み・並び順・関連読み込み（`@Relation` / `MemoWithRefs`）と矛盾させない。
- 返す型は Flow 観測系と suspend 実行系を用途で分ける。
- 検索・カレンダー等の条件を DAO と UseCase で二重定義しない。

## テスト判断

- query の絞り込み・並びは DAO instrumented test で押さえる。

## 検証観点

- 追加 query が既存 index を活かせているか。
- trash / active の境界条件が既存と一致するか。
