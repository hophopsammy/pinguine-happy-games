import SwiftUI
import SharedLogic

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(Router.self) private var router

    var body: some View {
        @Bindable var router = router
        Group {
            switch model.state.loadStatus {
            case .ready:
                NavigationStack(path: $router.path) {
                    HomeView()
                        .navigationDestination(for: Route.self) { route in
                            destination(for: route)
                        }
                }
            case .failed:
                LoadFailedView()
            default:
                ProgressView()
            }
        }
        .sheet(isPresented: importPreviewPresented) {
            ImportPreviewView()
        }
        .alert("Couldn't import the file", isPresented: importErrorPresented) {
            Button("OK") { model.dispatch(Actions.shared.dismissErrors()) }
        } message: {
            Text(importErrorMessage)
        }
        .alert("Couldn't save your data", isPresented: persistErrorPresented) {
            Button("OK") { model.dispatch(Actions.shared.dismissErrors()) }
        } message: {
            Text("Your last change may not be saved. Try again or free up some storage.")
        }
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .players: PlayersView()
        case .statistics: StatisticsView()
        case .history: HistoryView()
        case .sync: SyncView()
        case .newGame(let type): NewGameView(type: type)
        case .game(let id): ScoreSheetView(gameId: id)
        }
    }

    private var importPreviewPresented: Binding<Bool> {
        Binding(
            get: { model.state.pendingImport != nil },
            set: { if !$0 { model.dispatch(Actions.shared.cancelImport()) } }
        )
    }

    private var importErrorPresented: Binding<Bool> {
        Binding(
            get: { model.state.importError != nil },
            set: { if !$0 { model.dispatch(Actions.shared.dismissErrors()) } }
        )
    }

    private var persistErrorPresented: Binding<Bool> {
        Binding(
            get: { model.state.persistError != nil },
            set: { if !$0 { model.dispatch(Actions.shared.dismissErrors()) } }
        )
    }

    private var importErrorMessage: LocalizedStringKey {
        switch model.state.importError {
        case .newerSchema: "This file was made by a newer version of the app. Update the app and try again."
        case .inconsistent: "This file is damaged: some games refer to players that aren't in it."
        default: "This isn't a Pinguine Spiele backup file."
        }
    }
}

private struct LoadFailedView: View {
    @Environment(AppModel.self) private var model
    @State private var confirmStartFresh = false

    var body: some View {
        ContentUnavailableView {
            Label("Couldn't open your games", systemImage: "exclamationmark.triangle")
        } description: {
            Text("The data file couldn't be read. It hasn't been changed.")
        } actions: {
            Button("Try again") { model.dispatch(Actions.shared.load()) }
                .buttonStyle(.borderedProminent)
            Button("Start fresh", role: .destructive) { confirmStartFresh = true }
        }
        .confirmationDialog("Start with empty data?", isPresented: $confirmStartFresh, titleVisibility: .visible) {
            Button("Start fresh", role: .destructive) { model.dispatch(Actions.shared.startFresh()) }
        } message: {
            Text("The unreadable file is kept on the device. You can bring your games back by importing a backup file.")
        }
    }
}

#Preview {
    RootView()
        .environment(AppModel.preview())
        .environment(Router())
}
