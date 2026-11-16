# Contributing to MineLights

Thanks for helping improve MineLights! Bug reports, translations, testing, documentation and code changes are all welcome.

## Before you start

- Search the [open issues](https://github.com/megabytesme/MineLights/issues) and pull requests for similar work. For a larger change, opening an issue first can help agree on the approach.
- For a bug, include the Minecraft version, loader and MineLights version, steps to reproduce it, and relevant logs. Mention the device and RGB software when they matter.
- Remove pairing files, credentials, private network addresses and other personal information from anything you share. For a security issue, please ask the maintainer for a private reporting route rather than posting sensitive details publicly.
- Fork the repository, create a branch for your change, and open a pull request against `master`. Keep unrelated changes in separate pull requests where practical.

## Project layout

- `common/` contains shared Java sources and language files. Loader builds copy or adapt shared sources; edit the source of truth rather than a generated copy.
- `fabric/`, `quilt/`, `forge/` and `neoforge/` contain loader-specific code and version settings. Check their `versions/` directories and the build configuration for the versions a change affects.
- `horizon/` contains the Horizon port, its tests and translation generator.
- [MineLights Server](https://github.com/megabytesme/MineLights-Server) is a separate repository. Server changes and mod changes can be linked across pull requests.

Features and APIs differ across Minecraft versions. When an API is unavailable, keep an appropriate fallback and explain the boundary in the pull request. Check the other supported loaders and Horizon when changing shared settings, commands, effects or translations.

## Building and testing

The Java builds use the Gradle wrapper and Minecraft-version-specific toolchains. A full set of targets can require JDK 8, 16, 17, 21 and 25; install the versions needed for the targets you build. The checked-in Gradle properties contain maintainer-specific JDK paths, so override `org.gradle.java.home` and `org.gradle.java.installations.paths` for your machine when needed.

From the repository root, build a target relevant to your change, for example:

```sh
./gradlew -p fabric :1.21.9:build
./gradlew -p quilt :1.21.9:build
./gradlew -p forge :1.20.6:build
./gradlew -p neoforge :1.21.9:build
```

On Windows, use `gradlew.bat` instead of `./gradlew`. To build all configured targets for a loader, replace the individual task with `build`, for example `./gradlew -p fabric build --continue --parallel`. These builds can be large. Run different loader builds separately because they share generated sources.

For Horizon, with a current Rust toolchain:

```sh
cargo test --manifest-path horizon/Cargo.toml --all-targets
```

Run the checks that are practical for your change, including a game launch or device test when it helps. You do not need to own every supported device. In your pull request, say which versions and loaders you built or tested, and mention checks you could not run. Do not start hardware integrations or download and run a server during tests without understanding the effects on your setup.

## Translations

Language files live in `common/src/main/resources/assets/minelights/lang/`. Keep translation keys and format placeholders consistent with `en_us.json`. Preserve the character of the novelty locales. If you cannot confidently translate a string, an English fallback and a note in the pull request are more useful than an uncertain translation.

After changing setting names or server messages used by Horizon, run `python horizon/tools/generate_translations.py` and include the generated `horizon/mixins/minelights/translations.rs` changes. The generator reads tracked language files; stage a new language file before running it.

## Pull request checklist

- Explain what changed and why, and link any related issue.
- List the affected Minecraft versions and loaders, including Horizon or server changes where relevant.
- Describe the builds and tests you ran, their results, and anything you could not test.
- Mention configuration migration, translations, screenshots or hardware details when relevant.
- Keep private credentials and local paths out of logs and screenshots.
- Remove any AI co-authors from your commits.