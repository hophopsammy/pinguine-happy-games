import CoreTransferable
import Foundation
import SharedLogic

/// The `.pinguine` export. The Kotlin store writes the file when the share sheet asks for it.
struct BackupFile: Transferable, @unchecked Sendable {
    let store: AppStore

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .pinguineBackup) { backup in
            let day = Date.now.formatted(.iso8601.year().month().day())
            let path = backup.store.writeSnapshotFile(fileName: "PinguineSpiele-\(day).pinguine")
            return SentTransferredFile(URL(fileURLWithPath: path))
        }
    }

    /// Reads a backup picked in Files or opened from AirDrop and hands it to Kotlin for the preview.
    @MainActor
    static func importBackup(from url: URL, into model: AppModel) {
        let accessing = url.startAccessingSecurityScopedResource()
        defer {
            if accessing { url.stopAccessingSecurityScopedResource() }
            // Files opened from AirDrop or Mail are copied into the Inbox; the data now lives in the app.
            if url.path.contains("/Inbox/") { try? FileManager.default.removeItem(at: url) }
        }
        guard let data = try? Data(contentsOf: url) else {
            model.dispatch(Actions.shared.importDump(json: ""))
            return
        }
        model.dispatch(Actions.shared.importDump(json: String(decoding: data, as: UTF8.self)))
    }
}
