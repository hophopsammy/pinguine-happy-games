import Foundation
import Observation
import SharedLogic

/// Holds the latest Kotlin `AppState` for SwiftUI and forwards actions to the Kotlin store.
@MainActor
@Observable
final class AppModel {
    private(set) var state: AppState
    @ObservationIgnored let store: AppStore
    @ObservationIgnored private var observation: ObservationHandle?

    init(store: AppStore) {
        self.store = store
        self.state = store.state
        // The Kotlin store delivers changes on the main thread.
        observation = store.observe { [weak self] newState in
            MainActor.assumeIsolated { self?.state = newState }
        }
    }

    func dispatch(_ action: AppAction) {
        store.dispatch(action: action)
    }

    static func live() -> AppModel { AppModel(store: AppStoreFactory.shared.create()) }

    static func preview() -> AppModel { AppModel(store: AppStoreFactory.shared.preview()) }
}

/// Screens reachable from the home screen.
enum Route: Hashable {
    case players
    case statistics
    case history
    case sync
    case newGame(GameType)
    case game(String)
}

@MainActor
@Observable
final class Router {
    var path: [Route] = []

    /// Replaces the screen on top, e.g. the setup screen with the new game's sheet.
    func replaceTop(with route: Route) {
        if !path.isEmpty { path.removeLast() }
        path.append(route)
    }
}
