import SwiftUI

@main
struct KinderSchutzApp: App {
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup { PinGateView() }
            .onChange(of: phase) { p in
                // Beim Öffnen der App neue Regeln aus der Verwaltung holen
                if p == .active { Task { await CloudSync.shared.sync() } }
            }
    }
}
