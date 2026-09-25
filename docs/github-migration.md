# Moving the primary repository from GitLab to GitHub

GitHub (`lene/go-3`) becomes the primary home for code, issues and CI. GitLab
(`gitlab.com/go-3/go-3`) becomes a read-only mirror that accepts pushes only from GitHub and
takes no new issues. Steps marked **manual** need an account owner.

## 1. Preparation (manual)
- Create a GitLab personal access token with `api` scope.
- Create a GitHub fine-grained PAT for `lene/go-3` with *Issues: read and write*.
- Disable Dependabot version updates, and don't open issues or PRs on GitHub until step 2 is
  done. Every new issue or PR takes a number that the migration needs.

## 2. Migrate issues
```shell
export GITLAB_TOKEN=... GITHUB_TOKEN=...
scripts/migrate_gitlab_issues.py              # dry run: check the plan it prints
scripts/migrate_gitlab_issues.py --execute    # resumable; progress in migration-map.json
```
- GitLab issue #N becomes GitHub issue #N for N ≥ 3.
- Deleted GitLab issues get closed placeholder issues, so the numbers stay aligned.
- GitLab #1 and #2 are appended after the highest GitLab number, because GitHub #1 and #2 are
  pull requests.
- The script copies labels, milestones and comments.
- @-mentions are defused so they don't ping unrelated GitHub users.
- The script aborts if a GitHub number doesn't match the one it expects.

Check a few issues on GitHub, then comment on and close the GitLab originals:
```shell
scripts/migrate_gitlab_issues.py --close-gitlab             # dry run
scripts/migrate_gitlab_issues.py --close-gitlab --execute
```

## 3. GitHub Actions (this branch)
- `.github/workflows/ci.yml` replaces the GitLab pipeline. `release.yml` creates the tags and
  releases. `mirror-to-gitlab.yml` pushes to GitLab.
- The Docker image moves to `ghcr.io/lene/go-3/server`. After the first push to master, make
  the package public: *Package settings → Change visibility*.
- `.gitlab-ci.yml` now contains only GitLab's security scanners.

## 4. Mirror key (manual)
```shell
ssh-keygen -t ed25519 -N "" -C "github-to-gitlab mirror" -f gitlab-mirror
```
- GitHub → *Settings → Secrets and variables → Actions*: add the secret `GITLAB_MIRROR_SSH_KEY`
  containing the private key `gitlab-mirror`.
- GitLab → *Settings → Repository → Deploy keys*: add `gitlab-mirror.pub` and tick
  *Grant write permissions*.
- Delete both key files locally afterwards.

## 5. Lock down GitLab (manual)
- *Settings → Repository → Protected branches*: protect `*` with *Allowed to merge: No one*,
  *Allowed to push and merge: the deploy key*, and *Allowed to force push* on.
  Re-protect `master` the same way.
- *Protected tags*: protect `*` with *Allowed to create: the deploy key*. If your GitLab
  version doesn't offer deploy keys there, use *Maintainers*.
- Port the 3 open merge requests to GitHub or close them.
- *Settings → General → Visibility*: disable **Issues** and **Merge requests**.
- *Settings → General*: set the description to "Mirror of https://github.com/lene/go-3 — issues
  and pull requests go there". Don't archive the project, because archiving blocks the mirror
  pushes.
- *Settings → CI/CD → Variables*: delete `GITHUB_SSH_PRIVATE_KEY`, `GITHUB_API_TOKEN` and
  `GITLAB_ACCESS_TOKEN`. Remove the matching deploy key from GitHub.
- GitLab's built-in pull mirroring would make the Actions workflow unnecessary, but it needs a
  paid GitLab tier.

## 6. GitHub settings (manual)
- *Branches*: protect `master` and require the CI checks.
- *Code security*: enable secret scanning, push protection and Dependabot alerts. The
  `dependency-submission` job reports sbt dependencies.
- Re-enable Dependabot version updates.

## Verification
- Push a branch to GitHub. It should appear on GitLab, where only the scan jobs run.
- `git push` to GitLab with your own account is rejected.
- Creating an issue on GitLab isn't possible.
- A version bump merged to master produces a GitHub release with the zip attached, and the
  tag appears on GitLab.
