# LZCY Personal Fork Versioning

The phone and TV apps share one repository and shared code. Each app has its own release version, Android version code, release notes, APK and update channel.

## Version format and release rules

Each channel retains the upstream version and the personal three-part revision:

```text
<upstream-version>-lzcy<major>.<feature>.<fix>
```

For example, both channels may start at `1.13.3-lzcy1.5.1`. A TV-only fix can release TV `1.13.3-lzcy1.5.2` while phone stays at `1.13.3-lzcy1.5.1`. Later phone releases advance from the last published phone version. Matching numbers across channels do not imply identical functionality.

1. Change a channel's `versionName` and `versionCode` only when the maintainer explicitly requests a release of that channel. Routine development and temporary verification builds keep the channel's current version.
2. Increment the personal `major` for a major change, `feature` for new functionality, and `fix` for a bug-fix-only release. Make the decision independently for phone and TV.
3. An upstream merge alone does not release either channel. When a channel is first released on a new upstream version, use the exact upstream version and reset that channel's personal suffix to `1.0.0`, subject to the version-code checks below. The other channel advances only when it is released.
4. Keep `versionCode` strictly increasing relative to every previously distributed build of the same application ID. The existing formula may be used independently for each channel if it produces a value greater than that channel's installed and published codes:

   ```text
   upstream-versionCode * 1000000 + major * 10000 + feature * 100 + fix
   ```

   Check that each revision component fits its allocated digits and the resulting value is permitted by Android and any distribution store. If the formula cannot produce a valid higher value, choose and document a compatible higher code before release. The two application IDs do not need matching codes.
5. Preserve each formal channel's application ID and release signing identity for future updates. The phone ID is `gomeng.dev.stashplayer` and the formal TV ID is `gomeng.dev.stashplayer.tv`. The earlier `gomeng.dev.stashplayer.tvpreview` package uses a debug key and is a separate installation.
6. Debug and local test builds use the corresponding channel's version without advancing it. Distinguish test artifacts by filename and build metadata rather than publishing a new version.
7. Do not move or reuse a published tag. Use separate tags and APK names, for example:

   ```text
   phone-v1.13.3-lzcy1.5.2  /  StashPlayer-Mobile-1.13.3-lzcy1.5.2.apk
   tv-v1.13.3-lzcy1.5.2     /  StashPlayer-TV-1.13.3-lzcy1.5.2.apk
   ```

   Existing published tags remain historical records. A release of both channels may use separate tags and releases even when the versions match.
8. Add channel-specific changes to `CHANGELOG.md` and the matching GitHub Release notes. Evaluate shared-code changes for each app and release only the channels intended by the maintainer. Run the checks relevant to each released channel.

## Update channels and migration

The channel manifest is updates/channels.json. Each channel identifies its version, Android version code, APK URL, release URL and notes. Update only the intended channel and verify the corresponding published APK exists before updating the manifest.

The phone app reads the phone entry. The TV app does not yet provide an update prompt. The first formal TV package has a new application ID and release signature. The maintainer has chosen fresh installation for the transition from old builds.


Before publishing, inspect the currently installed versions and signatures, build and verify each intended APK, prepare the channel-specific notes for review when requested, then create the matching tags, releases and manifest entries. Verify each client's update result after publication. Temporary builds do not update the manifest.

## Upstream sync automation

`.github/workflows/sync-upstream.yml` runs every six hours and can also be started manually.

- If upstream `main` contains no new commits, the workflow exits without changes.
- If upstream merges cleanly, the workflow creates and immediately merges an automated pull request.
- If merging would conflict, the fork's `main` is left unchanged and an `automation/upstream-conflict` pull request is kept open for manual review.
