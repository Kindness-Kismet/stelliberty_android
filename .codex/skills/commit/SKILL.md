---
name: commit
description: Write English commit messages and create explicitly requested local commits for Stelliberty Android. Use when drafting a commit message, committing task changes, or preparing an authorized push. Does not authorize staging, committing, or pushing by itself.
---

# Commit

Read [AGENTS.md](../../../AGENTS.md) and inspect the actual changes before writing a message. Commit subjects and bodies are English; discussion with the user stays in the user's language.

## Authorization

- 用户要求本地提交时执行暂存和提交；更新版本号的请求按 [version-bump 技能](../version-bump/SKILL.md) 包含本次版本提交授权。只要求撰写文案或明确要求不提交时，保留修改。
- Push only after explicit confirmation covering the outgoing commits, remote, and destination branch. Permission to edit, commit, or open a pull request is separate from permission to push.
- Finish the local result, report changes and validation, and list the whole outgoing range before asking to push. A confirmation already given for the same result and destination stands; ask again when the scope or destination changes.
- Default to one focused local commit per task. Release metadata follows the [version-bump skill](../version-bump/SKILL.md).
- Amending, rewriting shared history, force pushing, merging, tagging, and publishing each need their own explicit authorization.

## Select changes

Start at the repository root with `git status --short --branch`, `git diff --stat`, `git diff --cached --stat`, and a short recent log, then read the relevant full diffs, including new files.

- Commit only the task's changes. Other staged and unstaged work stays in place, including unrelated edits inside files the task touches.
- Stage explicit paths or selected hunks, and review the full staged diff before committing. Use an isolated index when the user's index must stay intact; ask when ownership or scope is unclear.
- For a partial file, write `git diff -U0 -- <path>` to a temporary patch, keep the wanted hunks with their headers, and apply it with `git apply --cached --unidiff-zero` (`git add -p` needs a terminal). When the task's lines interleave with other work, write the intended content as a blob with `git hash-object -w` and place it with `git update-index --cacheinfo`. Afterwards the index holds exactly the selected changes and the working tree keeps the rest.
- The staged subset stands on its own: every file it references is tracked, and every documented behavior is committed together with its implementation.
- New files pass `.gitignore` and are staged normally. Downloaded toolchains, launchers, GeoIP data, native binaries, APKs, caches, and temporary reports stay untracked; Baseline Profiles are tracked when the task changes them.
- Keep `local.properties`, signing keys, and credentials out of reads, output, and commits. Include a submodule pointer change only when the task requires it, after inspecting it.
- Keep the authoring machine out of committed text: absolute paths, drive letters, home directories, user names, device serials, personal addresses, and real node, subscription, or account data. This covers comments and examples as well as code, and illustrations as well as real values. Derive paths from `BASH_SOURCE`, `Path.home()`, or the project root, and write placeholders such as `<drive>` or `Example` in prose.
- Skill changes keep `.claude/skills/` and `.codex/skills/` byte-identical: `diff -r .claude/skills .codex/skills` prints nothing. Mirror by copying whole files; script invocations in both copies use the `.claude/skills/` prefix. After partial staging, compare the staged blobs (`git show :<path>`), since the index is what the commit records.
- Skills state current constraints and their reasons. When code changes, rewrite the affected entry in place and delete entries that no longer apply; fix history belongs in commit messages.

## Write the message

Use `<scope>: <summary>`. The complete subject is at most 72 characters, uses sentence case after the colon, starts with an imperative verb, and ends without a period.

| Scope | Use |
|---|---|
| `home` | Home screen behavior and status |
| `proxy` | Proxy groups, node selection, and latency presentation |
| `subscription` | Subscription import, updates, and management |
| `settings` | Settings, appearance preferences, and the About screen |
| `service` | Proxy lifecycle, tunnels, and Android service integration |
| `native` | Go, C, JNI, and mihomo integration |
| `build` | Build tooling, CI, packaging, and release metadata |
| `docs` | Documentation and agent skills |
| `fix` | Cross-cutting fixes without a more precise scope |
| `chore(deps)` | Dependency version or coordinate updates |

Choose the narrowest scope that describes the purpose, and write `chore(deps): ...` literally.

Describe the resulting behavior or concrete correction. A self-explanatory change needs only the subject; otherwise add a body after a blank line, limited to a root cause, invariant, or tradeoff the diff leaves unclear. File lists, session narrative, and unverified performance or validation claims stay out. Add an issue-closing reference only when the task resolves that issue.

Examples of subjects:

```text
subscription: Preserve cancellation during profile imports
build: Rebuild the native core when the Go toolchain changes
docs: Define commit and pull request conventions
```

Beta release notes use commit subjects, so each subject stands alone. For multiline messages, write the exact UTF-8 text to a temporary file and use `git commit --file`, preserving newlines and backticks. Keep scratch files outside the working tree.

## Validate and finish

Use the validation required by [AGENTS.md](../../../AGENTS.md) and the affected subsystem skill. Always run `git diff --check`; when committing, also run `git diff --cached --check`. On Windows, `* text=auto` prints an LF/CRLF notice for each touched file; judge by the actual `--check` output and exit status.

| Change | Relevant validation |
|---|---|
| Documentation or skills only | Content, links, allowlist, and skill mirror parity; Android builds are unnecessary |
| Kotlin | `python scripts/build.py compile --dev` |
| Native code, build configuration, or packaging | `python scripts/build.py --dev` plus checks required by `native-build` |
| New composables | Also generate and inspect the Compose stability report as required by `AGENTS.md`, then remove the temporary configuration |
| Device behavior requiring verification | Use the `debug-app` skill and report the scenarios actually exercised |

Run `python scripts/prebuild.py` before the first build or when resources are missing. Reuse successful checks from the same final state. Report failures and skipped checks accurately, and resolve task-related failures before committing unless the user asks for an incomplete checkpoint.

After an authorized commit, inspect its message and file summary and run `git status --short --branch`. Report the commit ID, a concise summary, validation, and push status. Without commit authorization, deliver the draft and leave the index and history as they are.

## Push after confirmation

Before any push, verify the remote, destination, and current remote tip, and review every outgoing commit, including older local ones. For a new remote branch, compare against the intended base. Resolve unexpected outgoing changes before asking.

Explain the release effect: product changes pushed to `beta` can publish a beta; an application version change on `main` can publish a stable release. Check the current conditions in `.github/workflows/`.

After confirmation, push exactly the approved commit to the approved destination with an explicit remote and refspec — no tags, extra branches, `--all`, `--mirror`, or force options. If the push is rejected, inspect the cause and report the next step; a rejection grants no permission to force, rebase, or retarget. Verify the remote tip afterwards.
