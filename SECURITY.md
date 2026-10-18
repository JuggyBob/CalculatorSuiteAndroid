# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private reporting form:

**[Report a vulnerability](https://github.com/JuggyBob/CalculatorSuiteAndroid/security/advisories/new)**

This opens a private advisory visible only to the maintainer. If private advisories are unavailable for your account, contact the maintainer through their [GitHub profile](https://github.com/JuggyBob) and ask for a private channel.

Please do not disclose the issue publicly until it has been addressed.

### What to include

- Affected version (the app version from **Settings → Calc Suite 1.0.0**, or the commit SHA)
- Device model, Android version
- Steps to reproduce, ideally with the exact input that triggers it
- What you observed and what you expected
- Impact — what an attacker gains

## Scope

Calculator Suite has a very small attack surface: it has no accounts, no server of its own, no analytics and no code loading. Please report anything such as:

- A calculation that produces a result outside its documented domain in a way that could affect a safety- or money-related decision
- A crash or resource exhaustion triggered by crafted input
- Data from the device being exposed to another app (leaking stored history or settings through a content provider, exported component, log, or backup)
- An insecure network request, in particular anything that transmits more than the currency code to the exchange-rate service
- Insecure storage of local data

## Out of scope

- The [PolyForm Noncommercial License 1.0.0](LICENSE) — licence compliance questions belong in a normal issue, not a security report
- Missing features or incorrect maths that has no security impact — please report those as bugs, they are taken seriously
- Vulnerabilities in third-party AndroidX libraries; report those to Google, but do tell us if a Calculator Suite update would mitigate it
- Attacks requiring physical access to an unlocked device
- Reports from automated scanners without a demonstrated impact

## Supported versions

| Version | Supported |
|---|---|
| 1.0.0 | ✅ |

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

- **Local data stays local.** History, favourites and settings are stored in the app's private DataStore and never leave the device. There is no account, no sync and no backup to a server.
- **No analytics or advertising.** The app contains no analytics, crash-reporting, advertising or tracking SDK.
- **One network call, and only one.** The Currency converter issues a single HTTPS `GET` to `https://open.er-api.com/v6/latest/<base>`. The only data sent is the base currency code. No other screen makes any network request.
- **No code loading.** Nothing is downloaded and evaluated at runtime; no dynamic code, no reflection-based plugin system.
- **Release builds are signed** so users can verify the install came from this project. See [docs/BUILDING.md](docs/BUILDING.md).
- **Manifest is minimal.** Two permissions are declared: `INTERNET` (for the currency rates) and `ACCESS_NETWORK_STATE`. A portrait-only activity and a single exported launcher activity are declared; there are no custom permissions and no exported components other than the launcher entry.

See [PRIVACY.md](PRIVACY.md) for the full data-handling description.
