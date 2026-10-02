# 3 Patti Chips Handler

An Android app for keeping track of chips while you play **3 Patti (Teen Patti) or poker** with a deck of
cards. It only counts chips: there is no money anywhere in the app. One Android phone hosts the table and everyone else joins from their own phone over the local WiFi
or the host's hotspot. Friends with an iPhone join from Safari by scanning a QR code. No internet or
account needed.

The app does the chip work. In 3 Patti it collects the boot, knows what blind and seen players have
to pay, and handles show and side show. In poker it posts the blinds, runs the four betting rounds,
enforces minimum raises and pot limit, and splits side pots when players are all-in. Either way it gives
the pot to the winner and keeps a ledger of who is up or down in chips.

## Install

1. On each phone, open the [latest release](../../releases/latest) and download `3patti-chips-handler.apk`.
2. Open the file and allow installing from this source when Android asks.
3. Install the same version on every phone. The host turns away phones with a different version.

Works on Android 8 through Android 16.

**iPhone (or any phone without the app):** nothing to install. The host opens
**Invite players** and the iPhone scans the QR code with the camera, which opens the table in Safari.
The host must be an Android phone. If the host already added that player without a phone, they join
with the same name and take over that seat, chips and all. Browser players show "In browser".

A new APK is built automatically for every change pushed to `main`. It installs over the previous
one and keeps your data.

A Google Play version is being prepared; see [store/PLAY_STORE.md](store/PLAY_STORE.md). It is a
separate app, so it can sit next to the APK, and both play at the same table.

## How to play

1. **Connect the phones.** Everyone joins the same WiFi, or the host turns on their phone's hotspot and
   the others connect to it.
2. **Host a table.** The host enters their name, taps **Host a table**, picks **3 Patti** or **Poker**,
   sets the starting chips (250 by default) and the boot or blinds (plus an optional ante), then taps
   **Open table**. The game
   is fixed for that table; open a new table to play the other one.
3. **Join.** Everyone else enters their name and taps **Join a table**. The table shows up by itself.
   If it doesn't, type the address the host sees under **⋮ → Invite players**. iPhones scan the QR
   code on that same screen, enter their name in Safari and tap **Join the table**.
4. **Play.** Deal the cards and the host taps **Start round** (or **Start hand** in poker). Each player
   then acts on their own phone when it's their turn. In 3 Patti: **See cards**, **Blind/Chaal**,
   **Raise**, **Pack**, **Show** or **Side show**, and **All in** when your chips don't cover the bet.
   In poker: **Fold**, **Check**, **Call**, **Bet** or **Raise** (with a slider and Min / ½ pot / Pot /
   All-in shortcuts).
   After **See cards** you can enter your 3 cards (see [Your cards](#your-cards-3-patti) below).
   The **Table** tab draws everyone round a card table in turn order, with you at the bottom and play
   going clockwise. It shows the pot in the middle, each player's chips and status, the dealer button,
   and whose turn it is. Between 3 Patti rounds it says who deals next and who goes first. Tap **List**
   for a plain list of players instead. So the drawing matches where everyone really sits, the host can
   tap a player and use **Move seat up** or **Move seat down**.
   Your phone vibrates when your turn starts; switch it off in **⋮ → Vibrate on my turn**. Android phones
   playing in a browser vibrate too; iPhones don't let web pages vibrate.
5. **Results.** For a show, side show or poker showdown, compare cards on the table and the host taps
   who won. With side pots the host picks the winner of each pot, main pot first. The chips go to the
   winners automatically. In 3 Patti, when the players in a show or side show have entered their cards,
   the app says who won and the host confirms with one tap.
   Not sure who won a 3 Patti show or side show? Tap **Not sure? Decide from the cards**, enter everyone's
   3 cards and the app names each hand and the winner; the host can then give the pot in one tap.
   **⋮ → Which hand wins?** checks any hands at any time.
6. **Even out.** The **Ledger** tab shows everyone's chips and how far up or down they are, and an
   **Even out** list of who hands chips to whom so everyone ends even.

## Your cards (3 Patti)

After **See cards** the app opens the card picker (switch that off in **⋮ → Enter my cards after See
cards**, and enter them later from the panel). All 52 cards are on one screen, so each card is one tap;
tap a card again to take it back. The same picker is used by **Decide winner**. Entering your cards is
optional and helps settle arguments:

- Only you see them. Your phone shows them next to your buttons; the host's screen doesn't show them.
- In a **side show** the two players see each other's cards and the app's verdict. Everyone else only
  sees who won.
- At a **show** everyone's entered cards are on the table, and the app names each hand.
- Once everyone in the side show or show has entered their cards, the app works out the winner and the
  host confirms with one tap, or picks the winner by hand.
- Cards that have been shown are locked and can't be changed. Undo never changes them either.

The table's **variant** decides how hands rank. Set it in **Table settings → Which hand wins**:
**Classic**, **Muflis** (rankings reversed, the lowest hand wins), **AK47** (aces, kings, 4s and 7s are
jokers) or **Joker** (pick the joker rank). For dealer's choice, change it between rounds. A joker stands
for whichever card makes the best hand, or the lowest in Muflis, and the app shows what it used, such as
"Trail of 9s (K♠ as 9♥)".

## 3 Patti betting rules

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
| All in | Only when your chips don't cover the blind or chaal: put them all in and stay in. You can win the pot as it was then; what others bet afterwards goes to a side pot only they can win. When nobody else can bet, everyone still in shows. |
| Show | Allowed when two players are left. Costs one bet. The host enters the winner. |
| Side show | A seen player asks the previous seen player to compare privately. If accepted, the host enters the winner and the other packs. |
| Side pots | After an all-in the show is decided pot by pot, main pot first; each pot only goes to the players in it. |

The winner of each round deals the next one, so the player sitting after the winner goes first. If the winner
sits out, the player after their seat still goes first. With a split pot, the winner who comes first after
that round's dealer deals. Players who can't pay the boot, or who sit out, are skipped.

### Hand rankings (Decide winner)

From highest to lowest:

| Hand | Example |
| --- | --- |
| Trail (three of a kind) | A A A is the highest, 2 2 2 the lowest |
| Pure sequence (same suit) | A-K-Q ♠ |
| Sequence | Q-J-10 in mixed suits |
| Color (same suit) | K-9-4 ♥ |
| Pair | 7 7 K |
| High card | A-J-5 |

A-K-Q is the highest sequence and A-2-3 the second highest; after that sequences go by their top card,
down to 4-3-2. Equal hands of the same type are compared card by card, highest first; a pair is
compared by the pair, then the third card. When hands are exactly equal, a show splits the pot and in a
side show the player who asked for it packs.

## Poker betting rules

Works for Texas Hold'em, Omaha and other flop games; the host picks **No limit** or **Pot limit** per table.

| Rule | What the app does |
| --- | --- |
| Blinds | The two seats after the dealer post the small and big blind. Heads-up the dealer posts the small blind. The dealer button moves one seat every hand. |
| Antes | Optional. **Everyone** puts in the ante each hand, or the **big blind** pays it for the whole table. Antes go in before the blinds, count towards the pot and side pots, but not towards the bet to call. |
| Betting rounds | Pre-flop, flop, turn and river. Pre-flop the player after the big blind acts first and the big blind gets the option; after that the first player after the dealer starts. |
| Check / Call / Bet / Raise | A raise must be at least as big as the last bet or raise. |
| No limit | Bet any amount up to all your chips. |
| Pot limit | The most you can bet or raise is the size of the pot (after calling). |
| All-in | An all-in for less than a full raise doesn't let players who already acted raise again. |
| Side pots | Made automatically when players are all-in for different amounts. Chips nobody called go straight back. |
| Showdown | Everyone still in shows; the host taps the winner of each pot. Ties can be split. |

## Host tools

- **Undo** the last action if someone tapped the wrong button. Undo has limits so it can't be used to
  quietly rewrite the game:
  - Nothing is erased. Undone moves and results stay in **History**, crossed out, and History shows how
    many times the host used undo.
  - Every player sees a message saying what the host undid.
  - Only the current round (or hand) can be undone. Once the next one starts, earlier results are final.
    For a misdeal, use **Cancel round** instead.
  - The host is shown exactly what will be reversed and has to confirm.
- **Play for someone**: players without a phone can be added from the menu, and the host plays their moves.
  The host also gets the controls automatically when a player's phone is offline, and can take over any
  turn with **Play for …**.
- **Tap a player** to add or take chips (when they take more or give some back), mark cards seen, pack their hand, sit
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
./gradlew :core:test                    # game rules and networking tests
./gradlew :app:assembleGithubRelease    # APK in app/build/outputs/apk/github/release/
./gradlew :app:bundlePlayRelease        # Play Store bundle in app/build/outputs/bundle/playRelease/
```

Or open the folder in Android Studio and press Run.

The GitHub APK is signed with the key in `app/signing/` so that builds from GitHub Actions or any
computer can update each other on the phones. It is only meant for sharing this app with friends.
Anyone with the repository can sign an update, so don't reuse this key for anything else. The Play
Store bundle is signed with a private upload key that is never in the repository; see
[store/PLAY_STORE.md](store/PLAY_STORE.md).

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
