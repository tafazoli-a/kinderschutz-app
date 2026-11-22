# KinderSchutz für iPhone (Grundgerüst)

Stand: 22.11.2026, Version 0.1

## Was diese Version kann

Gleiche Funktionen wie die Android-App, aber über **Apples eigene Screen-Time-Schnittstelle**:

| Funktion | Apple-Baustein |
|---|---|
| Bildschirmzeit pro Tag | `DeviceActivity`: iOS weckt die Monitor-Erweiterung, wenn die Zeit erreicht ist |
| Zeitlimit für einzelne Apps oder Gruppen (z. B. „Spiele: 30 Min“) | `DeviceActivityEvent` pro Limit |
| Apps und Webseiten immer sperren | `ManagedSettings` (Apple zeigt dann seinen Sperrbildschirm) |
| Viele Webseiten als Text sperren, plus Erwachsenenfilter | `webContent.blockedByFilter` |
| Ruhezeit | eigener Zeitplan, sperrt alles außer Telefon |
| Eltern-PIN in der App | eigene PIN |
| Schutz vor Löschen | bei Freigabe als **Kind-Gerät** (`.child`) kann das Kind die App nicht löschen |

**Wichtig:** Apple zeigt uns nie die echten App-Namen. Die Eltern wählen Apps immer über Apples Auswahlliste aus. Die Nutzungszeit „anzeigen“ geht nur über eine weitere Erweiterung (kommt später).

## Dateien

```
ios/
├── project.yml                 Bauplan für das Xcode-Projekt (XcodeGen)
├── Shared/Rules.swift          Regeln speichern + durchsetzen (App und Erweiterung teilen das)
├── KinderSchutz/               die App mit Eltern-PIN und Einstellungen
│   ├── KinderSchutzApp.swift
│   ├── PinGateView.swift
│   └── ParentView.swift
└── Monitor/                    Hintergrund-Erweiterung, sperrt bei Zeitablauf und Ruhezeit
    └── DeviceActivityMonitorExtension.swift
```

## Was du dafür brauchst

1. Einen **Mac** mit **Xcode** (kostenlos im Mac App Store).
2. Ein **Apple-Entwicklerkonto** (99 € pro Jahr): https://developer.apple.com/programs/
3. Die Freigabe **„Family Controls“** von Apple. Zum Ausprobieren auf eigenen Geräten reicht die Entwicklungs-Variante. Für eine Verteilung (TestFlight / App Store) musst du sie hier beantragen: https://developer.apple.com/contact/request/family-controls-distribution
4. Das Kinder-iPhone muss in deiner **Familienfreigabe** als Kind eingetragen sein.

## So probierst du es aus

1. Auf dem Mac im Terminal: `brew install xcodegen`, dann in den Ordner `ios` wechseln und `xcodegen` eingeben. Es entsteht `KinderSchutz.xcodeproj`.
2. Das Projekt in Xcode öffnen. Bei beiden Zielen (KinderSchutz und Monitor) unter *Signing & Capabilities* dein Team auswählen.
3. Kinder-iPhone per Kabel anschließen und auf ▶ drücken.
4. In der App: PIN festlegen, „Erlauben“ tippen und mit **deiner** Apple-ID (Eltern) bestätigen.
5. Regeln einstellen und **„Regeln anwenden“** tippen.

Zum Testen auf deinem eigenen iPhone ändere in `ParentView.swift` `.child` zu `.individual`.

## Was geprüft ist – und was nicht

- ❌ Dieser Swift-Code konnte **noch nicht übersetzt oder getestet** werden. Dafür braucht man einen Mac. Rechne beim ersten Bauen mit kleinen Fehlern, die ich dann behebe.

## Bekannte Grenzen

- Die Ruhezeit muss mindestens 15 Minuten lang sein (Vorgabe von Apple).
- Apples Screen-Time-Schnittstelle ist manchmal etwas verzögert (Limits greifen teils erst nach ein paar Minuten).
