import SwiftUI
import SharedLogic

struct ScoreSheetView: View {
    let gameId: String
    @Environment(AppModel.self) private var model
    @Environment(Router.self) private var router
    @State private var availableWidth: CGFloat = 360
    @State private var showGameOver = false
    @State private var entryShowing = false
    @State private var gameOverPending = false
    @State private var confirmation: Confirmation?

    private enum Confirmation: Identifiable {
        case undo, end, delete
        var id: Self { self }
    }

    var body: some View {
        if let sheet = Selectors.shared.scoreSheet(state: model.state, gameId: gameId) {
            content(sheet)
        } else {
            ContentUnavailableView("This game was deleted", systemImage: "trash")
        }
    }

    private func content(_ sheet: ScoreSheetModel) -> some View {
        ScrollView(.vertical) {
            ScrollView(.horizontal, showsIndicators: false) {
                PaperSheet(sheet: sheet, availableWidth: availableWidth - 32) { row in
                    tapped(row, sheet: sheet)
                }
                .padding(16)
            }
            .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { availableWidth = $0 }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) { bottomBar(sheet) }
        .navigationTitle(sheet.type.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { menu(sheet) }
        .sheet(isPresented: entryPresented, onDismiss: entryDismissed) {
            RoundEntryView()
        }
        .sheet(isPresented: $showGameOver) {
            GameOverView(gameId: gameId)
        }
        .onChange(of: model.state.roundEntry?.gameId == gameId) { _, showing in
            if showing { entryShowing = true }
        }
        .onChange(of: sheet.isFinished) { wasFinished, isFinished in
            guard !wasFinished, isFinished else { return }
            if entryShowing { gameOverPending = true } else { showGameOver = true }
        }
        .confirmationDialog(confirmationTitle, isPresented: confirmationPresented, titleVisibility: .visible, presenting: confirmation) { kind in
            switch kind {
            case .undo:
                Button("Undo last round", role: .destructive) { model.dispatch(Actions.shared.deleteLastRound(gameId: gameId)) }
            case .end:
                Button("End game now") { model.dispatch(Actions.shared.endGameNow(gameId: gameId)) }
            case .delete:
                Button("Delete game", role: .destructive) {
                    model.dispatch(Actions.shared.deleteGame(gameId: gameId))
                    router.path.removeLast()
                }
            }
        } message: { kind in
            switch kind {
            case .undo: Text("The last round's scores are removed.")
            case .end: Text("The current totals decide the winner.")
            case .delete: Text("The game is removed from all your devices and doesn't count for the statistics.")
            }
        }
    }

    // MARK: Bottom bar and menu

    @ViewBuilder
    private func bottomBar(_ sheet: ScoreSheetModel) -> some View {
        let round = sheet.nextRoundIndex.int + 1
        Group {
            switch sheet.nextStep {
            case .enterPoints:
                Button { begin(sheet.nextRoundIndex) } label: {
                    Label("Enter round \(round)", systemImage: "square.and.pencil").frame(maxWidth: .infinity)
                }
            case .enterBids:
                Button { begin(sheet.nextRoundIndex) } label: {
                    Label("Enter bids for round \(round)", systemImage: "hand.raised").frame(maxWidth: .infinity)
                }
            case .enterTricks:
                Button { begin(sheet.nextRoundIndex) } label: {
                    Label("Enter tricks for round \(round)", systemImage: "checkmark.circle").frame(maxWidth: .infinity)
                }
            default:
                Button { showGameOver = true } label: {
                    Label("Show results", systemImage: "trophy").frame(maxWidth: .infinity)
                }
            }
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .tint(sheet.type.tint)
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.bar)
    }

    private func menu(_ sheet: ScoreSheetModel) -> some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                if sheet.canUndoLastRound {
                    Button("Undo last round", systemImage: "arrow.uturn.backward") { confirmation = .undo }
                }
                if sheet.canEndNow {
                    Button("End game now", systemImage: "flag.checkered") { confirmation = .end }
                }
                if sheet.isFinished {
                    Button("Rematch", systemImage: "arrow.clockwise") {
                        model.dispatch(Actions.shared.rematch(gameId: gameId))
                        router.replaceTop(with: .newGame(sheet.type))
                    }
                }
                Button("Delete game", systemImage: "trash", role: .destructive) { confirmation = .delete }
            } label: {
                Label("More", systemImage: "ellipsis.circle")
            }
        }
    }

    private var confirmationTitle: LocalizedStringKey {
        switch confirmation {
        case .undo: "Undo the last round?"
        case .end: "End the game now?"
        default: "Delete this game?"
        }
    }

    private var confirmationPresented: Binding<Bool> {
        Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } })
    }

    // MARK: Round entry

    private var entryPresented: Binding<Bool> {
        Binding(
            get: { model.state.roundEntry?.gameId == gameId },
            set: { if !$0 { model.dispatch(Actions.shared.cancelRoundEntry()) } }
        )
    }

    private func entryDismissed() {
        entryShowing = false
        if gameOverPending {
            gameOverPending = false
            showGameOver = true
        }
    }

    private func tapped(_ row: SheetRow, sheet: ScoreSheetModel) {
        if row.canEdit || row.state == .next {
            begin(row.roundIndex)
        }
    }

    private func begin(_ roundIndex: Int32) {
        model.dispatch(Actions.shared.beginRoundEntry(gameId: gameId, roundIndex: roundIndex))
    }
}
