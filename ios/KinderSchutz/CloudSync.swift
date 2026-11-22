import Foundation

/// Verbindung zur Eltern-Verwaltung (Firebase). Werte aus der Firebase-Konsole.
/// Leer = Verwaltung aus, die App funktioniert dann nur mit den Einstellungen am iPhone.
enum CloudConfig {
    static let apiKey = ""
    static let projectId = ""
}

/// Abgleich mit der Eltern-Verwaltung über die Firebase-REST-Schnittstelle.
/// Hinweis: iOS lässt Apps nicht dauerhaft im Hintergrund laufen. Neue Regeln
/// kommen an, wenn die App geöffnet wird oder iOS sie kurz im Hintergrund weckt.
@MainActor
final class CloudSync: ObservableObject {
    static let shared = CloudSync()

    @Published var deviceName: String?
    @Published var lastSync: Date?
    @Published var lastError: String?

    private let d = Shared.defaults
    private var docs: String { "https://firestore.googleapis.com/v1/projects/\(CloudConfig.projectId)/databases/(default)/documents" }

    var isConfigured: Bool { !CloudConfig.apiKey.isEmpty && !CloudConfig.projectId.isEmpty }
    var isPaired: Bool { d.string(forKey: "cloud.deviceId") != nil }

    private init() {
        deviceName = d.string(forKey: "cloud.deviceName")
        lastSync = d.object(forKey: "cloud.lastSync") as? Date
    }

    // MARK: HTTP
    struct HTTPError: Error { let code: Int }

    private func request(_ method: String, _ url: String, json: [String: Any]? = nil,
                         form: String? = nil, token: String? = nil) async throws -> [String: Any] {
        guard let u = URL(string: url) else { throw HTTPError(code: 0) }
        var req = URLRequest(url: u)
        req.httpMethod = method
        req.timeoutInterval = 15
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let json {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: json)
        } else if let form {
            req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            req.httpBody = form.data(using: .utf8)
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else { throw HTTPError(code: code) }
        return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    private func field(_ doc: [String: Any], _ name: String) -> String? {
        ((doc["fields"] as? [String: Any])?[name] as? [String: Any])?["stringValue"] as? String
    }

    // MARK: Anmeldung (anonym)
    private func token() async throws -> String {
        if let t = d.string(forKey: "cloud.idToken"),
           let exp = d.object(forKey: "cloud.tokenExp") as? Date, exp > Date() { return t }

        let id: String, refresh: String, expires: Double, uid: String
        if let r = d.string(forKey: "cloud.refreshToken") {
            let enc = r.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? r
            let res = try await request("POST", "https://securetoken.googleapis.com/v1/token?key=\(CloudConfig.apiKey)",
                                        form: "grant_type=refresh_token&refresh_token=\(enc)")
            id = res["id_token"] as? String ?? ""
            refresh = res["refresh_token"] as? String ?? r
            expires = Double(res["expires_in"] as? String ?? "3600") ?? 3600
            uid = res["user_id"] as? String ?? ""
        } else {
            let res = try await request("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=\(CloudConfig.apiKey)",
                                        json: ["returnSecureToken": true])
            id = res["idToken"] as? String ?? ""
            refresh = res["refreshToken"] as? String ?? ""
            expires = Double(res["expiresIn"] as? String ?? "3600") ?? 3600
            uid = res["localId"] as? String ?? ""
        }
        d.set(id, forKey: "cloud.idToken")
        d.set(refresh, forKey: "cloud.refreshToken")
        d.set(uid, forKey: "cloud.uid")
        d.set(Date().addingTimeInterval(expires - 120), forKey: "cloud.tokenExp")
        return id
    }

    // MARK: Verbinden mit Code
    /// Gibt nil zurück, wenn es geklappt hat, sonst eine Fehlermeldung.
    func pair(code: String) async -> String? {
        do {
            let t = try await token()
            guard let uid = d.string(forKey: "cloud.uid") else { return "Anmeldung fehlgeschlagen." }
            let pc = try await request("GET", "\(docs)/pairCodes/\(code)", token: t)
            guard let family = field(pc, "familyId"), let device = field(pc, "deviceId") else { return "Code ungültig." }
            _ = try await request("PATCH", "\(docs)/pairCodes/\(code)?updateMask.fieldPaths=claimedBy",
                                  json: ["fields": ["claimedBy": ["stringValue": uid]]], token: t)
            _ = try await request("PATCH", "\(docs)/families/\(family)/devices/\(device)?updateMask.fieldPaths=deviceUid",
                                  json: ["fields": ["deviceUid": ["stringValue": uid]]], token: t)
            d.set(family, forKey: "cloud.familyId")
            d.set(device, forKey: "cloud.deviceId")
            await sync()
            return nil
        } catch let e as HTTPError {
            switch e.code {
            case 404: return "Diesen Code gibt es nicht."
            case 403: return "Code abgelaufen oder schon benutzt."
            default: return "Verbinden fehlgeschlagen (\(e.code))."
            }
        } catch {
            return "Keine Verbindung zum Internet?"
        }
    }

    // MARK: Abgleich
    /// Holt die Regeln und meldet "zuletzt gesehen". Nutzungszeiten gibt iOS nicht an Apps heraus.
    func sync() async {
        guard isConfigured, let family = d.string(forKey: "cloud.familyId"),
              let device = d.string(forKey: "cloud.deviceId") else { return }
        let path = "\(docs)/families/\(family)/devices/\(device)"
        do {
            let t = try await token()
            let doc = try await request("GET", path, token: t)
            if let name = field(doc, "name") { deviceName = name; d.set(name, forKey: "cloud.deviceName") }
            if let json = field(doc, "rulesJson"), let data = json.data(using: .utf8),
               let remote = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                apply(remote)
            }
            let now = ISO8601DateFormatter().string(from: Date())
            _ = try await request("PATCH", "\(path)?updateMask.fieldPaths=lastSeen",
                                  json: ["fields": ["lastSeen": ["timestampValue": now]]], token: t)
            lastSync = Date(); d.set(lastSync, forKey: "cloud.lastSync")
            lastError = nil
        } catch let e as HTTPError where e.code == 403 || e.code == 404 {
            unpair()   // in der Verwaltung entfernt
            lastError = "Dieses iPhone wurde in der Verwaltung entfernt."
        } catch {
            lastError = "Abgleich fehlgeschlagen. Kein Internet?"
        }
    }

    func unpair() {
        ["cloud.familyId", "cloud.deviceId", "cloud.deviceName"].forEach { d.removeObject(forKey: $0) }
        deviceName = nil
    }

    /// Zeiten und Webseiten kommen aus der Verwaltung. Die App-Auswahl bleibt auf dem iPhone (Vorgabe von Apple).
    private func apply(_ r: [String: Any]) {
        var rules = Rules.load()
        rules.dailyLimitMinutes = r["dailyLimitMinutes"] as? Int ?? 0
        let start = r["bedtimeStart"] as? Int ?? -1, end = r["bedtimeEnd"] as? Int ?? -1
        rules.bedtimeOn = start >= 0 && end >= 0 && start != end
        if rules.bedtimeOn { rules.bedtimeStartMinutes = start; rules.bedtimeEndMinutes = end }
        rules.blockedDomains = r["blockedDomains"] as? [String] ?? []
        rules.save()
        Enforcer.applyBase(rules)
        try? Enforcer.schedule(rules)
    }
}
