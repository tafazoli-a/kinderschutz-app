import Foundation
import FamilyControls
import ManagedSettings
import DeviceActivity

/// Gemeinsamer Speicher von App und Monitor-Erweiterung (App Group).
enum Shared {
    static let appGroup = "group.de.familie.kinderschutz"
    static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }
}

/// Ein Zeitlimit für eine Gruppe von Apps, z. B. "Spiele: 30 Minuten".
struct AppLimit: Codable, Identifiable {
    var id = UUID()
    var name: String
    var selection = FamilyActivitySelection()
    var minutes: Int = 30
}

/// Alle Regeln der Eltern.
/// Wichtig: Apple verrät uns nie die echten App-Namen, nur verschlüsselte "Tokens".
/// Deshalb wählen die Eltern Apps immer über Apples eigene Auswahlliste aus.
struct Rules: Codable {
    var dailyLimitMinutes = 0                         // 0 = kein Limit
    var totalSelection = FamilyActivitySelection()    // welche Apps zur Gesamtzeit zählen (am besten "alle")
    var limits: [AppLimit] = []
    var blocked = FamilyActivitySelection()           // immer gesperrt
    var blockedDomains: [String] = []                 // Webseiten als Text, z. B. "tiktok.com"
    var adultFilter = true                            // Apples Filter für Erwachseneninhalte
    var bedtimeOn = false
    var bedtimeStartMinutes = 21 * 60
    var bedtimeEndMinutes = 7 * 60

    static func load() -> Rules {
        guard let data = Shared.defaults.data(forKey: "regeln"),
              let rules = try? JSONDecoder().decode(Rules.self, from: data) else { return Rules() }
        return rules
    }

    func save() {
        if let data = try? JSONEncoder().encode(self) { Shared.defaults.set(data, forKey: "regeln") }
    }
}

extension DeviceActivityName {
    static let daily = Self("taeglich")
    static let bedtime = Self("ruhezeit")
}

extension DeviceActivityEvent.Name {
    static let total = Self("gesamt")
    static func limit(_ id: UUID) -> Self { Self("limit-\(id.uuidString)") }
}

extension ManagedSettingsStore.Name {
    static let base = Self("basis")        // dauerhafte Sperren
    static let limits = Self("limits")     // Zeit aufgebraucht
    static let bedtime = Self("ruhezeit")  // Ruhezeit
}

/// Setzt die Regeln mit Apples Screen-Time-Schnittstelle durch.
enum Enforcer {

    /// Dauerhaft gesperrte Apps und Webseiten.
    static func applyBase(_ r: Rules) {
        let store = ManagedSettingsStore(named: .base)
        store.shield.applications = r.blocked.applicationTokens.isEmpty ? nil : r.blocked.applicationTokens
        store.shield.applicationCategories = r.blocked.categoryTokens.isEmpty ? nil : .specific(r.blocked.categoryTokens)
        store.shield.webDomains = r.blocked.webDomainTokens.isEmpty ? nil : r.blocked.webDomainTokens

        let domains = Set(r.blockedDomains.map { WebDomain(domain: $0) })
        if r.adultFilter {
            store.webContent.blockedByFilter = .auto(domains)      // Erwachsenenfilter + eigene Liste
        } else if !domains.isEmpty {
            store.webContent.blockedByFilter = .specific(domains)  // nur eigene Liste
        } else {
            store.webContent.blockedByFilter = nil
        }
    }

    /// Meldet bei iOS an, wann die Monitor-Erweiterung geweckt werden soll.
    static func schedule(_ r: Rules) throws {
        let center = DeviceActivityCenter()
        center.stopMonitoring()
        ManagedSettingsStore(named: .limits).clearAllSettings()
        ManagedSettingsStore(named: .bedtime).clearAllSettings()

        var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        for l in r.limits where l.minutes > 0 {
            events[.limit(l.id)] = DeviceActivityEvent(
                applications: l.selection.applicationTokens,
                categories: l.selection.categoryTokens,
                webDomains: l.selection.webDomainTokens,
                threshold: DateComponents(minute: l.minutes)
            )
        }
        if r.dailyLimitMinutes > 0 {
            events[.total] = DeviceActivityEvent(
                applications: r.totalSelection.applicationTokens,
                categories: r.totalSelection.categoryTokens,
                webDomains: r.totalSelection.webDomainTokens,
                threshold: DateComponents(minute: r.dailyLimitMinutes)
            )
        }

        let wholeDay = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: true
        )
        try center.startMonitoring(.daily, during: wholeDay, events: events)

        if r.bedtimeOn {
            let bed = DeviceActivitySchedule(
                intervalStart: DateComponents(hour: r.bedtimeStartMinutes / 60, minute: r.bedtimeStartMinutes % 60),
                intervalEnd: DateComponents(hour: r.bedtimeEndMinutes / 60, minute: r.bedtimeEndMinutes % 60),
                repeats: true
            )
            try center.startMonitoring(.bedtime, during: bed)
        }
    }

    /// Alles sperren, was sich sperren lässt (Telefon und Notruf bleiben immer erlaubt).
    static func shieldEverything(_ store: ManagedSettingsStore) {
        store.shield.applicationCategories = .all()
        store.shield.webDomainCategories = .all()
    }
}
