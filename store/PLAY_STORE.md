# Publishing on Google Play

Everything needed to put 3 Patti Chips Handler on the Play Store: the app bundle, the listing text,
graphics and the answers Play Console asks for.

## Two versions of the app

| | GitHub APK | Google Play |
| --- | --- | --- |
| App ID | `com.threepatti.tracker` | `com.afler.chipshandler` |
| File | `3patti-chips-handler.apk` | `3patti-chips-handler-play.aab` |
| Signed with | the shared key in `app/signing/` | the private upload key; Play re-signs it |

They are separate apps, so a phone can have both. Phones that installed the GitHub APK keep getting
updates from GitHub. Once everyone has the Play version, the old app can be deleted (finish or delete
any saved table on it first). Both versions play at the same table together.

## 1. The upload key (once)

Google Play only accepts bundles signed with your private **upload key**. It is not in the repo,
because the repo is public. Keep `upload-keystore.jks` and its password somewhere safe, such as your
Google Drive. If it is ever lost, Play support can reset it, but that takes a few days.

So that every push to `main` also builds the Play bundle, add two secrets on GitHub:
**repository → Settings → Secrets and variables → Actions → New repository secret**

| Name | Value |
| --- | --- |
| `PLAY_UPLOAD_KEYSTORE_BASE64` | the whole text of `upload-keystore-base64.txt` |
| `PLAY_UPLOAD_PASSWORD` | the password |

From then on the [latest release](https://github.com/itz-puneet/3-patti_local/releases/latest) also has `3patti-chips-handler-play.aab`.
That is the file to upload to Play (it can't be installed on a phone directly). Its version number
goes up with every build, as Play requires.

## 2. Developer account

1. Sign up at https://play.google.com/console (a one-time fee; Google verifies your identity).
2. **Developer name**: `Afler`. This is the name shown on the store.
3. **Contact email**: Play shows it publicly on the listing. Use an email made for the app (for example
   a new Gmail for Afler) if you don't want your personal one shown.
4. The app is free and sells nothing. If Play asks whether you are a *trader* (an EU rule), you can
   answer that you're not; then your address isn't shown. Check the wording in Play Console when you
   get there.

New personal accounts must run a **closed test** before releasing to everyone: at the time of writing
at least 12 testers who stay opted in for 14 days. Your friends who play together are ideal testers.

## 3. Create the app

Play Console → **Create app**

- App name: `3 Patti Chips Handler`
- Default language: English (India) – en-IN
- App or game: **App** (it doesn't deal cards itself, it keeps score)
- Free or paid: **Free**

When asked about app signing, let Google manage the app signing key (the default).

## 4. Store listing

**Short description** (80 characters max)

> Track chips, bets and the pot for 3 Patti and poker played with real cards

**Full description**

> 3 Patti Chips Handler keeps track of chips while you play 3 Patti (Teen Patti) or poker with real
> cards. No more paper slips, coins or arguments about who owes whom.
>
> One phone hosts the table and everyone else joins from their own phone on the same WiFi or the
> host's hotspot. No internet or account needed. Friends with an iPhone join from Safari by scanning a
> QR code.
>
> 3 PATTI
> • Boot collected automatically every round
> • Blind and seen (chaal) bets, raises, chaal limit and pot limit
> • Show and side show
> • Not sure who won? Enter the cards and Decide winner names each hand and the winner: trail, pure
>   sequence, sequence, color, pair or high card
> • Muflis, AK47 and Joker variants, with jokers worked out for you
> • The winner deals the next round, so the player after them goes first
>
> POKER
> • Small and big blinds, with optional antes
> • No limit or pot limit
> • Minimum raises, all-ins and side pots worked out for you
>
> A REAL TABLE ON YOUR SCREEN
> • Everyone seated round the table in turn order, the pot in the middle
> • See whose turn it is, who is blind or seen, and what everyone has put in
> • Your phone vibrates when it's your turn
>
> FOR THE HOST
> • Play for friends without a phone
> • Add or take chips, sit players out, change the seat order
> • Undo mistakes; every undo stays in the history for everyone to see
> • The table is saved automatically, so you can resume after closing the app
>
> AT THE END
> • A ledger with everyone's profit or loss
> • A settle-up list of who owes whom
>
> The app only counts chips. There is no betting with real money in the app, no online play, no ads
> and no in-app purchases.

**Graphics** (in this folder)

| Play Console asks for | File |
| --- | --- |
| App icon, 512 × 512 | `icon-512.png` |
| Feature graphic, 1024 × 500 | `feature-graphic.png` |
| Phone screenshots | `screenshots/01-table.png` to `06-home.png` |

**Category**: Entertainment. **Tags**: card games, scorekeeper.

## 5. App content

Play Console → **Policy → App content**. Answer for what the app really does; these notes describe it.

- **Privacy policy**: https://github.com/itz-puneet/3-patti_local/blob/main/PRIVACY.md
- **Ads**: No ads.
- **App access**: All features work without logging in.
- **Content rating**: fill in the questionnaire. The app has no violence, sexual content, bad
  language, drugs, user-to-user chat or internet sharing. It is a score keeper for card games; it does
  not deal cards, run games of chance, or take or pay out money.
- **Target audience**: 18 and over. The app is about games played for stakes, so keep it adults only;
  this also keeps it out of the Families programme.
- **Data safety**: the app has no accounts, ads or analytics and sends nothing to you or any server.
  Player names and chips go only directly to the other phones at the same table, on the local WiFi the
  players choose. On that basis the answer is *No, the app doesn't collect or share user data*. Data
  is deleted by deleting the table or uninstalling the app.
- **Advertising ID**: not used.
- **Government app, financial features, health, news**: none.
- **Foreground service permissions**: declare *Connected device*. Description you can use:
  "When a phone hosts a table, a foreground service keeps the local game server running so that the
  other players' phones, connected over the same WiFi or hotspot, stay connected and in sync while the
  host's screen is off or another app is open. It starts when the host opens a table and stops when the
  table is closed." Play asks for a short video: record the host opening a table, the notification
  appearing, and another phone joining and staying connected with the host's screen off. Upload it to
  YouTube as *unlisted* and paste the link.

## 6. Test, then release

1. **Testing → Closed testing → Create track**. Add your testers' Gmail addresses (or a Google
   Group), upload `3patti-chips-handler-play.aab`, write short release notes and roll it out.
2. Share the opt-in link with the testers. They install from Play and play as usual.
3. After the testing period, **Apply for production** from the dashboard, then create a production
   release with the latest bundle.

## Updating later

Push changes to `main`. GitHub builds a new bundle with a higher version number and attaches it to
the latest release. In Play Console create a new release on the same track and upload it.
