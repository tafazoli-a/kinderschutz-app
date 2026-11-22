# KinderSchutz – eigene Kindersicherungs-App (Grundgerüst)

> Dieser Ordner ist die **Android-App**. Die **iPhone-Version** liegt im Unterordner [`ios/`](ios/README.md).

Stand: 22.11.2026, Version 0.1

## Was die App jetzt schon kann

| Funktion | Wie es technisch geht |
|---|---|
| **Bildschirmzeit pro Tag** begrenzen (z. B. 120 Min) | Android-Funktion „Nutzungszugriff“ (`UsageStatsManager`) misst, wie lange jede App heute offen war |
| **Zeitlimit pro App** (z. B. YouTube 30 Min) | gleiche Messung, pro App |
| **Apps ganz sperren** | Bedienungshilfe-Dienst (`AccessibilityService`) merkt, welche App aufgeht, und zeigt sofort den Sperrbildschirm |
| **Ruhezeit** (z. B. 21:00–07:00) | in der Ruhezeit ist alles gesperrt außer Telefon, Startbildschirm und Tastatur |
| **Webseiten sperren** (beliebig viele) | lokaler Filter (`VpnService`), der nur die DNS-Fragen („Wo ist tiktok.com?“) abfängt. Gesperrte Adressen bekommen „gibt es nicht“. Alles andere geht an *Cloudflare for Families* (1.1.1.3), das zusätzlich Erwachseneninhalte und Schadsoftware blockiert |
| **Schutz vor Löschen** | Geräte-Administrator (`DeviceAdminReceiver`) |
| **Eltern-PIN** | Einstellungen sind nur mit PIN erreichbar |

## Annahmen (bitte prüfen)

- Die Kinder haben **Android und iPhone**. Dieser Teil ist für Android, die iPhone-Version ist eigener Code im Ordner `ios/` (braucht Mac, Apple-Entwicklerkonto und eine Freigabe von Apple).
- **Version 1 ist eine einzige App auf dem Kinderhandy.** Die Eltern stellen alles direkt dort mit PIN ein. Eine eigene Eltern-App auf dem Elternhandy kommt in Stufe 2 (siehe unten), weil dafür ein Server oder Firebase-Konto nötig ist.
- Mindestens Android 8.0.
- Die App wird **direkt installiert** (APK), nicht über den Play Store. Google erlaubt Bedienungshilfe-Dienste im Play Store nur mit strenger Prüfung.

## Architektur

```
Kinderhandy
├── MainActivity            Eltern-Bereich mit PIN (alle Einstellungen auf einer Seite)
├── RulesStore              speichert die Regeln auf dem Handy
├── UsageHelper             misst die Nutzungszeit heute
├── RuleChecker             entscheidet: darf diese App jetzt laufen?
├── AppGuardService         Bedienungshilfe: erkennt App-Wechsel, prüft alle 30 s, sperrt
├── BlockActivity           Sperrbildschirm für das Kind
├── WebFilterVpnService     lokaler DNS-Filter für Webseiten
├── DnsPacket               liest/baut DNS-Pakete
├── AdminReceiver           Geräte-Admin (erschwert Deinstallation)
└── BootReceiver            Webfilter nach Neustart wieder an

Stufe 2 (geplant): Elternhandy ──(Firebase)──> Kinderhandy
```

## So probierst du es aus

1. **Android Studio** installieren (kostenlos): https://developer.android.com/studio
2. In Android Studio **„Open“** wählen und den Ordner `kinderschutz-app` öffnen. Android Studio lädt dann alles Nötige selbst herunter (dauert beim ersten Mal ein paar Minuten).
3. Kinderhandy per USB anschließen, dort vorher **Entwickleroptionen → USB-Debugging** einschalten.
4. Oben auf den grünen **▶ Run**-Knopf drücken. Die App „KinderSchutz“ landet auf dem Handy.
5. In der App: PIN festlegen, dann unter **„1. Einrichtung“** alle vier Punkte auf ✅ bringen.
6. Ab Android 13: Wenn „Bedienungshilfe“ ausgegraut ist, in den Einstellungen bei *Apps → KinderSchutz → ⋮ → Eingeschränkte Einstellungen zulassen* tippen.

## Was ich geprüft habe – und was nicht

- ✅ Der gesamte Kotlin-Code kompiliert fehlerfrei gegen Android 14.
- ✅ Die DNS-Sperre wurde mit einem Test-Paket geprüft (www.tiktok.com → „gibt es nicht“, Prüfsumme stimmt).
- ❌ **Noch nicht auf einem echten Handy getestet**, und das komplette App-Paket (APK) konnte ich in meiner Umgebung nicht bauen. Das passiert beim ersten Öffnen in Android Studio.

## Ehrliche Grenzen von Version 1

- Ein geschicktes Kind kann in den **Android-Einstellungen** die Bedienungshilfe oder den Webfilter abschalten. Gegenmittel in Stufe 2: Einstellungen-App sperren, oder die App als **„Device Owner“** einrichten (dann ist sie fest im System, muss aber auf einem frisch zurückgesetzten Handy eingerichtet werden).
- **Tipp jetzt schon:** In den Android-Einstellungen unter *Netzwerk → VPN → KinderSchutz* „**Durchgehend aktives VPN**“ und „**Verbindungen ohne VPN blockieren**“ einschalten.
- Wenn auf dem Handy **„Privates DNS“** fest auf einen Anbieter gestellt ist, umgeht das den Webfilter. Auf „Automatisch“ oder „Aus“ stellen.
- Die Nutzungszeit wird pro Tag ab Mitternacht gezählt. Kleine Abweichungen zur Android-Anzeige „Digital Wellbeing“ sind normal.

## Nächste Schritte (Vorschlag)

1. Auf dem echten Kinderhandy testen und Fehler beheben.
2. „Immer erlaubt“-Liste (z. B. WhatsApp mit Eltern, Schul-App) auch während der Ruhezeit.
3. Einstellungen-App sperren / Schutz gegen Abschalten.
4. **Stufe 2: Eltern-App** auf dem Elternhandy, Regeln aus der Ferne ändern, Bericht über Nutzungszeit, „+15 Minuten“-Wunsch vom Kind. Braucht ein Firebase-Konto (kostenlos), das ich erst nach deinem OK anlege.
5. Schöneres Aussehen.
