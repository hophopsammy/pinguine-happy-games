import SwiftUI
import SharedLogic

struct NewGameView: View {
    let type: GameType
    @Environment(AppModel.self) private var model
    @Environment(Router.self) private var router
    @State private var newPlayer = ""

    var body: some View {
        Group {
            // The draft is created before navigating here and cleared once the game starts.
            if let setup = Selectors.shared.gameSetup(state: model.state), setup.type == type {
                form(setup)
            } else {
                Color(.systemGroupedBackground)
            }
        }
        .navigationTitle(type.title)
        .onDisappear {
            if model.state.newGame != nil { model.dispatch(Actions.shared.cancelNewGame()) }
        }
    }

    private func form(_ setup: NewGameModel) -> some View {
        Form {
            Section {
                HStack(spacing: 14) {
                    Image(systemName: type.symbol)
                        .font(.title)
                        .foregroundStyle(.white)
                        .frame(width: 52, height: 52)
                        .background(type.gradient, in: RoundedRectangle(cornerRadius: 12))
                        .accessibilityHidden(true)
                    Text(type.rules)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                ForEach(setup.choices, id: \.id) { choice in
                    Button { model.dispatch(Actions.shared.toggleNewGamePlayer(playerId: choice.id)) } label: {
                        HStack {
                            Image(systemName: choice.isSelected ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(choice.isSelected ? type.tint : .secondary)
                                .font(.title3)
                                .accessibilityHidden(true)
                            Text(choice.name)
                                .foregroundStyle(.primary)
                            Spacer()
                            if choice.isSelected {
                                Text("Seat \(choice.seatNumber.int)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                    .buttonStyle(.plain)
                    .contentShape(Rectangle())
                    .disabled(!choice.canSelect)
                    .accessibilityAddTraits(choice.isSelected ? .isSelected : [])
                }
                HStack {
                    TextField("New player", text: $newPlayer)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .onSubmit(addPlayer)
                    Button("Add", action: addPlayer)
                        .disabled(Selectors.shared.usernameIssue(state: model.state, name: newPlayer, editingPlayerId: nil) != nil)
                }
            } header: {
                Text("Who's playing? (\(setup.minPlayers.int)–\(setup.maxPlayers.int) players)")
            } footer: {
                if Selectors.shared.usernameIssue(state: model.state, name: newPlayer, editingPlayerId: nil) == .taken {
                    Text("This username is already taken.").foregroundStyle(.red)
                } else if let issue = setup.issue {
                    if issue == .tooFewPlayers {
                        Text("Pick at least \(setup.minPlayers.int) players.")
                    } else {
                        Text("Pick at most \(setup.maxPlayers.int) players.")
                    }
                }
            }

            if setup.seats.count > 1 {
                Section {
                    ForEach(setup.seats, id: \.id) { seat in
                        Text(seat.name)
                    }
                    .onMove { indices, destination in
                        guard let from = indices.first else { return }
                        model.dispatch(Actions.shared.moveNewGamePlayer(fromIndex: Int32(from), toIndex: Int32(destination)))
                    }
                } header: {
                    Text("Seating order")
                } footer: {
                    if type == .wizard {
                        Text("Drag to match the table. The first seat deals the first round, then the deal moves on.")
                    } else {
                        Text("Drag to match the table. This is also the order of the columns.")
                    }
                }
            }

            Section("Rounds") {
                rounds(setup)
            }
        }
        .environment(\.editMode, .constant(.active))
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Start") { start() }
                    .fontWeight(.semibold)
                    .disabled(!setup.canStart)
            }
        }
    }

    @ViewBuilder
    private func rounds(_ setup: NewGameModel) -> some View {
        switch type {
        case .skyjo:
            Text("Until someone reaches 100 points")
        case .biberbande:
            if setup.seats.isEmpty {
                Text("One round per player, unless you change it")
                    .foregroundStyle(.secondary)
            } else {
                Stepper(value: roundsBinding(setup), in: setup.minRounds.int...setup.maxRounds.int) {
                    Text("\(setup.plannedRounds.int) rounds")
                }
            }
        default:
            if setup.canStart {
                Text("\(setup.plannedRounds.int) rounds (60 cards ÷ \(setup.seats.count) players)")
            } else {
                Text("Depends on the number of players")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func roundsBinding(_ setup: NewGameModel) -> Binding<Int> {
        Binding(
            get: { setup.plannedRounds.int },
            set: { model.dispatch(Actions.shared.setNewGameRounds(rounds: Int32($0))) }
        )
    }

    private func addPlayer() {
        guard Selectors.shared.usernameIssue(state: model.state, name: newPlayer, editingPlayerId: nil) == nil else { return }
        model.dispatch(Actions.shared.addPlayerToNewGame(name: newPlayer))
        newPlayer = ""
    }

    private func start() {
        let action = Actions.shared.createGame()
        model.dispatch(action)
        if Selectors.shared.scoreSheet(state: model.state, gameId: action.gameId) != nil {
            router.replaceTop(with: .game(action.gameId))
        }
    }
}
