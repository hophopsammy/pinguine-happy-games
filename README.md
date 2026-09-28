This app replaces the paper score pads for **Skyjo**, **Biberbande** (2002 AMIGO edition) and **Wizard**. It has a shared list of players, score sheets that look like the pads, a game history and statistics. It syncs between your devices through iCloud, and you can export or import a `.pinguine` backup file.

It is a Kotlin Multiplatform project with iOS as the only target. The UI is SwiftUI, and all logic lives in Kotlin.

## Where things are

* [/sharedLogic](./sharedLogic/src/commonMain/kotlin/com/pinguine/spiele) holds the Kotlin logic:
  * `model/`: players, games and rounds, all `@Serializable`.
  * `rules/`: scoring and end-of-game rules for each game (Skyjo doubling, Wizard bids, and so on).
  * `redux/`: ReduxKotlin store. It contains `AppState`, `AppAction`, the reducers, and the persistence and sync middleware. Swift talks to it through the `AppStore` facade, and creates actions with `Actions.shared`.
  * `selectors/`: turn the state into view models for each screen. Swift renders these as they are.
  * `persistence/`: a JSON file stored with [KStore](https://github.com/xxfast/KStore).
  * `sync/`: `DataMerger` merges data from other devices, matching players by username. `DumpCodec` reads and writes `.pinguine` files.
* [/iosApp](./iosApp/iosApp) holds the SwiftUI screens:
  * `CloudSyncService`: the CloudKit/`CKSyncEngine` side of iCloud sync.
  * `Localizable.xcstrings`: all UI text in English, German and Spanish.
* [/design/app-icon](./design/app-icon) holds the app icon as SVG, in light, dark and tinted versions. `render.sh` renders them into the asset catalog.

## Running

* Tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`
* App: open [/iosApp](./iosApp) in Xcode and run the `iosApp` scheme. The Kotlin framework is built by the "Compile Kotlin Framework" build phase.

## iCloud sync

`iosApp/Configuration/Config.xcconfig` sets the team, the bundle ID `com.pinguine.spiele.PinguineSpiele` and the container `iCloud.com.pinguine.spiele`. The entitlements are in `iosApp/Configuration/iosApp.entitlements`.

To finish the setup:

1. Connect an iPhone and run the app on it from Xcode once, with automatic signing. This registers the device, the App ID and the iCloud container in your developer account. Afterwards, check that the container is ticked under **Signing & Capabilities → iCloud**.
2. Sign in to the same Apple ID on every device you want to sync.
3. Before a TestFlight or App Store build, deploy the CloudKit schema to Production in the [CloudKit Console](https://icloud.developer.apple.com).

To build without iCloud, clear `CLOUDKIT_CONTAINER_ID` and `CODE_SIGN_ENTITLEMENTS` in `Config.xcconfig`.

How sync works: each device stores one snapshot record of everything it knows in the private CloudKit database, and merges all the snapshots it finds there, including its own. Because devices never write each other's records, their writes can't conflict. A device only uploads after its first download of the session has been merged, so after "Start fresh" its games come back from iCloud instead of being overwritten. The simulator can't receive CloudKit pushes, so there you can use "Sync now" or bring the app back to the foreground instead.
