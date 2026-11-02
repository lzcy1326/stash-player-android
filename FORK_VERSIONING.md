# LZCY Personal Fork Versioning

This fork follows the upstream release version and adds a personal three-part revision suffix:

```text
<upstream-version>-lzcy<major>.<feature>.<fix>
```

Git tags add the leading `v`:

```text
v1.13.2-lzcy1.0.0
v1.13.2-lzcy1.0.1
v1.13.2-lzcy1.1.0
```

## Rules

1. Start from the exact upstream semantic version.
2. Increment `major` for personal releases that introduce a major change.
3. Increment `feature` for new fork-only functionality.
4. Increment `fix` for bug-fix-only releases.
5. When a new upstream version is merged, reset the personal suffix to `1.0.0`.
6. Keep debug and release builds on the same displayed version; only the application ID adds `.debug`.
7. Never move or reuse a published tag.
8. Keep Android `versionCode` monotonically increasing with:

   ```text
   upstream-versionCode * 1000000 + major * 10000 + feature * 100 + fix
   ```

## Examples

| Upstream base | Fork version | Change |
| --- | --- | --- |
| `v1.13.2` | `v1.13.2-lzcy1.0.0` | Initial fork baseline |
| `v1.13.2` | `v1.13.2-lzcy1.0.1` | Bug-fix-only release |
| `v1.13.2` | `v1.13.2-lzcy1.1.0` | New fork-only feature |
| `v1.13.2` | `v1.13.2-lzcy2.0.0` | Major personal release |
| `v1.13.3` | `v1.13.3-lzcy1.0.0` | New upstream baseline |

The suffix is intentionally separate from the upstream version, so upstream version bumps remain easy to recognize and merge.

## Upstream sync automation

`.github/workflows/sync-upstream.yml` runs every six hours and can also be started manually.

- If upstream `main` contains no new commits, the workflow exits without changes.
- If upstream merges cleanly, the workflow creates and immediately merges an automated pull request.
- If merging would conflict, the fork's `main` is left unchanged and an `automation/upstream-conflict` pull request is kept open for manual review.
