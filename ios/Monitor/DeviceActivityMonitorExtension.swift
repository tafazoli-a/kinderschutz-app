import DeviceActivity
import ManagedSettings
import Foundation

/// Wird von iOS im Hintergrund geweckt: bei Tagesbeginn, bei Ruhezeit und wenn ein Zeitlimit erreicht ist.
class DeviceActivityMonitorExtension: DeviceActivityMonitor {

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        switch activity {
        case .daily:
            ManagedSettingsStore(named: .limits).clearAllSettings()     // neuer Tag, neue Zeit
        case .bedtime:
            Enforcer.shieldEverything(ManagedSettingsStore(named: .bedtime))
        default:
            break
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        switch activity {
        case .daily:
            ManagedSettingsStore(named: .limits).clearAllSettings()
        case .bedtime:
            ManagedSettingsStore(named: .bedtime).clearAllSettings()
        default:
            break
        }
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        let store = ManagedSettingsStore(named: .limits)

        if event == .total {
            Enforcer.shieldEverything(store)
            return
        }
        let rules = Rules.load()
        guard let limit = rules.limits.first(where: { DeviceActivityEvent.Name.limit($0.id) == event }) else { return }

        // Zu den bereits gesperrten Apps hinzufügen
        let apps = (store.shield.applications ?? []).union(limit.selection.applicationTokens)
        store.shield.applications = apps.isEmpty ? nil : apps
        if !limit.selection.categoryTokens.isEmpty {
            var cats = limit.selection.categoryTokens
            if case let .specific(existing, _)? = store.shield.applicationCategories { cats.formUnion(existing) }
            store.shield.applicationCategories = .specific(cats)
        }
        let webs = (store.shield.webDomains ?? []).union(limit.selection.webDomainTokens)
        store.shield.webDomains = webs.isEmpty ? nil : webs
    }
}
