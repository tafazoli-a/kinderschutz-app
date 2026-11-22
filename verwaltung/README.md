# Eltern-Verwaltung

Eine Web-App (läuft im Browser auf Handy, iPhone und PC), mit der die Eltern alle Kinderhandys verwalten.

## Wie es zusammenhängt

```
Eltern (Browser)  ──Google-Anmeldung──►  Firebase (Datenbank Firestore)  ◄──anonym + Code──  Kinderhandy
   Regeln ändern                         families/{Eltern}/devices/{Handy}                 Regeln holen, Nutzung melden
```

- Jede Familie ist das Google-Konto der Eltern. Andere Familien sehen nichts davon (`firestore.rules`).
- Ein Kinderhandy wird mit einem **6-stelligen Code** verbunden (1 Stunde gültig, nur einmal nutzbar).
- Android holt die Regeln alle 5 Minuten ab und meldet die Nutzungszeit und die App-Liste.
- iPhone: Zeiten und Webseiten kommen aus der Verwaltung. Apps wählt man auf dem iPhone selbst aus, weil Apple die App-Namen nicht herausgibt.

## Einrichten (einmalig)

1. Firebase-Projekt anlegen, **Google** und **Anonym** als Anmeldung einschalten, Firestore in `europe-west3` anlegen.
2. Die Web-Werte in `verwaltung/firebase-config.js` und `apiKey`/`projectId` in `app/.../CloudConfig.kt` eintragen.
3. Sicherheitsregeln: Inhalt von `firestore.rules` in der Firebase-Konsole unter *Firestore → Regeln* einfügen und veröffentlichen.
4. Online stellen: entweder automatisch über GitHub (Secret `FIREBASE_SERVICE_ACCOUNT` anlegen) oder per `npx firebase-tools deploy`.
