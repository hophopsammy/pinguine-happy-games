import Charts
import SwiftUI
import SharedLogic

struct StatisticsView: View {
    @Environment(AppModel.self) private var model
    @State private var filter: GameType?

    var body: some View {
        let stats = Selectors.shared.statistics(state: model.state, filter: filter)
        List {
            Section {
                Picker("Game", selection: $filter) {
                    Text("All").tag(GameType?.none)
                    ForEach(GameType.entries, id: \.self) { type in
                        Text(type.title).tag(Optional(type))
                    }
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            if stats.gamesPlayed == 0 {
                ContentUnavailableView(
                    "No finished games yet",
                    systemImage: "chart.bar.xaxis",
                    description: Text("Statistics appear once a game is finished.")
                )
            } else {
                Section {
                    if filter == nil {
                        ForEach(stats.perType, id: \.type) { count in
                            LabeledContent {
                                Text(verbatim: "\(count.count)").monospacedDigit()
                            } label: {
                                Label(count.type.title, systemImage: count.type.symbol)
                                    .foregroundStyle(count.type.tint)
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }
                    LabeledContent("Games played") {
                        Text(verbatim: "\(stats.gamesPlayed)").monospacedDigit().fontWeight(.semibold)
                    }
                }

                Section("Wins") {
                    Chart(stats.leaderboard, id: \.playerId) { row in
                        BarMark(
                            x: .value("Wins", row.wins.int),
                            y: .value("Player", row.name)
                        )
                        .foregroundStyle((filter ?? .skyjo).tint.gradient)
                        .annotation(position: .trailing) {
                            Text(verbatim: "\(row.wins)").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .chartXAxis(.hidden)
                    .frame(height: CGFloat(stats.leaderboard.count) * 34 + 16)
                    .padding(.vertical, 8)
                    // Same numbers as the Leaderboard section below, in an accessible list form.
                    .accessibilityHidden(true)
                }

                Section("Leaderboard") {
                    ForEach(stats.leaderboard, id: \.playerId) { row in
                        LeaderboardRowView(row: row)
                    }
                }

                if !stats.records.isEmpty {
                    Section {
                        ForEach(stats.records, id: \.type) { record in
                            NavigationLink(value: Route.game(record.gameId)) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(record.type.title).font(.headline)
                                    Text(verbatim: "\(joined(record.playerNames)) · \(record.total)")
                                    Text(record.date.date, format: .dateTime.day().month().year())
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                .accessibilityElement(children: .combine)
                            }
                        }
                    } header: {
                        Text("Records")
                    } footer: {
                        Text("The best final score: lowest for Skyjo and Biberbande, highest for Wizard.")
                    }
                }
            }

            Section {
                NavigationLink(value: Route.history) {
                    Label("Game history", systemImage: "clock.arrow.circlepath")
                }
            }
        }
        .navigationTitle("Statistics")
    }
}

struct HistoryView: View {
    @Environment(AppModel.self) private var model
    @State private var gameToDelete: HistoryRow?

    var body: some View {
        let history = Selectors.shared.history(state: model.state)
        List {
            if history.isEmpty {
                ContentUnavailableView("No finished games yet", systemImage: "clock")
            }
            ForEach(history, id: \.gameId) { row in
                NavigationLink(value: Route.game(row.gameId)) {
                    HStack(spacing: 12) {
                        Image(systemName: row.type.symbol)
                            .foregroundStyle(row.type.tint)
                            .font(.title2)
                            .frame(width: 36)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text(row.type.title).font(.headline)
                                Spacer()
                                Text(row.endedAt.date, format: .dateTime.day().month().year())
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Text("Winner: \(joined(row.winnerNames)) (\(row.winningTotal.int))")
                                .font(.subheadline)
                            Text(joined(row.playerNames))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
                .swipeActions {
                    Button("Delete", systemImage: "trash", role: .destructive) { gameToDelete = row }
                }
            }
        }
        .navigationTitle("Game history")
        .confirmationDialog("Delete this game?", isPresented: deleteDialogPresented, titleVisibility: .visible, presenting: gameToDelete) { row in
            Button("Delete game", role: .destructive) { model.dispatch(Actions.shared.deleteGame(gameId: row.gameId)) }
        } message: { _ in
            Text("The game is removed and no longer counts for the statistics.")
        }
    }

    private var deleteDialogPresented: Binding<Bool> {
        Binding(get: { gameToDelete != nil }, set: { if !$0 { gameToDelete = nil } })
    }
}
