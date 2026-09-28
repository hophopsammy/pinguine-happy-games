This app replaces the paper score pads for **Skyjo**, **Biberbande** (2002 AMIGO edition) and **Wizard**. It has a shared list of players, score sheets that look like the pads, a game history and statistics. To bring your devices up to date, you export a `.pinguine` file on one and import it on the other.

It is a Kotlin Multiplatform project with iOS as the only target. The UI is SwiftUI, and all logic lives in Kotlin.

## Where things are

* [/sharedLogic](./sharedLogic/src/commonMain/kotlin/com/pinguine/spiele) holds the Kotlin logic:
  * `model/`: players, games and rounds, all `@Serializable`.
  * `rules/`: scoring and end-of-game rules for each game (Skyjo doubling, Wizard bids, and so on).
  * `redux/`: ReduxKotlin store. It contains `AppState`, `AppAction`, the reducers, and the persistence and import middleware. Swift talks to it through the `AppStore` facade, and creates actions with `Actions.shared`.
  * `selectors/`: turn the state into view models for each screen. Swift renders these as they are.
  * `persistence/`: a JSON file stored with [KStore](https://github.com/xxfast/KStore).
  * `sync/`: `DataMerger` merges data from other devices, matching players by username. `DumpCodec` reads and writes `.pinguine` files.
* [/iosApp](./iosApp/iosApp) holds the SwiftUI screens. `Localizable.xcstrings` has all UI text in English, German and Spanish.
* [/design/app-icon](./design/app-icon) holds the app icon as SVG, in light, dark and tinted versions. `render.sh` renders them into the asset catalog.

## Running

* Tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`
* App: open [/iosApp](./iosApp) in Xcode and run the `iosApp` scheme. The Kotlin framework is built by the "Compile Kotlin Framework" build phase.

## Syncing devices

Open **Sync**, tap **Export data** and send the `.pinguine` file to the other device, for example with AirDrop. Open it there (or use **Import data…**). The app previews what changes, then merges the file into its own data:

* Players are matched by username, so a player who exists on both devices isn't added twice. Renamed and merged players carry over.
* For a game on both devices, the most recently changed version wins.
* A game deleted on one device is also removed on the other once that device imports the file.

To keep two devices fully in step, import in both directions. The same file also works as a backup.
