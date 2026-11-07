# Main-only GitFlow and semantic versions

Proposed workflow contract under [#100](https://github.com/skizzay/slickflow/issues/100).
The [BPMN/Mermaid reference](../architecture/workflow-reference.md) illustrates it;
runtime branching, release and hotfix handlers are not implemented by this model.

## Branches and release identities

`main` is the only permanent integration branch. There is no `develop` branch.
Supporting branches are temporary and use exact approved commit bases:

| Branch | Created from | PR target / purpose |
|---|---|---|
| `epic/ISSUE` | Approved, current `main` commit | Coordinate approved child stories and acceptance checks |
| `feature/EPIC-STORY` | Fresh approved epic head | Merge into that epic after verified review/tests and human merge approval |
| `release/X.Y.Z` | Tested and approved epic head, after release-version classification | Final release PR into `main`; only release stabilization changes belong here |
| `hotfix/X.Y.Z` | Approved latest released `main` commit, identified by its immutable tag | Urgent compatible bug-fix PR directly into `main`, without creating an epic |

Normal delivery is main → epic → feature → epic → release → main. The release
branch takes the place of GitFlow's preparation step without a persistent develop
branch. These names are the proposed workflow convention; this documentation
change does not rename existing repository branches or change current PR targets.

Choose the release version from the actual reviewed public-API change:
backward-incompatible changes increment MAJOR, backward-compatible functionality
increments MINOR, and backward-compatible bug fixes increment PATCH. Reset lower
components when incrementing MAJOR/MINOR. Agent suggestions are evidence for human
review, not authority to determine compatibility or issue a release. Initial 0.x
versions require an explicit compatibility policy; absence of a stable API does
not justify silently classifying a breaking fix as a patch.

Reserve the candidate version before creating its versioned branch. Branch/tag
collisions or a competing release require reconciliation and fresh approval; never
reuse a branch for another approved change or move an existing version tag.
Prerelease identifiers such as `1.5.0-rc.1` are optional reviewed candidates, not
production release tags. Immutable stable release tags are `vX.Y.Z`, pointing to
the exact reviewed and human-authorized main commit. Tagging/release publication
requires separate authenticated release approval after verified merge, bound to
the version and commit. This model does not authorize automatic deployment.

## Hotfixes

A verified hotfix ticket starts a separate process. An authenticated human approves
the scope and pinned released base; labels or urgency alone grant no authority.
The default path supports the latest released main version and a compatible patch.
For `v1.4.2`, the normal candidate is `hotfix/1.4.3`, targeting main and eventually
tagged `v1.4.3`. A feature or breaking change takes the normal release path instead.
Older supported lines require a separately approved maintenance policy; they must
not be guessed or merged indiscriminately into main.

Before branch creation, require main to equal the approved released base. Untagged
main changes, stale bases or concurrent release reservations hold for reconciliation
rather than contaminating a patch with unrelated work. Revalidate current scope,
branch/PR base and head, tests, completed review and human authority at publication,
merge and release boundaries. The shared webhook-driven review/remediation loop
and its two-attempt default cap apply to hotfixes as well.

After the patch tag is verified, track propagation PRs into affected active epic
and release branches. They require their own review/tests and human merge approval;
there is no develop branch to merge into. A changed base invalidates stale checks
and merge approval; changed plan/configuration pins require renewed plan approval.
Block further delivery from affected work until its required propagation is verified.
The hotfix process ends with the patch released and follow-ups tracked, not with
an assertion that every propagation PR has already merged.

For example, while `release/1.5.0` is being prepared, a `hotfix/1.4.3` can patch
released `v1.4.2` on main. Incorporate that verified patch into the active release
and affected epics before their next eligible publication/merge, re-run checks,
and obtain fresh approvals. Do not merge the unfinished `1.5.0` release into the
hotfix or tag the patch from an unapproved main head.

This is a project-specific adaptation of
[GitFlow's original branch model](https://nvie.com/posts/a-successful-git-branching-model/)
using [Semantic Versioning 2.0.0](https://semver.org/). GitFlow's develop branch is
intentionally omitted; SemVer specifies version meaning, not branch naming.
