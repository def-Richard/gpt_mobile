# v0.9.1-1 image sending validation

Validation date: 2026-10-08.

## Source and upstream synchronization

- Upstream: `Taewan-P/gpt_mobile`, `b79433c` (v0.9.1 line).
- Merge: `0b4863e`, pushed to `def-Richard/gpt_mobile` main and verified against the remote.
- Version: `0.9.1-1`, Android version code `28` (previously `27`).
- The release includes the image fix described below. Its APK is the same artifact used for the successful device verification; publishing only adds release documentation to the validated application sources.

## Image fix

The OpenAI Responses path previously uploaded each new image through the Files API. A compatible gateway returning HTML from that endpoint caused JSON decoding to fail before the Responses request was sent.

New images now use the existing Base64 data-URL encoder in the Responses request. Valid existing OpenAI file references are reused; unavailable image references fall back to inline encoding. Other providers' references are preserved. The existing 12 MiB inline attachment budget now also applies to OpenAI, including compaction requests; reused file references do not count toward that budget.

## Automated verification

- A local HTTP-server regression test reproduced the original failure before the fix: the Files endpoint returns HTML, producing `JsonDecodingException`.
- With the fix, the same test sends only `/v1/responses`, with text and an `input_image` Base64 data URL and no file ID.
- Additional coverage checks unavailable references, preservation of other providers' references, reuse of valid references, and rejection of oversized inline input.
- `:app:testDebugUnitTest`: 738 tests across 88 suites, 0 failures, 0 errors, 0 skipped.
- `:app:lintDebug`: 0 errors, 125 warnings.
- `:app:assembleRelease`: successful.
- Changed Kotlin files passed ktlint; `git diff --check` passed.

## APK and device verification

- Artifact: `build/releases/GPTMobile-0.9.1-1.apk` (approximately 59.1 MiB).
- SHA-256: `c98ef9375a2b54fb99c0c9014c1941a43c3a84ab125b622f096aacf3327834b2`.
- Signature matches the previously installed APK; signature, 16 KiB ZIP alignment, and ARM64 native library load alignment checks passed.
- Non-debuggable Release build, minimum Android API 31, includes `arm64-v8a`.
- Upgraded Samsung Galaxy S23 Ultra (SM-S9180) with `adb install -r`; installed version verified as `0.9.1-1` / `28`.
- Existing profile and chat remained available after upgrade.
- Retried the original failed image in the existing conversation using the configured Responses gateway and model. The response described the image successfully, completing in approximately 8 seconds with 844 reported tokens.

## Publication audit

- Staged source, extracted APK files, and APK strings were scanned for credentials; local path and device-identifier checks passed.
- One generic-token scanner finding is the Kotlin serialization 1.11.0 dependency's `verification.properties`. Its contents exactly match the public Maven Central artifact; it is dependency verification metadata, not an application credential.
- Only the APK and `SHA256SUMS.txt` are attached to the release. GitHub source archives contain the tagged repository files.

Raw build logs and device UI snapshots are retained locally under the ignored `build/` directory and are excluded from publication. No account credentials were extracted.
