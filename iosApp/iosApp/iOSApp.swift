import SwiftUI

@main
struct iOSApp: App {
    @State private var model = AppModel.live()
    @State private var router = Router()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(router)
                .onOpenURL { url in BackupFile.importBackup(from: url, into: model) }
        }
    }
}
