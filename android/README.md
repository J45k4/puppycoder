# PuppyCoder for Android

PuppyCoder is a chat-first Android client for Codex app-server and OpenCode. Each chat stays attached to one computer and remote thread/session, so follow-up messages retain context.

## Current features

- One chronological Chats list across every configured computer
- Automatic and manual Chats sync from Codex threads and OpenCode workspace sessions
- Server history hydration when an imported chat is opened, with stable de-duplication
- New chat flow with computer selection and optional workspace override
- Multi-turn Codex threads and OpenCode sessions
- Optimistic, durable outbox: the bubble appears immediately and messages send FIFO per chat
- Visible queued, “Sending to …”, sent, streaming, stopped, uncertain, and failed states
- Assistant response streaming, tool activity cards, retry, remove, and stop controls
- Room-backed chats, messages, computers, tunnel profiles, and outbox recovery
- A per-chat model picker populated from the selected Codex or OpenCode server
- Keystore-encrypted connection passwords, SSH passwords, private keys, and passphrases
- Direct, automatic best-route, or specific SSH tunnel routing
- Multiple reusable single-hop SSH tunnels opened on demand, with priorities and route rules
- Connection and SSH forwarding tests with route/latency feedback
- SSH-first computers with automatic Codex/OpenCode discovery and service creation

## Build and verify

Requirements: JDK 17 and Android SDK API 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

With a device or emulator attached:

```bash
./gradlew connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The live Codex test is opt-in and requires the absolute workspace path used by the host app-server. When testing through an emulator, forward its loopback port first:

```bash
adb reverse tcp:4310 tcp:4310
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.liveCodex=true \
  -Pandroid.testInstrumentationRunnerArguments.liveWorkspace=/absolute/path/to/workspace
```

## CI and Android releases

The `Android CI/CD` GitHub Actions workflow runs unit tests, lint, and a debug APK build for Android changes on `master` and pull requests. The debug APK is retained as a workflow artifact for 14 days.

Signed GitHub Releases are created from tags named `android-vMAJOR.MINOR.PATCH`. Before creating the first release, add these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: the release JKS/keystore file encoded as a single-line Base64 value.
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: the signing-key alias.
- `ANDROID_KEY_PASSWORD`: the signing-key password.

Keep the keystore and passwords outside the repository and back them up securely. Every future update must use the same signing key.

For the first release, generate the signing identity and its GitHub secret values in a secure directory outside the repository:

```bash
./ops/create-release-signing.sh /secure/backup/puppycoder-android-signing
```

The script creates a JKS and a mode-`600` `github-actions-secrets.env` recovery file without printing secret values. Add `--github-repo J45k4/puppycoder` to upload all four repository Actions secrets with the authenticated GitHub CLI. The script refuses to overwrite an existing identity or write it inside the Git repository.

Then publish version `0.3.9`:

```bash
git tag android-v0.3.9
git push origin android-v0.3.9
```

The workflow derives Android `versionName` and monotonically ordered `versionCode` values from the tag, verifies the APK signature, and attaches the signed APK plus its SHA-256 checksum to the GitHub Release.

The Android app checks GitHub for published `android-vMAJOR.MINOR.PATCH` releases when it starts. When a newer version exists, Android's `DownloadManager` downloads the APK in the background and the app shows update progress at the top of every screen. After the APK checksum is verified, PuppyCoder prompts before opening Android's package installer. Android 8 and newer may first ask the user to allow PuppyCoder as an installation source; installation always requires explicit system confirmation.

## Agent servers

Keep agent listeners on loopback or another trusted private network. OpenCode is unsecured unless `OPENCODE_SERVER_PASSWORD` is configured.

```bash
opencode serve --hostname 127.0.0.1 --port 4096
codex app-server --listen ws://127.0.0.1:4310
```

The bundled Linux user service provides a durable Codex listener:

```bash
install -D -m 0644 ops/puppycoder-codex-app-server.service \
  ~/.config/systemd/user/puppycoder-codex-app-server.service
systemctl --user daemon-reload
systemctl --user enable --now puppycoder-codex-app-server.service
curl -i http://127.0.0.1:4310/readyz
```

Point other Codex clients at that same process when they need the same local thread store:

```bash
codex --remote ws://127.0.0.1:4310
```

Starting another app-server process, including one managed independently by Codex Desktop, can still contend for the same thread-store writer lock.

An Android emulator can reach host services through `10.0.2.2`. A physical device needs a private-network address, VPN, ADB reverse during development, or PuppyCoder’s SSH routing.

## SSH routing

Open **Computers → SSH tunnels → Define**. A profile contains the SSH gateway, authentication, optional SHA-256 host-key fingerprint, priority, and one or more target rules. Rules accept:

- an exact target, such as `127.0.0.1:4310`;
- a suffix, such as `*.internal.example:4096`;
- `*` as a catch-all.

Automatic routing ranks exact rules ahead of suffix and catch-all rules, port-specific rules ahead of host-only rules, then higher priority. It tries matching tunnels in order and uses direct fallback only when that computer permits it. A computer can instead force Direct or one Specific tunnel.

Use **Test tunnel** to verify SSH authentication and host-key validation. When a matching computer or exact route target exists, the test also opens a direct TCP channel from the gateway to the agent server.

## Chats and models

With one configured computer, **New chat** opens a blank conversation immediately. With several computers, tapping one in the short picker opens the chat immediately; there is no preparation form.

Inside a chat, tap **Model: Server default** in the header to choose a model reported by that computer. The choice is saved per chat and applies to subsequent messages. Selecting **Server default** returns control to the agent server configuration.

For host verification, copy the gateway’s SHA-256 host-key fingerprint:

```bash
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256
```

Leaving the fingerprint blank accepts any host key and is vulnerable to an on-path attacker. Password and pasted OpenSSH/PEM private-key authentication are supported; secrets are encrypted using an Android Keystore key before Room persistence.

## Protocol behavior

PuppyCoder keeps one initialized Codex WebSocket per configured computer and multiplexes model, history, thread, turn, and interrupt RPCs over it. Responses are routed by RPC id; streamed notifications are routed by thread and turn id. A stored `threadId` is resumed once when it attaches to a connection, later messages use `turn/start`, the local message UUID is sent as `clientUserMessageId`, and stop uses `turn/interrupt` on the same socket. OkHttp pings keep idle sockets alive; reconnects reinitialize the transport, resume active thread subscriptions, and never replay `turn/start` automatically.

OpenCode chats persist the session ID, attach a stable `messageID`, send through `prompt_async`, stream `/event` message/tool/session events, and stop with the session abort endpoint.

PuppyCoder currently starts Codex threads with `approvalPolicy: "never"`. Interactive approval cards are intentionally not enabled yet.
