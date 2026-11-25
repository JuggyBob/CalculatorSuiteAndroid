# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private reporting form:

**[Report a vulnerability](https://github.com/JuggyBob/CalculatorSuiteAndroid/security/advisories/new)**

This opens a private advisory visible only to the maintainer. If private advisories are unavailable for your account, contact the maintainer through their [GitHub profile](https://github.com/JuggyBob) and ask for a private channel.

Please do not disclose the issue publicly until it has been addressed.

### What to include

- Affected version (the app version shown on **Settings → About CalcSuite**, or the commit SHA)
- Device model, Android version
- Steps to reproduce, ideally with the exact input that triggers it
- What you observed and what you expected
- Impact — what an attacker gains

## Scope

Calculator Suite has a very small attack surface: it has no accounts, no server of its own, no analytics and no code loading. Please report anything such as:

- A calculation that produces a result outside its documented domain in a way that could affect a safety- or money-related decision
- A crash or resource exhaustion triggered by crafted input
- Data from the device being exposed to another app (leaking stored history or settings through a content provider, exported component, log, or backup)
- An insecure network request on any of the three paths — currency rates, the optional AI provider or the GitHub update check — in particular anything that transmits more than the documented data
- Insecure storage of local data
- An update link that leads off GitHub's own hosts, a redirect that escapes that allowlist, or an APK accepted without the published digest, package, version and signing-certificate checks
- An update download that ignores the 2 MiB metadata / 512 MiB APK caps or the three-hour throttle, or that installs without Android's confirmation
- History, settings or the cached release being copied into a system backup when they should be excluded
- The AI credential leaking into a log, saved state or an error message (including when a provider repeats the key in its error)

## Out of scope

- The [PolyForm Noncommercial License 1.0.0](LICENSE) — licence compliance questions belong in a normal issue, not a security report
- Missing features or incorrect maths that has no security impact — please report those as bugs, they are taken seriously
- Vulnerabilities in third-party AndroidX libraries; report those to Google, but do tell us if a Calculator Suite update would mitigate it
- Attacks requiring physical access to an unlocked device
- Reports from automated scanners without a demonstrated impact

## Supported versions

| Version | Supported |
|---|---|
| 3.1.x | ✔ |
| 3.0.x | ✘ (upgrade to 3.1.0) |
| 2.0.0 | ✘ (upgrade to 3.1.0) |
| 1.0.0 | ✘ (upgrade to 3.1.0) |

As the project is new, only the latest release receives fixes. When a new version is published, the table above is updated.

## What happens next

| Stage | Target |
|---|---|
| Acknowledgement | Within 7 days |
| Initial assessment (severity, scope, whether confirmed) | Within 14 days |
| Fix released or mitigation advised | As soon as practical, coordinated with you |
| Public disclosure | Only after a fix is available, and agreed with you |

We will keep you updated throughout and credit you in the advisory unless you would prefer to remain anonymous.

## Responsible disclosure

- Give us a reasonable opportunity to fix the issue before public disclosure.
- Do not access data belonging to other users, degrade the service, or use the issue for anything beyond proof of concept.
- Do not send automated bulk traffic.

We do not take legal action against good-faith research that follows this policy, and we will not pursue action for reports made in good faith and in accordance with this policy.

## Security properties of this app

For context when assessing risk, and because it is genuinely useful to users:

- **Local data stays local.** History, favourites, settings and cached rates are stored in the app's private DataStore and never leave the device. There is no account and no sync to a server of our own; the DataStore files are excluded from Android cloud backup and device-to-device transfer.
- **No analytics or advertising.** The app contains no analytics, crash-reporting, advertising or tracking SDK.
- **Three network paths, and no more.** The Currency converter issues an HTTPS `GET` to `https://open.er-api.com/v6/latest/<base>` with only the base currency code; the optional AI problem solver sends your picture, question and key to the provider you chose; and the update check makes an unauthenticated HTTPS `GET` to GitHub's public releases API. All three use HTTPS, and no other screen makes any network request.
- **No code loading.** Nothing is downloaded and evaluated at runtime; no dynamic code, no reflection-based plugin system.
- **Release builds are signed** so users can verify the install came from this project. See [docs/BUILDING.md](docs/BUILDING.md).
- **Manifest is minimal.** Three permissions are declared: `INTERNET` (currency, AI and update check), `ACCESS_NETWORK_STATE` (offline detection for cached rates and the cached release) and `REQUEST_INSTALL_PACKAGES` (offering a verified update to Android's installer). A portrait-only activity and a single exported launcher activity are declared, plus a non-exported `FileProvider` used only to hand the downloaded APK to Android; there are no custom permissions and no exported components other than the launcher entry.

### Update hardening in 3.1

The 3.1 update check was hardened. Release metadata is read only from an allowlist of GitHub HTTPS hosts (the API, `github.com` and GitHub content hosts), and the final address after any redirect is checked again before the body is read. The reply is capped at 2 MiB and a downloaded APK at 512 MiB, so an oversized response cannot exhaust memory or storage. Before anything is offered, the APK must match the digest the release publishes (SHA-256 or SHA-512), its package name and version code, and the project's signing certificate; Android, not the app, confirms the install. Log identifiers are sanitised and provider error bodies redacted, so a key repeated in an error cannot reach a log.

See [PRIVACY.md](PRIVACY.md) for the full data-handling description.
