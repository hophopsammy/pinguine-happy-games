import SwiftUI
import SharedLogic

struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(Router.self) private var router
    @Environment(CloudSyncService.self) private var cloud
    @State private var gameToDelete: GameSummary?

    var body: some View {
        let home = Selectors.shared.home(state: model.state)
        List {
            if home.activePlayerCount < 2 {
                Section {
                    Button { router.path.append(.players) } label: {
                        Label("Add the players first", systemImage: "person.badge.plus")
                    }
                } footer: {
                    Text("Players are shared by all games. Add everyone who plays at your table.")
                }
            }

            Section("New game") {
                ForEach(home.tiles, id: \.type) { tile in
                    Button { startNewGame(tile.type) } label: { GameTileView(tile: tile) }
                        .listRowBackground(tile.type.gradient)
                }
            }

            if !home.inProgress.isEmpty {
                Section("Continue") {
                    ForEach(home.inProgress, id: \.gameId) { game in
                        NavigationLink(value: Route.game(game.gameId)) { RunningGameRow(game: game) }
                            .swipeActions {
                                Button("Delete", systemImage: "trash", role: .destructive) { gameToDelete = game }
                            }
                    }
                }
            }

            if !home.topPlayers.isEmpty {
                Section {
                    ForEach(home.topPlayers, id: \.playerId) { row in
                        LeaderboardRowView(row: row)
                    }
                    NavigationLink(value: Route.statistics) {
                        Label("All statistics", systemImage: "chart.bar.xaxis")
                    }
                } header: {
                    Text("Most wins")
                }
            }
        }
        .navigationTitle("Pinguine Spiele")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { router.path.append(.players) } label: { Label("Players", systemImage: "person.2") }
                Button { router.path.append(.statistics) } label: { Label("Statistics", systemImage: "chart.bar.xaxis") }
                Button { router.path.append(.sync) } label: { Label("Sync", systemImage: cloud.toolbarSymbol) }
            }
        }
        .confirmationDialog("Delete this game?", isPresented: deleteDialogPresented, titleVisibility: .visible, presenting: gameToDelete) { game in
            Button("Delete game", role: .destructive) { model.dispatch(Actions.shared.deleteGame(gameId: game.gameId)) }
        } message: { _ in
            Text("The game is removed from all your devices and doesn't count for the statistics.")
        }
    }

    private var deleteDialogPresented: Binding<Bool> {
        Binding(get: { gameToDelete != nil }, set: { if !$0 { gameToDelete = nil } })
    }

    private func startNewGame(_ type: GameType) {
        model.dispatch(Actions.shared.startNewGame(type: type))
        router.path.append(.newGame(type))
    }
}

private struct GameTileView: View {
    let tile: GameTile

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: tile.type.symbol)
                .font(.title)
                .frame(width: 52, height: 52)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 4) {
                Text(tile.type.title)
                    .font(.title2.weight(.bold))
                Text("\(tile.minPlayers.int)–\(tile.maxPlayers.int) players")
                    .font(.subheadline)
                if tile.finishedCount > 0 {
                    Text("\(tile.finishedCount.int) games played")
                        .font(.caption)
                        .opacity(0.85)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.headline)
                .opacity(0.7)
        }
        .foregroundStyle(.white)
        .padding(.vertical, 8)
    }
}

struct RunningGameRow: View {
    let game: GameSummary

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: game.type.symbol)
                .foregroundStyle(game.type.tint)
                .font(.title2)
                .frame(width: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(game.type.title)
                    .font(.headline)
                Text(joined(game.playerNames))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(progress)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var progress: String {
        // The round being played now, as written on the pad.
        let current = game.completedRounds.int + 1
        let rounds = game.plannedRounds > 0
            ? String(localized: "Round \(min(current, game.plannedRounds.int)) of \(game.plannedRounds.int)")
            : String(localized: "Round \(current)")
        guard !game.leaderNames.isEmpty else { return rounds }
        return String(localized: "\(rounds) · \(joined(game.leaderNames)) leading")
    }
}

struct LeaderboardRowView: View {
    let row: LeaderboardRow

    var body: some View {
        HStack(spacing: 12) {
            Text(verbatim: "\(row.rank)")
                .font(.headline.monospacedDigit())
                .frame(width: 28, height: 28)
                .background(medal.opacity(0.2), in: Circle())
                .foregroundStyle(medal)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.name).font(.headline)
                HStack(spacing: 4) {
                    Text("\(row.played.int) games")
                    Text(verbatim: "·")
                    Text("\((Double(row.winRatePercent) / 100).formatted(.percent)) won")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer()
            Text("\(row.wins.int) wins")
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
        }
    }

    private var medal: Color {
        switch row.rank {
        case 1: .yellow
        case 2: .gray
        case 3: .brown
        default: .secondary
        }
    }
}
