import CloudKit
import Foundation
import Observation
import SharedLogic

/// Syncs through the private iCloud database. Every device uploads one record holding a snapshot of
/// everything it knows and merges the snapshots of the other devices (the merging happens in Kotlin),
/// so devices never overwrite each other's records.
///
/// Sync stays off until the build has a CloudKit container (`CLOUDKIT_CONTAINER_ID` in Config.xcconfig
/// plus the iCloud capability): creating a container without the entitlement would crash.
@MainActor
@Observable
final class CloudSyncService {
    enum Status: Equatable {
        case notConfigured
        case off
        case waiting
        case noAccount
        case syncing
        case upToDate(Date)
        case failed
    }

    private(set) var status: Status
    let isConfigured: Bool

    var isEnabled: Bool {
        didSet {
            guard isEnabled != oldValue else { return }
            UserDefaults.standard.set(isEnabled, forKey: Keys.enabled)
            if isEnabled { start() } else { stop() }
        }
    }

    @ObservationIgnored private let containerIdentifier: String?
    @ObservationIgnored private let model: AppModel
    @ObservationIgnored private var engine: CKSyncEngine?
    @ObservationIgnored private var dataIsLoaded = false
    @ObservationIgnored private var hasFetchedThisSession = false
    @ObservationIgnored private var uploadWaiting = false

    private static let zoneID = CKRecordZone.ID(zoneName: "PinguineSync")
    private static let recordType = "DeviceSnapshot"
    private enum Keys {
        static let enabled = "iCloudSyncEnabled"
        static let deviceID = "iCloudDeviceID"
        static let systemFields = "iCloudSnapshotSystemFields"
    }

    /// Pass `containerIdentifier: nil` to keep iCloud off, e.g. in SwiftUI previews.
    init(model: AppModel, containerIdentifier: String? = CloudSyncService.configuredContainer) {
        self.model = model
        self.containerIdentifier = containerIdentifier
        isConfigured = containerIdentifier != nil
        isEnabled = UserDefaults.standard.object(forKey: Keys.enabled) as? Bool ?? true
        status = containerIdentifier == nil ? .notConfigured : .waiting
    }

    /// The container from `CLOUDKIT_CONTAINER_ID` in Config.xcconfig, or nil when it isn't set.
    nonisolated static var configuredContainer: String? {
        let identifier = (Bundle.main.object(forInfoDictionaryKey: "PinguineCloudKitContainer") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return identifier?.isEmpty == false ? identifier : nil
    }

    // MARK: Called by the app

    func dataLoaded() {
        dataIsLoaded = true
        start()
    }

    func localDataChanged() {
        queueUpload()
    }

    /// Called when the local data file is replaced by empty data. The next start fetches everything
    /// again, including this device's own snapshot, so the games come back from iCloud.
    func resetForFreshStart() {
        engine = nil
        Self.clearState()
        Self.clearSystemFields()
    }

    func appBecameActive() {
        guard let engine else { return }
        Task { try? await engine.fetchChanges() }
    }

    func syncNow() {
        guard let engine else { return }
        status = .syncing
        Task {
            do {
                try await engine.fetchChanges()
                try await engine.sendChanges()
                status = .upToDate(.now)
            } catch {
                status = .failed
            }
        }
    }

    var isSyncing: Bool { status == .syncing }

    var canSyncNow: Bool { engine != nil && status != .syncing && status != .noAccount }

    var statusText: String {
        switch status {
        case .notConfigured: String(localized: "iCloud sync isn't set up in this build yet.")
        case .off: String(localized: "Off")
        case .waiting: String(localized: "Waiting…")
        case .noAccount: String(localized: "Sign in to iCloud in Settings to sync.")
        case .syncing: String(localized: "Syncing…")
        case .upToDate(let date): String(localized: "Up to date, last synced \(date.formatted(.relative(presentation: .named)))")
        case .failed: String(localized: "Sync failed. It will try again later.")
        }
    }

    var toolbarSymbol: String {
        switch status {
        case .notConfigured, .off: "icloud.slash"
        case .noAccount, .failed: "exclamationmark.icloud"
        case .upToDate: "checkmark.icloud"
        case .waiting, .syncing: "arrow.triangle.2.circlepath.icloud"
        }
    }

    // MARK: Engine

    private func start() {
        guard let containerIdentifier else { return }
        guard isEnabled else { status = .off; return }
        guard dataIsLoaded, engine == nil else { return }
        let container = CKContainer(identifier: containerIdentifier)
        var configuration = CKSyncEngine.Configuration(
            database: container.privateCloudDatabase,
            stateSerialization: Self.loadState(),
            delegate: self
        )
        configuration.automaticallySync = true
        let engine = CKSyncEngine(configuration)
        self.engine = engine
        hasFetchedThisSession = false
        uploadWaiting = true // Upload once per session, after the first fetch.
        status = .syncing
        engine.state.add(pendingDatabaseChanges: [.saveZone(CKRecordZone(zoneID: Self.zoneID))])
        Task {
            await refreshAccountStatus(container)
            try? await engine.fetchChanges()
        }
    }

    private func stop() {
        let stopped = engine
        engine = nil
        status = isConfigured ? .off : .notConfigured
        Task { await stopped?.cancelOperations() }
    }

    private func restart() {
        engine = nil
        start()
    }

    /// Our snapshot is only uploaded once this session has fetched what's on the server, so a device
    /// whose data was reset can't overwrite its old snapshot before reading it back.
    private func queueUpload() {
        guard let engine else { return }
        guard hasFetchedThisSession else {
            uploadWaiting = true
            return
        }
        engine.state.add(pendingRecordZoneChanges: [.saveRecord(Self.ownRecordID)])
    }

    private func refreshAccountStatus(_ container: CKContainer) async {
        let accountStatus = try? await container.accountStatus()
        if accountStatus != .available { status = .noAccount }
    }

    private static var ownRecordID: CKRecord.ID {
        CKRecord.ID(recordName: "device-\(deviceID)", zoneID: zoneID)
    }

    private static var deviceID: String {
        if let existing = UserDefaults.standard.string(forKey: Keys.deviceID) { return existing }
        let created = UUID().uuidString
        UserDefaults.standard.set(created, forKey: Keys.deviceID)
        return created
    }
}

// MARK: - CKSyncEngineDelegate

extension CloudSyncService: CKSyncEngineDelegate {
    func handleEvent(_ event: CKSyncEngine.Event, syncEngine: CKSyncEngine) async {
        guard syncEngine === engine else { return }
        switch event {
        case .stateUpdate(let update):
            Self.saveState(update.stateSerialization)
        case .accountChange(let change):
            handleAccountChange(change)
        case .fetchedDatabaseChanges(let changes):
            if changes.deletions.contains(where: { $0.zoneID == Self.zoneID }) {
                // The iCloud data was reset: create the zone again and upload everything.
                Self.clearSystemFields()
                syncEngine.state.add(pendingDatabaseChanges: [.saveZone(CKRecordZone(zoneID: Self.zoneID))])
                queueUpload()
            }
        case .fetchedRecordZoneChanges(let changes):
            // Awaited, so the merge is done before the sync engine moves on (and possibly uploads).
            await handleFetched(changes)
        case .sentRecordZoneChanges(let sent):
            handleSent(sent, engine: syncEngine)
        case .willFetchChanges, .willSendChanges:
            if status != .noAccount { status = .syncing }
        case .didFetchChanges:
            hasFetchedThisSession = true
            if uploadWaiting {
                uploadWaiting = false
                queueUpload()
            }
            if status != .noAccount { status = .upToDate(.now) }
        case .didSendChanges:
            if status != .noAccount { status = .upToDate(.now) }
        default:
            break
        }
    }

    func nextRecordZoneChangeBatch(
        _ context: CKSyncEngine.SendChangesContext,
        syncEngine: CKSyncEngine
    ) async -> CKSyncEngine.RecordZoneChangeBatch? {
        let pending = syncEngine.state.pendingRecordZoneChanges.filter { context.options.scope.contains($0) }
        guard !pending.isEmpty else { return nil }
        let ownID = Self.ownRecordID
        let store = model.store
        return await CKSyncEngine.RecordZoneChangeBatch(pendingChanges: pending) { recordID in
            guard recordID == ownID else {
                syncEngine.state.remove(pendingRecordZoneChanges: [.saveRecord(recordID)])
                return nil
            }
            return Self.snapshotRecord(id: ownID, store: store)
        }
    }

    private func handleAccountChange(_ change: CKSyncEngine.Event.AccountChange) {
        switch change.changeType {
        case .signIn:
            status = .syncing
            engine?.state.add(pendingDatabaseChanges: [.saveZone(CKRecordZone(zoneID: Self.zoneID))])
            queueUpload()
            let engine = engine
            Task { try? await engine?.fetchChanges() }
        case .switchAccounts:
            // Another Apple ID: forget the old account's sync state but keep the games on this device.
            Self.clearState()
            Self.clearSystemFields()
            restart()
        case .signOut:
            status = .noAccount
        @unknown default:
            break
        }
    }

    private func handleFetched(_ changes: CKSyncEngine.Event.FetchedRecordZoneChanges) async {
        var snapshots: [String] = []
        for modification in changes.modifications {
            let record = modification.record
            // Our own snapshot is merged too: after a reset it is how this device gets its games back.
            if record.recordID == Self.ownRecordID {
                Self.saveSystemFields(of: record)
            }
            guard record.recordType == Self.recordType,
                  let asset = record["payload"] as? CKAsset,
                  let url = asset.fileURL,
                  let data = try? Data(contentsOf: url)
            else { continue }
            snapshots.append(String(decoding: data, as: UTF8.self))
        }
        guard !snapshots.isEmpty else { return }
        let decoded = await Task.detached(priority: .utility) {
            DecodedSnapshots(batch: CloudSnapshotDecoder.shared.decode(jsons: snapshots))
        }.value
        model.dispatch(Actions.shared.applyCloudSnapshots(batch: decoded.batch))
    }

    private func handleSent(_ sent: CKSyncEngine.Event.SentRecordZoneChanges, engine: CKSyncEngine) {
        for record in sent.savedRecords where record.recordID == Self.ownRecordID {
            Self.saveSystemFields(of: record)
        }
        for failure in sent.failedRecordSaves {
            switch failure.error.code {
            case .serverRecordChanged:
                if let serverRecord = failure.error.serverRecord { Self.saveSystemFields(of: serverRecord) }
                queueUpload()
            case .zoneNotFound:
                Self.clearSystemFields()
                engine.state.add(pendingDatabaseChanges: [.saveZone(CKRecordZone(zoneID: Self.zoneID))])
                queueUpload()
            case .unknownItem:
                Self.clearSystemFields()
                queueUpload()
            case .networkFailure, .networkUnavailable, .zoneBusy, .serviceUnavailable,
                 .notAuthenticated, .operationCancelled, .requestRateLimited:
                break // CKSyncEngine retries these on its own.
            default:
                status = .failed
            }
        }
    }
}

/// The decoded Kotlin batch is immutable, so it can be handed from the decoding task to the main actor.
private struct DecodedSnapshots: @unchecked Sendable {
    let batch: CloudSnapshotBatch
}

// MARK: - Local bookkeeping

private extension CloudSyncService {
    nonisolated static func snapshotRecord(id: CKRecord.ID, store: AppStore) -> CKRecord {
        let record = loadSystemFieldsRecord(id: id) ?? CKRecord(recordType: recordType, recordID: id)
        let path = store.writeSnapshotFile(fileName: "snapshot.pinguine")
        record["payload"] = CKAsset(fileURL: URL(fileURLWithPath: path))
        record["schemaVersion"] = 1 as NSNumber
        record["exportedAt"] = Date.now as NSDate
        return record
    }

    nonisolated static var stateURL: URL {
        URL.applicationSupportDirectory.appending(path: "cloudkit-state.json")
    }

    nonisolated static func loadState() -> CKSyncEngine.State.Serialization? {
        guard let data = try? Data(contentsOf: stateURL) else { return nil }
        return try? JSONDecoder().decode(CKSyncEngine.State.Serialization.self, from: data)
    }

    nonisolated static func saveState(_ serialization: CKSyncEngine.State.Serialization) {
        guard let data = try? JSONEncoder().encode(serialization) else { return }
        try? FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
        try? data.write(to: stateURL, options: .atomic)
    }

    nonisolated static func clearState() {
        try? FileManager.default.removeItem(at: stateURL)
    }

    /// The server's change tag for our record, so saves don't run into conflicts.
    nonisolated static func saveSystemFields(of record: CKRecord) {
        let archiver = NSKeyedArchiver(requiringSecureCoding: true)
        record.encodeSystemFields(with: archiver)
        archiver.finishEncoding()
        UserDefaults.standard.set(archiver.encodedData, forKey: Keys.systemFields)
    }

    nonisolated static func loadSystemFieldsRecord(id: CKRecord.ID) -> CKRecord? {
        guard let data = UserDefaults.standard.data(forKey: Keys.systemFields),
              let unarchiver = try? NSKeyedUnarchiver(forReadingFrom: data)
        else { return nil }
        unarchiver.requiresSecureCoding = true
        defer { unarchiver.finishDecoding() }
        guard let record = CKRecord(coder: unarchiver), record.recordID == id else { return nil }
        return record
    }

    nonisolated static func clearSystemFields() {
        UserDefaults.standard.removeObject(forKey: Keys.systemFields)
    }
}
