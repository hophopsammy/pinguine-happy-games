import SwiftUI
import SharedLogic

struct SyncView: View {
    @Environment(AppModel.self) private var model
    @State private var importing = false

    var body: some View {
        List {
            Section {
                ShareLink(
                    item: BackupFile(store: model.store),
                    preview: SharePreview("Pinguine Spiele backup", image: Image(systemName: "doc.badge.arrow.up"))
                ) {
                    Label("Export data", systemImage: "square.and.arrow.up")
                }
                Button { importing = true } label: {
                    Label("Import data…", systemImage: "square.and.arrow.down")
                }
            } footer: {
                Text("To bring another device up to date, export here and import the .pinguine file there, for example with AirDrop. Importing adds its games to yours and matches players by username. The file also works as a backup.")
            }
        }
        .navigationTitle("Sync")
        .fileImporter(isPresented: $importing, allowedContentTypes: [.pinguineBackup, .json]) { result in
            if case .success(let url) = result {
                BackupFile.importBackup(from: url, into: model)
            }
        }
    }
}

struct ImportPreviewView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            if let preview = Selectors.shared.importPreview(state: model.state) {
                content(preview)
            }
        }
    }

    private func content(_ preview: ImportPreviewModel) -> some View {
        let report = preview.report
        return List {
            Section {
                if preview.exportedAt > 0 {
                    LabeledContent("Exported") {
                        Text(preview.exportedAt.date, format: .dateTime.day().month().year().hour().minute())
                    }
                }
                LabeledContent("Players in file") { Text(verbatim: "\(preview.incomingPlayers)") }
                LabeledContent("Games in file") { Text(verbatim: "\(preview.incomingGames)") }
            }

            Section("What changes") {
                if report.hasChanges {
                    if report.newGames > 0 { Label("New games: \(report.newGames.int)", systemImage: "plus.circle") }
                    if report.updatedGames > 0 { Label("Updated games: \(report.updatedGames.int)", systemImage: "arrow.triangle.2.circlepath") }
                    if report.removedGames > 0 { Label("Removed games: \(report.removedGames.int)", systemImage: "minus.circle") }
                    if !report.newPlayers.isEmpty { Label("New players: \(joined(report.newPlayers))", systemImage: "person.badge.plus") }
                    ForEach(report.renamedPlayers, id: \.from) { renamed in
                        Label("\(renamed.from) is now called \(renamed.to)", systemImage: "pencil")
                    }
                    if !report.combinedPlayers.isEmpty {
                        Label("Merged players: \(joined(report.combinedPlayers))", systemImage: "arrow.triangle.merge")
                    }
                } else {
                    Label("Everything in this file is already on this device.", systemImage: "checkmark.circle")
                }
                if report.matchedPlayers > 0 {
                    Label("Players matched by username: \(report.matchedPlayers.int)", systemImage: "person.2")
                        .foregroundStyle(.secondary)
                }
            }

            if !report.conflicts.isEmpty {
                Section {
                    ForEach(report.conflicts, id: \.firstName) { conflict in
                        Text("\(conflict.firstName) and \(conflict.secondName) stay separate players because they played together.")
                    }
                }
            }
        }
        .navigationTitle("Import data")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { model.dispatch(Actions.shared.cancelImport()) }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Import") { model.dispatch(Actions.shared.confirmImport()) }
                    .fontWeight(.semibold)
                    .disabled(!report.hasChanges)
            }
        }
    }
}
