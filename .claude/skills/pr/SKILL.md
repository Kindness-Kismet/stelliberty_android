---
name: pr
description: Draft English pull request titles and descriptions, or create and update explicitly requested GitHub pull requests for Stelliberty Android. Use for PR preparation and publication, with project branch and validation rules. Does not authorize pushing, merging, or releasing.
---

# Pull Request

Read [AGENTS.md](../../../AGENTS.md), the contributing section in [README.md](../../../README.md), and the actual branch diff before writing. PR titles and descriptions are English; discussion with the user stays in the user's language.

## Authorization

- A request to draft PR text covers local preparation. Creating or editing a remote PR, including a draft PR, needs an explicit request for that action.
- Push only after explicit confirmation covering the outgoing changes, remote, and destination branch. A request to create or update a PR is separate from permission to push its branch.
- Finish preparation, validation, title, and body before asking for missing push authorization, and present the complete outgoing range and destination. A confirmation already given for the same result and destination stands.
- Publish once the head is already on the approved remote branch, passing it via `--head`, so `gh pr create` (including `--dry-run`) has nothing to push or fork.
- Merging, auto-merge, history rewrites, branch deletion, tags, and release workflows each need their own explicit authorization.

## Establish the comparison

Inspect the working tree, local and remote branches, remotes, and any existing PR for the intended head and base. Read the owner, remote name, and default branch from repository metadata. Fetch refs when needed; fetching is read-only.

| Purpose | Head and base |
|---|---|
| Regular feature, fix, or documentation change | Task branch into `beta` |
| Stable release promotion | `beta` into `main` |

Use an explicitly chosen alternative after checking its consequences. If the documented branches are missing, report the mismatch and prepare the draft; the working tree stays on its current branch and remote branches stay as they are.

For the actual refs, read `git log BASE..HEAD` and `git diff BASE...HEAD`, including submodule changes. The PR describes the whole comparison. Resolve unrelated inherited commits with the user before publication.

Uncommitted changes are absent from a remote PR. Include them in a draft only when they belong to the requested work, mark them as pending, and refresh the text against the final head before publication. Local commits follow the [commit skill](../commit/SKILL.md).

## Write the title and description

Title: the commit skill's `<scope>: <summary>` convention — English, imperative, sentence case, at most 72 characters, no trailing period — covering the final PR scope.

PR 正文直观、简洁地说明比较范围内的最终变化，适当润色，让读者容易看懂新增、改善和修复了什么。

- 发布 PR 先核对基准版本和目标版本，在开头写明版本范围；功能 PR 按实际分支差异描述，不编造版本号。
- 按功能合并相关改动，省略已被回退或替代的中间方案；用简短段落或条目描述具体问题和最终行为，避免实现流水账。
- 测试细节、构建与审查结果、用户的本地要求、对话过程及后续工作安排保留在提交说明或本地记录中，不写进 PR 正文。
- 已知相关 issue 时附上链接；只有完整解决问题时才使用关闭关键词。
- 范围变化后，按最终结果重写标题和正文。

Release preparation uses the [version-bump skill](../version-bump/SKILL.md); `.github/CHANGELOG.md` keeps its bilingual format while the PR text stays English. Ordinary PRs leave versions and the changelog as they are.

## Validate the proposed head

以下要求供准备和审查时内部核对，不作为 PR 正文模板。

Apply the validation in the [commit skill](../commit/SKILL.md#validate-and-finish) and the affected subsystem skills. Check the full comparison with `git diff --check BASE...HEAD`, check pending edits separately, and confirm the tracked-file allowlist and skill mirror parity.

`.github/workflows/verify.yml` checks PRs targeting `beta` and `main`: whitespace, the tracked-file allowlist, resource preparation, and debug Kotlin compilation. Signed release APKs and device behavior are outside its coverage. Report CI as passed only after observing its result for the intended head.

Pushing or merging into release branches can trigger `build-beta.yml` or `build-stable.yml`; mention this when asking for authorization.

## Create or update the requested PR

When remote creation or editing is authorized:

1. Confirm the repository, base, remote head branch, and head commit. If a push is needed, follow the commit skill's push rule first, then verify that the remote head matches the reviewed local commit.
2. Check for an existing PR with that head and base; edit it only when the request covers updating it.
3. Write the exact English body to a temporary UTF-8 file and run `gh pr create` or `gh pr edit` with `--body-file`, an explicit repository and title, and explicit `--base` and `--head` on creation. Pass Markdown only through the file, with `--fill` left out.
4. Honor the requested draft or ready state, defaulting to draft. Add reviewers, labels, assignees, or comments only on request.
5. Read back the URL, title, body, base, head, and draft status, and report them with validation and pending checks. If a command fails ambiguously, inspect the remote state before retrying.

If authentication, network access, or authorization is missing, deliver the finished title and body with the exact remaining step, and state that nothing was published.
