# Snapfacture

**Application de facturation Android minimaliste, 100 % locale, pour artisans et indépendants** (France / États-Unis).

Pas de cloud, pas d'abonnement, pas de compte à créer. Tes données restent sur ton téléphone.

<!-- Captures d'écran : déposer les images dans docs/screenshots/ puis dé-commenter ci-dessous -->
<!--
| Liste | Création | Détail |
|---|---|---|
| ![Liste des factures](docs/screenshots/list.png) | ![Création](docs/screenshots/create.png) | ![Détail](docs/screenshots/detail.png) |
-->

---

## Pour qui ?

- Auto-entrepreneur, micro-entrepreneur, artisan, indépendant
- 1 à 3 personnes maximum dans l'entreprise
- Tu encaisses tes clients **au comptant** (espèces, CB, virement instantané)
- Tu veux **facturer en quelques taps**, pas configurer un ERP

## Fonctionnalités

- Facturation en quelques taps, optimisée pour usage au comptoir ou à domicile (gros boutons, gants OK)
- Numérotation chronologique sans rupture (conforme art. 242 nonies A CGI)
- Chaîne anti-fraude SHA-256 (conforme loi anti-fraude TVA 2018)
- Mode auto-entrepreneur (mention « TVA non applicable, art. 293 B du CGI »)
- Mentions B2B (SIRET, pénalités de retard, indemnité 40 €) quand le client est un pro
- Avoir (note de crédit) en 1 tap depuis une facture
- Génération PDF locale + partage par email/WhatsApp/AirDrop
- Verrouillage biométrique optionnel
- Sauvegarde manuelle ou automatique vers un dossier de ton choix (Drive, local…)
- Restauration depuis un fichier `.db`
- Tableau de bord (CA mensuel/annuel, TVA collectée, top produits, top clients)
- Export CSV pour ton expert-comptable
- Disponible en français et en anglais (suit la langue du téléphone)

## Installation

1. Va sur la page **[Releases](../../releases)** du dépôt
2. Télécharge le fichier `.apk` le plus récent
3. Ouvre-le sur ton téléphone Android (≥ Android 10)
4. Si Android refuse l'installation, va dans **Réglages → Sécurité → Autoriser les sources inconnues** pour l'application via laquelle tu as téléchargé (Chrome, par exemple)
5. Ouvre l'app, remplis le formulaire d'accueil (nom, pays, gérant), c'est parti

## Données & vie privée

- **Tout est stocké localement** dans la base de données chiffrée du téléphone (Android Keystore)
- **Aucune donnée n'est envoyée** vers Internet, jamais
- **Aucun pistage**, aucune analytique, aucune télémétrie
- Si tu veux sauvegarder, **c'est toi qui choisis où** (Google Drive, stockage local, etc.) via le sélecteur de dossier

Détails complets : [PRIVACY.md](PRIVACY.md).

## Compiler depuis les sources

```bash
git clone https://github.com/karimlancien/facturation-android.git
cd facturation-android
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/`.

---

# Snapfacture (English)

**Minimalist, 100 % local Android invoicing app for solo entrepreneurs** (France / United States).

No cloud, no subscription, no account. Your data stays on your phone.

## Who is it for?

- Sole proprietor, freelancer, tradesperson
- 1 to 3 people in the business
- You get paid **on the spot** (cash, card, instant transfer)
- You want to **invoice in a few taps**, not configure an ERP

## Features

- A few-tap invoicing flow, optimised for counter or on-site use
- Gapless chronological numbering (French anti-fraud compliant)
- SHA-256 audit chain (French anti-fraud compliant)
- VAT-free regime support (French auto-entrepreneur)
- B2B mentions (SIRET, late-payment penalties, 40 € recovery indemnity) when the customer is a business
- 1-tap credit notes
- Local PDF generation + share via email / messaging / AirDrop
- Optional biometric lock
- Manual or automatic backup to a folder of your choice (Drive, local…)
- Restore from a `.db` file
- Dashboard (monthly/yearly revenue, VAT collected, top products, top customers)
- CSV export for your accountant
- Available in French and English (follows the phone language)

## Install

1. Go to the **[Releases](../../releases)** page
2. Download the latest `.apk`
3. Open it on your Android phone (Android 10+)
4. If Android refuses, enable **Settings → Security → Allow unknown sources** for the app you downloaded it from
5. Open the app, fill in the welcome form, you are done

## Data & privacy

- **Everything is stored locally** in the phone's encrypted database (Android Keystore)
- **No data ever leaves the device**
- **No tracking, no analytics, no telemetry**
- Backups go where **you** decide (Google Drive folder, local storage, …)

Full details: [PRIVACY.md](PRIVACY.md).

## Build from source

```bash
git clone https://github.com/karimlancien/facturation-android.git
cd facturation-android
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

---

## Roadmap

Voir [ROADMAP.md](ROADMAP.md) — what's shipped, what's planned, and what is intentionally out of scope.

## License

MIT — see [LICENSE](LICENSE).
