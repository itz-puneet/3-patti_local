# 3 Patti Tracker

An Android app for keeping track of chips while you play 3 Patti (Teen Patti) with **real cards**.
One Android phone hosts the table and everyone else joins from their own phone over the local WiFi
or the host's hotspot. Friends with an iPhone join from Safari by scanning a QR code. No internet or
account needed.

The app does the chip work: it collects the boot, knows what blind and seen players have to pay,
tracks the pot, handles show and side show, pays out the winner and keeps a ledger of who is up or
down, so at the end it tells you exactly who pays whom.

## Install

1. On each phone, open the [latest release](../../releases/latest) and download `3patti-tracker.apk`.
2. Open the file and allow installing from this source when Android asks.
3. Install the same version on every phone. The host turns away phones with a different version.

**iPhone (or any phone without the app):** nothing to install. The host opens
**Invite players** and the iPhone scans the QR code with the camera, which opens the table in Safari.
The host must be an Android phone.

A new APK is built automatically for every change pushed to `main`. It installs over the previous
one and keeps your data.

## How to play

1. **Connect the phones.** Everyone joins the same WiFi, or the host turns on their phone's hotspot and
   the others connect to it.
2. **Host a table.** The host enters their name, taps **Host a table**, sets the starting chips
   (250 by default), boot, and optional limits, then taps **Open table**.
3. **Join.** Everyone else enters their name and taps **Join a table**. The table shows up by itself.
   If it doesn't, type the address the host sees under **⋮ → Invite players**. iPhones scan the QR
   code on that same screen, enter their name in Safari and tap **Join the table**.
4. **Play.** Deal real cards and the host taps **Start round**. Each player then acts on their own
   phone when it's their turn: **See cards**, **Blind/Chaal**, **Raise**, **Pack**, **Show** or
   **Side show**.
5. **Results.** For a show or side show, compare cards on the table and the host taps who won. The pot
   goes to the winner automatically.
6. **Settle up.** The **Ledger** tab shows everyone's chips and profit or loss, and a **Settle up** list
   of who pays whom.

## Betting rules

| Rule | What the app does |
| --- | --- |
| Boot | Collected from every player at the start of each round. The stake starts at the boot. |
| Blind | A player who hasn't looked at their cards bets the stake. |
| Chaal | A player who has seen their cards bets twice the stake. |
| Raise | Bets double and doubles the stake for everyone after. |
| Chaal limit | Highest bet a seen player can make. No raises above it. `0` means no limit. |
| Pot limit | When the pot reaches it, everyone still playing must show. `0` means no limit. |
| Blind turns | How many blind bets a player can make before they must see. `0` means no limit. |
| Pack | Fold. When only one player is left, they win the pot. |
| Show | Allowed when two players are left. Costs one bet. The host enters the winner. |
| Side show | A seen player asks the previous seen player to compare privately. If accepted, the host enters the winner and the other packs. |

Dealer and first turn move one seat each round. Players who can't pay the boot, or who sit out, are skipped.

## Host tools

- **Undo** the last action if someone tapped the wrong button.
- **Play for someone**: players without a phone can be added from the menu, and the host plays their moves.
  The host also gets the controls automatically when a player's phone is offline, and can take over any
  turn with **Play for …**.
- **Tap a player** to add or take chips (buy more / cash out), mark cards seen, pack their hand, sit
  them out, rename, change seat order, or remove them.
- **Call show for everyone**, or **Cancel round (misdeal)** to give every bet of the round back.
- **Table settings** can be changed between rounds.
- The table is saved on the host's phone after every change. If the app is closed or the phone restarts,
  **Resume as host** brings everything back and players reconnect to their own seats.

## Troubleshooting

- **Table doesn't show up in Join**: make sure all phones are on the same WiFi or on the host's hotspot.
  Some routers (and guest networks) block phones from seeing each other. Using the host's hotspot avoids
  that. You can also type the address from **⋮ → Invite players**.
- **iPhone shows "Offline" on the host**: Safari pauses pages when the iPhone locks or switches apps. While
  it's offline the host gets that player's buttons, so the game never waits. Opening Safari again
  reconnects to the same seat.
- **Players get disconnected when the host locks the screen**: the app keeps running in the background
  with a notification. If your phone's battery saver still stops it, allow the app to run in the
  background in the phone's battery settings.
- **"This app version doesn't match"**: install the latest APK on every phone.

## Building from source

Requirements: JDK 17 and the Android SDK (Android Studio installs both).

```bash
./gradlew :core:test              # game rules and networking tests
./gradlew :app:assembleRelease    # APK in app/build/outputs/apk/release/
```

Or open the folder in Android Studio and press Run.

The APK is signed with the key in `app/signing/` so that builds from GitHub Actions or any computer
can update each other on the phones. It is only meant for sharing this app with friends. Anyone with
the repository can sign an update, so don't reuse this key for anything else.

## Project layout

```
core/   Plain Kotlin: game rules, table state, ledger, and the LAN protocol (host server,
        client, discovery, and the web server for browsers). Covered by unit tests,
        including real socket and HTTP tests. The browser page is core/src/main/resources/web/.
app/    Android app: Jetpack Compose screens, the host's background service, and saving the table.
```

The host phone is the only source of truth. Players' phones send moves to the host over TCP
(port 47474) and receive the full table after every change. Tables are found with a UDP broadcast
on port 47475. Browsers load a page from the host on port 8080, get table updates as Server-Sent
Events and send moves as small POST requests.
