# Support

Calculator Suite is maintained by one person in their spare time. There is no support desk, no paid tier and no ticket queue — but the project is genuinely well-documented and most questions are already answered in the docs.

## Start here

| I want to… | Look at |
|---|---|
| Know what a tool does / see a worked example | [Features & tools](docs/FEATURES_AND_TOOLS.md) |
| Understand input rules, units and error messages | [User guide](docs/USER_GUIDE.md) |
| Learn the calculus expression syntax and its limits | [Calculus guide](docs/CALCULUS_GUIDE.md) |
| Install or update the app | [Releases](https://github.com/JuggyBob/CalculatorSuiteAndroid/releases) |
| Build it myself | [Building](docs/BUILDING.md) |
| Answer "does it work on my phone?" | [FAQ](docs/FAQ.md) |
| Know what happens to my data | [Privacy](PRIVACY.md) |

## Frequently asked, in one line each

- **Does it work offline?** Yes — every tool except Currency works with no network. Currency keeps working from its cached rates.
- **Is it free?** Yes, and there is no advertising, no in-app purchase and no account.
- **Can I use it commercially?** No. It is licensed under the PolyForm Noncommercial License 1.0.0. See [LICENSE](LICENSE).
- **Does it collect anything about me?** No. See [PRIVACY.md](PRIVACY.md).
- **Why does my phone show a "unknown sources" warning?** Because the APK is distributed from GitHub rather than the Play Store. See [Installation](#installation-help).

## Reporting a problem

Please [open an issue](https://github.com/JuggyBob/CalculatorSuiteAndroid/issues/new/choose). Use the **Bug report** template and fill in every field.

For a wrong calculation, the three things that matter most are:

1. **Which tool** (name or id).
2. **Exactly what you typed** — the field values and the units you selected. Copy the text rather than describing it.
3. **What it showed vs what you expected**, and where your expected value comes from.

A worked example is worth ten descriptions:

```
Tool:     Loan
Inputs:   Principal 250000, rate 4.5 %, term 30 years
Showed:   1265.85
Expected: 1265.84  (my bank quotes this)
```

That is reproducible in seconds. "Loan is wrong" is not.

If the app **crashes**, include the Android version, the device model, and what you were doing immediately before it.

## Requesting a feature

Please [open a feature request](https://github.com/JuggyBob/CalculatorSuiteAndroid/issues/new/choose). It is much more likely to be accepted if you describe the *problem* and the numbers you need to work out, rather than just naming a tool. If several people need the same thing, that is a strong argument for it.

## Installation help

The APK is signed with a release key, so Android will show a warning the first time because it did not come from the Play Store.

- **Android 8.0+** is required (API 26).
- Download the `.apk` from the [latest release](https://github.com/JuggyBob/CalculatorSuiteAndroid/releases).
- Open the downloaded file and allow installation from your file manager when prompted.
- Verify the download first:

  ```bash
  sha256sum CalculatorSuite-v3.0.0.apk
  # compare with the .sha256 file published alongside it
  ```

If Play Protect blocks it, that is expected for a sideloaded app. Confirm the checksum matches first, then allow the install.

## Contributing

Fixes, documentation improvements and new tools are all welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). You do not need to be a Kotlin developer to help: correcting a wrong documented example or improving a formula explanation is genuinely valuable.

## Security problems

Do not report security issues in public issues. See [SECURITY.md](SECURITY.md).
