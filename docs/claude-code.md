# Claude Code

この文書は Claude Code 固有の `CLAUDE.md` import と skill 管理を扱う。

## Claude Code の共通指示

`CLAUDE.md` は `@AGENTS.md` を import する。共通指示は `AGENTS.md` に集約し、
Claude Code 固有の指示だけを `CLAUDE.md` に追加する。

## Claude Code 向け skill

Claude Code 向け skill は `.claude/skills/` で直接管理する。
Codex 向けの `.agents/skills/` との自動同期は行わない。

共通する方針を変更するときは、必要に応じて両方の skill を個別に確認して編集する。
各環境に固有のメタデータや記述は、それぞれのディレクトリ内で管理する。
