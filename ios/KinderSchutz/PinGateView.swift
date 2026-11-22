import SwiftUI
import CryptoKit

/// Eltern-PIN, damit das Kind die Regeln in der App nicht selbst ändert.
enum ParentPin {
    private static let key = "pin_hash"
    private static func hash(_ pin: String) -> String {
        SHA256.hash(data: Data(("kinderschutz-" + pin).utf8)).map { String(format: "%02x", $0) }.joined()
    }
    static var isSet: Bool { Shared.defaults.string(forKey: key) != nil }
    static func set(_ pin: String) { Shared.defaults.set(hash(pin), forKey: key) }
    static func check(_ pin: String) -> Bool { Shared.defaults.string(forKey: key) == hash(pin) }
}

struct PinGateView: View {
    @State private var unlocked = false
    @State private var pin = ""
    @State private var message = ""
    @Environment(\.scenePhase) private var phase

    var body: some View {
        Group {
            if unlocked {
                ParentView(onLock: { unlocked = false })
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "lock.shield").font(.system(size: 56))
                    Text(ParentPin.isSet ? "Eltern-Bereich" : "Eltern-PIN festlegen").font(.title2).bold()
                    Text(ParentPin.isSet ? "Bitte Eltern-PIN eingeben."
                                         : "Mindestens 4 Ziffern. Dein Kind darf sie nicht kennen.")
                        .multilineTextAlignment(.center)
                    SecureField("PIN", text: $pin)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 200)
                    Button(ParentPin.isSet ? "Entsperren" : "PIN speichern", action: submit)
                        .buttonStyle(.borderedProminent)
                    Text(message).foregroundColor(.red)
                }
                .padding()
            }
        }
        .onChange(of: phase) { newPhase in
            if newPhase == .background { unlocked = false; pin = "" }   // beim Verlassen wieder sperren
        }
    }

    private func submit() {
        if !ParentPin.isSet {
            guard pin.count >= 4 else { message = "Mindestens 4 Ziffern"; return }
            ParentPin.set(pin)
            unlocked = true
        } else if ParentPin.check(pin) {
            unlocked = true
        } else {
            message = "Falsche PIN"
        }
        pin = ""
    }
}
