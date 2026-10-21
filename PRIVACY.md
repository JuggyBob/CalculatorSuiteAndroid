# Privacy Policy

**Last reviewed: 21 October 2026 — Applies to Calculator Suite 3.0.0**

The short version: Calculator Suite does not collect, transmit, sell or share personal data. It has no accounts, no analytics, no advertising and no tracking. Everything you do in the app stays on your device, with one narrow, documented exception for currency exchange rates.

This document describes what the app actually does, based on a review of the source code in this repository.

---

## 1. Who is responsible

Calculator Suite is an independent, non-commercial project published by
[JuggyBob](https://github.com/JuggyBob). It is not operated by a company, and
there is no data controller receiving your information.

## 2. Permissions the app requests

The manifest declares exactly two permissions:

| Permission | Why it is needed | Where it is used |
|---|---|---|
| `android.permission.INTERNET` | Fetching currency exchange rates | Currency converter only |
| `android.permission.ACCESS_NETWORK_STATE` | Telling "you're offline" apart from "the server is down", so the app can show cached rates instead of an error | Currency converter only |

The merged release manifest also contains `com.calcsuite.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which AndroidX adds automatically for its own internal broadcast receivers. It is a self-defined, signature-level permission and is never granted to other apps.

The app requests **no** runtime permissions — there is no dialog asking for location, camera, microphone, contacts, storage or notifications, and the app cannot be granted them.

## 3. What is stored on your device

All persistence uses Android **DataStore Preferences**, in the app's private internal storage. This area is sandboxed: other apps cannot read it, and it is removed when you uninstall Calculator Suite.

| Data | Where | Why | Retention |
|---|---|---|---|
| Calculation history | `history` key — a JSON array, max **500** entries | To show your recent calculations | Until you delete it or uninstall |
| Favourites | `favorites` key — a set of tool ids | To show starred tools first | Until you change it or uninstall |
| Recently used tools | `recents` key — max 8 tool ids | To show recents on the main screen | Until you change it or uninstall |
| Settings | `theme_mode`, `angle_mode`, `precision`, `grouping`, `thousands_separator`, `haptics`, `dynamic_color` | To remember your preferences | Until you change it or uninstall |
| Onboarding state | `onboarding_complete` | So the welcome pages appear once only | Until you change it or uninstall |
| Cached exchange rates | `currency_rates` key — rates plus the fetch timestamp | So the currency converter works offline | Until refreshed or you clear app data |
| AI provider and model | `ai_provider` and `ai_model` keys | So the app knows which service and which model you chose | Until you change it or uninstall |
| AI API key | `ai_credential` key — **encrypted** with a key held in the Android keystore | So the app can authenticate to the provider you chose | Until you remove it or uninstall |

A history entry records the **tool**, a **text summary of the input**, the **result** and a **timestamp**. For example, a Loan calculation stores something like
`principal 250000 · rate 4.5 % · term 30` → `1265.85`. It does not store anything else about you.

You can erase all of it at any time with **Settings → History → Clear all history**, or by clearing the app's storage in Android settings. Uninstalling removes everything.

### Android backups

Android's system backup may include app data if you have device backup enabled. That backup is handled by Android and your device manufacturer, under Google's terms — not by this project. The app does not opt out of it.

## 4. Network activity

**Every tool except Currency converter and the AI problem solver makes no network requests at all.** There is no telemetry, no crash reporting, no update check, no "are you an Android user?" ping and no font/icon download.

There are exactly two things that talk to the internet, and both are opt-in and visible to you.

### Currency converter

When you use the **Currency converter**, the app makes a single HTTPS `GET` request to:

```
https://open.er-api.com/v6/latest/<base currency code>
```

[open.er-api.com](https://www.exchangerate-api.com) is a free, key-less public exchange-rate service.

**What is sent:**

- The base currency code you selected (for example `USD`), as part of the URL path.

**What is NOT sent:** no device identifier, no advertising ID, no IP-derived profile, no analytics parameters, no account, no name, no email, and no data about any other calculation you have performed. The request carries only two headers: `Accept: application/json` and a fixed `User-Agent: CalcSuite/1.0 (Android)`.

**What is received:** a JSON document of reference exchange rates and the provider's own last-update timestamp. The rates are stored locally (see the table above) so the tool keeps working without a connection. If the cached rates are more than 24 hours old, the app displays a staleness warning.

Requests time out after 15 seconds and failures are reported in-app; nothing is queued for later transmission.

### AI problem solver — only if you configure it

The **AI problem solver** is the one feature that can send something you supply to a third party. It only works if you turn it on yourself, and it is off until you do.

**Turning it on.** Open the brain button on the main screen, then the settings icon, and choose a provider, type the model you want, and paste your API key. The key is kept by the phone's own **Android keystore**, encrypted with AES/GCM, and is never written in plain text, logged, or included in an error message. You can replace or remove it at any time.

**What is sent, and to whom.** When you press **Read the problem**, the app sends one request to the provider **you selected**, and to nobody else. The request contains:

- the **picture** you took or picked, as an image;
- a short **question** asking for the mathematical problem to be read;
- a **list of the mathematical tools** the model may choose from, with the arguments each one takes;
- your **API key**, in the provider's own authentication header.

**What is NOT sent:** no device identifier, no advertising ID, no account, no name, no email, no analytics parameter, and no history, favourites or any other calculation you have ever done. The picture is not stored in the app's history and is not kept after the request.

**The ten providers, and where the picture goes.** The request goes to the service you chose, and that service's own privacy policy then applies to it:

| Provider | Where the request goes |
|---|---|
| OpenAI | `api.openai.com` |
| Anthropic | `api.anthropic.com` |
| Google Gemini | `generativelanguage.googleapis.com` |
| OpenRouter | `openrouter.ai` |
| DeepSeek | `api.deepseek.com` |
| Groq | `api.groq.com` |
| Mistral AI | `api.mistral.ai` |
| xAI | `api.x.ai` |
| Together AI | `api.together.xyz` |
| Fireworks AI | `api.fireworks.ai` |

**Cost.** The request is a normal API call, so it is billed by that provider according to its own pricing, using your key. The app never makes a request you did not ask for, never retries on its own, and never moves you to a different model without your say-so. The **Test the connection** button sends one very short message, which is enough to tell you whether the key and the model work.

**If you never configure the AI feature**, no picture, and nothing else, is ever sent anywhere.

### No other network use

There is no WebView, no embedded browser, no deep link that loads remote content, no dynamic code loading and no runtime evaluation of downloaded code. The model is asked to pick a tool from a fixed list and to fill in that tool's arguments; it is never allowed to name a function, an expression or anything else that would be executed directly.

## 5. Analytics, advertising and tracking

None. Calculator Suite contains:

- no analytics SDK (Firebase Analytics, Amplitude, Mixpanel, Sentry, Crashlytics, etc.)
- no advertising SDK (AdMob, Unity Ads, etc.)
- no crash reporting or diagnostic upload
- no A/B testing or remote configuration
- no attribution or install tracking

If you see network traffic from this app other than the currency request and the AI request you asked for, it is not coming from this codebase — please report it via the [private security advisory channel](SECURITY.md).

## 6. Third-party services

| Service | Purpose | Data shared |
|---|---|---|
| [open.er-api.com](https://www.exchangerate-api.com) | Reference currency exchange rates | Base currency code only |
| The AI provider **you chose** (see the table above) | Reading the mathematical problem in a picture you supply | The picture, the question, the list of tools, and your API key |

The second row applies only if you turn the AI feature on yourself. No other third party receives any data. No data is sold, rented or shared for advertising. There is no data broker, no analytics vendor and no CDN.

## 7. Children

Calculator Suite is safe for all ages and collects nothing from anyone, including children. Because it collects no data, it is not directed at children in a way that would require a dedicated children's policy. The AI feature is not switched on by default, and if you do turn it on, the picture and the question are sent only to the provider you have chosen.

## 8. Your rights

Because Calculator Suite does not collect personal data, there is generally nothing for us to disclose, correct or delete — the data lives only on your device, and you control it directly.

- To delete your history: **Settings → Clear all history**.
- To delete everything: clear the app's storage in Android settings, or uninstall.
- To ask what is stored: it is exactly the table in section 3.

You also have whatever rights your jurisdiction grants over data that never leaves your device. In practice, the operative controls are the ones on your device, and you have them all.

## 9. Security

- Release builds are signed, so you can verify that an installed APK is genuinely this project.
- All local data is in sandboxed private storage.
- The single network call uses HTTPS.
- The app is portrait-only and declares one exported component (the launcher activity).

To report a vulnerability, please use the private channel in [SECURITY.md](SECURITY.md) rather than a public issue.

## 10. Changes to this policy

If a future version changes what the app stores or sends, this policy will be updated in the same commit that changes the code, and the "Last reviewed" date at the top will change. Material changes will also be noted in [CHANGELOG.md](CHANGELOG.md).

## 11. Contact

Questions about this policy or privacy in general can go to the maintainer via
[GitHub](https://github.com/JuggyBob/CalculatorSuiteAndroid/issues). For anything
that might be a security or privacy **breach**, please use the private channel in
[SECURITY.md](SECURITY.md) instead of a public issue.

---

## Summary table

| | |
|---|---|
| Accounts required | No |
| Personal data collected | No |
| Data sold or shared | No |
| Analytics / ads / tracking | None |
| Local storage | History (≤500), favourites, recents, settings, cached rates |
| Network requests | One HTTPS request to `open.er-api.com` for currency rates, base currency code only |
| Data leaves the device | Currency base code only, to a rate provider |
| Deletion | Settings → Clear all history, or clear app data / uninstall |
