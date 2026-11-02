# LZCY Personal Fork Versioning

This fork follows the upstream release version and adds a personal revision suffix:

```text
<upstream-version>-lzcy.<fork-revision>
```

Git tags add the leading `v`:

```text
v1.13.2-lzcy.1
v1.13.2-lzcy.2
```

## Rules

1. Start from the exact upstream semantic version.
2. Increment `fork-revision` for each release that stays on the same upstream baseline.
3. When a new upstream version is merged, reset `fork-revision` to `1`.
4. Never move or reuse a published tag.
5. Keep Android `versionCode` monotonically increasing with:

   ```text
   upstream-versionCode * 1000 + fork-revision
   ```

## Examples

| Upstream base | Fork version | Upstream code | Fork code |
| --- | --- | ---: | ---: |
| `v1.13.2` | `v1.13.2-lzcy.1` | `59` | `59001` |
| `v1.13.2` | `v1.13.2-lzcy.2` | `59` | `59002` |
| `v1.13.3` | `v1.13.3-lzcy.1` | `60` | `60001` |

The suffix is intentionally separate from the upstream version, so upstream version bumps remain easy to recognize and merge.
