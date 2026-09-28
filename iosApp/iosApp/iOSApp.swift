import SwiftUI

@main
struct iOSApp: App {
    @State private var model: AppModel
    @State private var router = Router()
    @State private var cloud: CloudSyncService
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let model = AppModel.live()
        _model = State(initialValue: model)
        _cloud = State(initialValue: CloudSyncService(model: model))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(router)
                .environment(cloud)
                .onOpenURL { url in BackupFile.importBackup(from: url, into: model) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { cloud.appBecameActive() }
        }
    }
}
