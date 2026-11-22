import SwiftUI
import FamilyControls

/// Alle Einstellungen der Eltern auf einer Seite.
struct ParentView: View {
    var onLock: () -> Void

    @ObservedObject private var auth = AuthorizationCenter.shared
    @State private var rules = Rules.load()
    @State private var domainText = Rules.load().blockedDomains.joined(separator: "\n")
    @State private var pickTotal = false
    @State private var pickBlocked = false
    @State private var status = ""

    var body: some View {
        NavigationView {
            Form {
                Section("1. Einrichtung") {
                    HStack {
                        Text(auth.authorizationStatus == .approved ? "✅ Bildschirmzeit erlaubt" : "❌ Bildschirmzeit nicht erlaubt")
                        Spacer()
                        if auth.authorizationStatus != .approved {
                            Button("Erlauben") { Task { await requestAuth() } }
                        }
                    }
                    Text("Auf dem Kinder-iPhone mit Familienfreigabe bestätigen die Eltern mit ihrer Apple-ID. Danach kann das Kind die App nicht mehr löschen.")
                        .font(.footnote).foregroundColor(.secondary)
                }

                Section("2. Bildschirmzeit pro Tag") {
                    Stepper(rules.dailyLimitMinutes == 0 ? "Kein Limit" : "\(rules.dailyLimitMinutes) Minuten",
                            value: $rules.dailyLimitMinutes, in: 0...600, step: 15)
                    Button("Welche Apps zählen? (am besten alle)") { pickTotal = true }
                        .familyActivityPicker(isPresented: $pickTotal, selection: $rules.totalSelection)
                }

                Section("3. Zeitlimits für Apps") {
                    ForEach($rules.limits) { $limit in LimitRow(limit: $limit) }
                        .onDelete { rules.limits.remove(atOffsets: $0) }
                    Button("+ Limit hinzufügen") {
                        rules.limits.append(AppLimit(name: "Limit \(rules.limits.count + 1)"))
                    }
                }

                Section("4. Immer gesperrt") {
                    Button("Apps und Webseiten auswählen (\(count(rules.blocked)))") { pickBlocked = true }
                        .familyActivityPicker(isPresented: $pickBlocked, selection: $rules.blocked)
                }

                Section("5. Webseiten sperren") {
                    Toggle("Erwachseneninhalte filtern", isOn: $rules.adultFilter)
                    Text("Eine Adresse pro Zeile, z. B. tiktok.com").font(.footnote).foregroundColor(.secondary)
                    TextEditor(text: $domainText)
                        .frame(minHeight: 100)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }

                Section("6. Ruhezeit") {
                    Toggle("Ruhezeit an", isOn: $rules.bedtimeOn)
                    if rules.bedtimeOn {
                        DatePicker("Von", selection: timeBinding(\.bedtimeStartMinutes), displayedComponents: .hourAndMinute)
                        DatePicker("Bis", selection: timeBinding(\.bedtimeEndMinutes), displayedComponents: .hourAndMinute)
                    }
                }

                Section {
                    Button("Regeln anwenden", action: apply).bold()
                    if !status.isEmpty { Text(status).font(.footnote) }
                    Button("Eltern-Bereich sperren", role: .destructive, action: onLock)
                }
            }
            .navigationTitle("KinderSchutz")
        }
    }

    private func requestAuth() async {
        do {
            // .child = Kinder-iPhone in der Familienfreigabe. Zum Testen auf dem eigenen iPhone: .individual
            try await AuthorizationCenter.shared.requestAuthorization(for: .child)
        } catch {
            status = "Erlaubnis fehlgeschlagen: \(error.localizedDescription)"
        }
    }

    private func apply() {
        rules.blockedDomains = domainText
            .split(whereSeparator: \.isNewline)
            .map { normalize(String($0)) }
            .filter { $0.contains(".") }
        rules.save()
        Enforcer.applyBase(rules)
        do {
            try Enforcer.schedule(rules)
            status = "✅ Gespeichert und aktiv"
        } catch {
            status = "Fehler beim Planen: \(error.localizedDescription)"
        }
    }

    private func normalize(_ s: String) -> String {
        var d = s.trimmingCharacters(in: .whitespaces).lowercased()
        for p in ["https://", "http://", "www."] where d.hasPrefix(p) { d.removeFirst(p.count) }
        return String(d.split(separator: "/").first ?? "")
    }

    private func count(_ s: FamilyActivitySelection) -> Int {
        s.applicationTokens.count + s.categoryTokens.count + s.webDomainTokens.count
    }

    private func timeBinding(_ key: WritableKeyPath<Rules, Int>) -> Binding<Date> {
        Binding(
            get: {
                let m = rules[keyPath: key]
                return Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: Date()) ?? Date()
            },
            set: {
                let c = Calendar.current.dateComponents([.hour, .minute], from: $0)
                rules[keyPath: key] = (c.hour ?? 0) * 60 + (c.minute ?? 0)
            }
        )
    }
}

struct LimitRow: View {
    @Binding var limit: AppLimit
    @State private var picking = false

    var body: some View {
        VStack(alignment: .leading) {
            TextField("Name, z. B. Spiele", text: $limit.name)
            Stepper("\(limit.minutes) Minuten pro Tag", value: $limit.minutes, in: 15...600, step: 15)
            Button("Apps auswählen (\(limit.selection.applicationTokens.count + limit.selection.categoryTokens.count))") { picking = true }
                .familyActivityPicker(isPresented: $picking, selection: $limit.selection)
        }
    }
}
