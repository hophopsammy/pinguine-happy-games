import SwiftUI
import SharedLogic

struct PlayersView: View {
    @Environment(AppModel.self) private var model
    @State private var editor: PlayerEditor?
    @State private var merge: MergeRequest?

    var body: some View {
        let players = Selectors.shared.players(state: model.state)
        List {
            if players.active.isEmpty && players.archived.isEmpty {
                ContentUnavailableView {
                    Label("No players yet", systemImage: "person.2")
                } description: {
                    Text("The same players can join every game. Their username is how your devices recognise them.")
                } actions: {
                    Button("Add player") { editor = .add }
                        .buttonStyle(.borderedProminent)
                }
            }
            if !players.active.isEmpty {
                Section {
                    ForEach(players.active, id: \.id) { row in
                        playerRow(row)
                    }
                }
            }
            if !players.archived.isEmpty {
                Section {
                    ForEach(players.archived, id: \.id) { row in
                        playerRow(row)
                    }
                } header: {
                    Text("Archived")
                } footer: {
                    Text("Archived players keep their statistics but can't be picked for new games.")
                }
            }
        }
        .navigationTitle("Players")
        .toolbar {
            Button { editor = .add } label: { Label("Add player", systemImage: "plus") }
        }
        .sheet(item: $editor) { editor in
            PlayerEditorSheet(editor: editor)
        }
        .sheet(item: $merge) { request in
            MergePlayerSheet(source: request.row)
        }
    }

    private func playerRow(_ row: PlayerRow) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(row.name).font(.headline)
            HStack(spacing: 4) {
                Text("\(row.gamesPlayed.int) games")
                Text(verbatim: "·")
                Text("\(row.wins.int) wins")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .swipeActions(edge: .trailing) {
            if row.canDelete {
                Button("Delete", systemImage: "trash", role: .destructive) {
                    model.dispatch(Actions.shared.deletePlayer(playerId: row.id))
                }
            } else if !row.isArchived {
                Button("Archive", systemImage: "archivebox") {
                    model.dispatch(Actions.shared.setPlayerArchived(playerId: row.id, archived: true))
                }
                .tint(.orange)
            }
            if row.isArchived {
                Button("Restore", systemImage: "arrow.uturn.backward") {
                    model.dispatch(Actions.shared.setPlayerArchived(playerId: row.id, archived: false))
                }
                .tint(.green)
            }
        }
        .swipeActions(edge: .leading) {
            Button("Rename", systemImage: "pencil") { editor = .rename(row) }
                .tint(.blue)
        }
        .contextMenu {
            Button("Rename", systemImage: "pencil") { editor = .rename(row) }
            if !row.mergeTargets.isEmpty {
                Button("Merge into…", systemImage: "arrow.triangle.merge") { merge = MergeRequest(row: row) }
            }
        }
    }
}

enum PlayerEditor: Identifiable {
    case add
    case rename(PlayerRow)

    var id: String {
        switch self {
        case .add: "add"
        case .rename(let row): "rename-\(row.id)"
        }
    }
}

private struct MergeRequest: Identifiable {
    let row: PlayerRow
    var id: String { row.id }
}

struct PlayerEditorSheet: View {
    let editor: PlayerEditor
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Username", text: $name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .focused($focused)
                        .onSubmit(save)
                } footer: {
                    switch issue {
                    case .taken: Text("This username is already taken.").foregroundStyle(.red)
                    default: Text("The username identifies the player on all your devices.")
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save).disabled(issue != nil)
                }
            }
            .onAppear {
                if case .rename(let row) = editor { name = row.name }
            }
            .task {
                // Focusing before the sheet finished presenting is ignored.
                try? await Task.sleep(for: .milliseconds(450))
                focused = true
            }
        }
        .presentationDetents([.medium])
    }

    private var title: LocalizedStringKey {
        switch editor {
        case .add: "New player"
        case .rename: "Rename player"
        }
    }

    private var editingId: String? {
        if case .rename(let row) = editor { return row.id }
        return nil
    }

    private var issue: UsernameIssue? {
        Selectors.shared.usernameIssue(state: model.state, name: name, editingPlayerId: editingId)
    }

    private func save() {
        guard issue == nil else { return }
        switch editor {
        case .add: model.dispatch(Actions.shared.addPlayer(name: name))
        case .rename(let row): model.dispatch(Actions.shared.renamePlayer(playerId: row.id, newName: name))
        }
        dismiss()
    }
}

private struct MergePlayerSheet: View {
    let source: PlayerRow
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var target: PlayerRef?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(source.mergeTargets, id: \.id) { candidate in
                        Button(candidate.name) { target = candidate }
                    }
                } footer: {
                    Text("Use this when the same person was added twice, for example on two devices. Players who played in the same game can't be merged.")
                }
            }
            .navigationTitle("Merge \(source.name) into…")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .confirmationDialog("Merge players?", isPresented: dialogPresented, titleVisibility: .visible, presenting: target) { target in
                Button("Merge into \(target.name)") {
                    model.dispatch(Actions.shared.mergePlayers(sourceId: source.id, targetId: target.id))
                    dismiss()
                }
            } message: { target in
                Text("All games of \(source.name) will belong to \(target.name). This also applies on your other devices.")
            }
        }
    }

    private var dialogPresented: Binding<Bool> {
        Binding(get: { target != nil }, set: { if !$0 { target = nil } })
    }
}
