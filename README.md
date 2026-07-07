# Snapfacture

**Application de facturation Android minimaliste, 100 % locale, pour artisans et indépendants** (France / États-Unis).

Pas de cloud, pas d'abonnement, pas de compte à créer. Tes données restent sur ton téléphone.

| Facture B2C | Facture B2B | Avoir |
|---|---|---|
| ![Facture B2C](docs/screenshots/invoice-b2c.jpg) | ![Facture B2B](docs/screenshots/invoice-b2b.jpg) | ![Avoir](docs/screenshots/credit-note.jpg) |

*PDF générés à partir des données démo « Plomberie Saadi » fournies dans [docs/demo/](docs/demo/).*

---

## Pour qui ?

- Auto-entrepreneur, micro-entrepreneur, artisan, indépendant
- 1 à 3 personnes maximum dans l'entreprise
- Tu encaisses tes clients **au comptant** (espèces, CB, virement instantané)
- Tu veux **facturer en quelques taps**, pas configurer un ERP

## Fonctionnalités

- Facturation en quelques taps, optimisée pour usage au comptoir ou à domicile (gros boutons, gants OK)
- **Profils pays France et États-Unis** : devise (€ / $), format de date (jj/mm/aaaa ou mm/jj/aaaa), libellé de taxe (TVA ou Sales Tax), mentions légales et anti-fraude appliqués automatiquement selon le pays renseigné dans la fiche entreprise
- Numérotation chronologique sans rupture (conforme art. 242 nonies A CGI) — France uniquement
- Chaîne anti-fraude SHA-256 (conforme loi anti-fraude TVA 2018) — France uniquement
- Mode auto-entrepreneur (mention « TVA non applicable, art. 293 B du CGI »)
- Taux de sales tax par défaut configurable (États-Unis)
- Mentions B2B (SIRET, pénalités de retard, indemnité 40 €) quand le client est un pro — France uniquement
- Avoir (note de crédit) en 1 tap depuis une facture
- **Devis** avec numérotation propre et validité 30 jours, convertibles en facture en 1 tap (lignes et prix figés)
- Ligne libre au panier pour facturer une prestation hors catalogue
- Génération PDF locale + partage par email/WhatsApp/AirDrop
- Verrouillage biométrique optionnel
- Sauvegarde manuelle ou automatique vers un dossier de ton choix (Drive, local…)
- Restauration depuis un fichier `.db`
- Tableau de bord (CA mensuel/annuel, TVA collectée, top produits, top clients)
- Export CSV pour ton expert-comptable
- **Export comptable FEC** (Fichier des Écritures Comptables, importable tel quel par un expert-comptable) — France uniquement
- **Récap TVA / sales tax par taux et par trimestre** : les chiffres à recopier dans la déclaration, avoirs déduits
- Vérification d'intégrité de la chaîne anti-fraude en 1 tap (Réglages) — France uniquement
- Disponible en français et en anglais (suit la langue du téléphone)

## Installation

1. Va sur la page **[Releases](../../releases)** du dépôt
2. Télécharge le fichier `.apk` le plus récent
3. Ouvre-le sur ton téléphone Android (≥ Android 8.0)
4. Si Android refuse l'installation, va dans **Réglages → Sécurité → Autoriser les sources inconnues** pour l'application via laquelle tu as téléchargé (Chrome, par exemple)
5. Ouvre l'app, remplis le formulaire d'accueil (nom, pays, gérant), c'est parti

## Données & vie privée

- **Tout est stocké localement** sur ton téléphone, dans l'espace privé de l'app (protégé par le chiffrement du disque d'Android et inaccessible aux autres apps). Attention : le fichier de sauvegarde que tu exportes vers Drive/OneDrive n'est pas chiffré par Snapfacture — protège ton compte cloud en conséquence
- **Aucune donnée n'est envoyée** vers Internet, jamais
- **Aucun pistage**, aucune analytique, aucune télémétrie
- Si tu veux sauvegarder, **c'est toi qui choisis où** (Google Drive, stockage local, etc.) via le sélecteur de dossier

Détails complets : [PRIVACY.md](PRIVACY.md).

## Compiler depuis les sources

```bash
git clone https://github.com/karim-abbes/snapfacture-android.git
cd snapfacture-android
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
- **Built-in profiles for France and the United States**: currency (€ / $), date format (dd/mm/yyyy or mm/dd/yyyy), tax label (VAT or Sales Tax), legal mentions and anti-fraud features are applied automatically based on the country set in the business profile
- Gapless chronological numbering (French anti-fraud compliant) — France only
- SHA-256 audit chain (French anti-fraud compliant) — France only
- VAT-free regime support (French auto-entrepreneur)
- Configurable default sales tax rate (United States)
- B2B mentions (SIRET, late-payment penalties, 40 € recovery indemnity) when the customer is a business — France only
- 1-tap credit notes
- **Quotes** with their own numbering and 30-day validity, convertible to an invoice in one tap (frozen lines and prices)
- Custom cart line to invoice one-off work outside the catalog
- Local PDF generation + share via email / messaging / AirDrop
- Optional biometric lock
- Manual or automatic backup to a folder of your choice (Drive, local…)
- Restore from a `.db` file
- Dashboard (monthly/yearly revenue, VAT collected, top products, top customers)
- CSV export for your accountant
- **FEC accounting export** (the normalized French statutory file accountants import directly) — France only
- **Per-rate VAT / sales tax summary by quarter**: the exact figures a filing asks for, credit notes deducted
- One-tap anti-fraud chain integrity check (Settings) — France only
- Available in French and English (follows the phone language)

## Install

1. Go to the **[Releases](../../releases)** page
2. Download the latest `.apk`
3. Open it on your Android phone (Android 8.0+)
4. If Android refuses, enable **Settings → Security → Allow unknown sources** for the app you downloaded it from
5. Open the app, fill in the welcome form, you are done

## Data & privacy

- **Everything is stored locally** on your phone, in the app's private storage (protected by Android's disk encryption and inaccessible to other apps). Note: the backup file you export to Drive/OneDrive is not encrypted by Snapfacture — secure your cloud account accordingly
- **No data ever leaves the device**
- **No tracking, no analytics, no telemetry**
- Backups go where **you** decide (Google Drive folder, local storage, …)

Full details: [PRIVACY.md](PRIVACY.md).

## Build from source

```bash
git clone https://github.com/karim-abbes/snapfacture-android.git
cd snapfacture-android
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

---

## Roadmap

Voir [ROADMAP.md](ROADMAP.md) — what's shipped, what's planned, and what is intentionally out of scope.

## License

MIT — see [LICENSE](LICENSE).
