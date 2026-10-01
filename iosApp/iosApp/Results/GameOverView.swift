import SwiftUI
import SharedLogic

struct GameOverView: View {
    let gameId: String
    @Environment(AppModel.self) private var model
    @Environment(Router.self) private var router
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            if let sheet = Selectors.shared.scoreSheet(state: model.state, gameId: gameId) {
                content(sheet)
            }
        }
    }

    private func content(_ sheet: ScoreSheetModel) -> some View {
        let standings = sheet.columns.sorted { ($0.rank, $0.name) < ($1.rank, $1.name) }
        let winners = standings.filter(\.isWinner).map(\.name)
        return List {
            Section {
                VStack(spacing: 12) {
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(.yellow.gradient)
                        .symbolEffect(.bounce, value: winners)
                        .accessibilityHidden(true)
                    if winners.count > 1 {
                        Text("\(joined(winners)) share the win!")
                            .font(.title2.weight(.bold))
                    } else if let winner = winners.first {
                        Text("\(winner) wins!")
                            .font(.title2.weight(.bold))
                    }
                    HStack(spacing: 4) {
                        Text(verbatim: "\(sheet.type.title) ·")
                        Text("\(sheet.completedRounds.int) rounds")
                    }
                    .foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical)
                .accessibilityElement(children: .combine)
            }

            Section("Final standings") {
                ForEach(standings, id: \.playerId) { column in
                    HStack {
                        Text(verbatim: "\(column.rank).")
                            .font(.headline.monospacedDigit())
                            .frame(width: 32, alignment: .leading)
                        Text(column.name)
                            .font(column.isWinner ? .headline : .body)
                        Spacer()
                        Text(verbatim: "\(column.total)")
                            .font(.headline.monospacedDigit())
                    }
                    .accessibilityElement(children: .combine)
                }
            }

            Section {
                if sheet.canPlayExtraRound {
                    Button {
                        model.dispatch(Actions.shared.playExtraRound(gameId: gameId))
                        dismiss()
                    } label: {
                        Label("Play another round", systemImage: "plus.circle")
                    }
                }
                Button {
                    model.dispatch(Actions.shared.rematch(gameId: gameId))
                    dismiss()
                    router.replaceTop(with: .newGame(sheet.type))
                } label: {
                    Label("Rematch", systemImage: "arrow.clockwise")
                }
            }
        }
        .navigationTitle("Game over")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
            }
        }
    }
}
