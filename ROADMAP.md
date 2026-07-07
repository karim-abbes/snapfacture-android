# Roadmap

État vivant de ce qui est livré, de ce qui est prévu, et de ce qui ne sera **pas** fait — pour cadrer les contributions et éviter les revirements.

Statuts : ✅ livré · 🔨 prévu · 🔮 envisagé · ❌ hors-scope

---

## ✅ Livré

### Cœur métier

- Création de facture en quelques taps, optimisée pour usage au comptoir / sur la route
- Avoirs (notes de crédit) en 1 tap depuis une facture
- Numérotation chronologique sans rupture (conforme art. 242 nonies A CGI)
- Chaîne anti-fraude SHA-256 (loi anti-fraude TVA 2018) — active uniquement sur profil France
- Snapshot des informations entreprise par facture (immuabilité légale)
- Profils pays France / États-Unis (devise, format de date, libellé taxe, mentions B2B, anti-fraude) — choix invisible après l'onboarding
- Mentions B2B automatiques sur PDF (SIRET client, pénalités L441-10, indemnité D441-5) — France uniquement
- Mode franchise TVA (auto-entrepreneur)
- Taux de sales tax par défaut configurable (US)
- Catalogue produits avec note de service optionnelle
- Onboarding bloquant au premier lancement (dialogue centré, 3 champs)

### Distribution / stockage

- Génération PDF locale + partage par email / message / AirDrop
- Sauvegarde manuelle ou automatique vers un dossier de l'utilisateur (Google Drive, OneDrive, local…)
- Restauration depuis un fichier `.db` avec validation d'en-tête SQLite
- Verrouillage biométrique optionnel au démarrage
- Tableau de bord (CA mensuel/annuel, TVA collectée, top produits, top clients)
- Import / export CSV (visible côté France uniquement, format spécifique)
- Internationalisation français / anglais (anglais = défaut, français en `values-fr`)

### Identité & repo

- **Rebrand complet en Snapfacture** : package `com.snapfacture`, applicationId `com.snapfacture`, DB `snapfacture.db`, repo GitHub `snapfacture-android` — l'ancien Ohmybattery est conservé dans un fork séparé
- Schéma DB v1 propre, sans dette des migrations Ohmybattery
- CI publie un APK debug à chaque push
- Tests JUnit pour `CountryProfile` (20 cas couvrant format monnaie, dates, profils)
- README bilingue (avec 3 captures d'écran intégrées), LICENSE MIT, politique de confidentialité

### Données démo

- `docs/demo/demo-plomberie-saadi.db` : kit prêt à restaurer (7 pièces : 5 B2C + 1 B2B + 1 avoir)
- `docs/screenshots/` : 3 PDFs (B2C, B2B, avoir) intégrés au README

---

## 🔨 Prévu (par priorité)

0. **Trouver les premiers utilisateurs**
   Le produit est prêt : c'est le sujet n°1 maintenant. Le reste de cette liste attend des retours réels (forums d'artisans, groupes Facebook BTP, bouche-à-oreille comptables) plutôt que d'être développé à l'aveugle.

1. **Export comptable FEC**
   CSV au format Fichier des Écritures Comptables, importable directement par un expert-comptable français. Effort : ~1 h. Gain : utile à chaque bilan trimestriel.

2. **Import CSV interactif**
   Sélecteur de colonnes pour absorber le format de n'importe quel outil tiers (pas seulement le format figé actuel). Effort : ~3 h. Utile dès qu'un utilisateur migre depuis Pennylane / Sellsy / Excel ou QuickBooks / Wave côté US. Une fois fait, l'import / export redeviennent visibles côté US dans le menu Réglages.

3. **Écrans de déclaration fiscale**
   Rapport trimestriel TVA (France) et déclaration sales tax par état (US), au format prêt à recopier dans le portail des impôts. Effort : ~2 h.

4. **Devis**
   Document non engageant, converti en facture après accord du client. Absent aujourd'hui alors que c'est l'objet n°1 de la vie d'un artisan — plus grosse opportunité produit identifiée à ce jour. Effort non chiffré : **à valider avec les premiers retours utilisateurs avant de coder**, pas à développer à l'aveugle.

5. **Précision taxe en basis points**
   Passer du stockage `permille` (1/1000) au `basisPoints` (1/10000) pour exactement représenter 6,25 % / 7,25 % / etc. Effort : ~30 min, à faire dès qu'un utilisateur US se plaint.

6. **Factur-X / PDP**
   Émission de factures électroniques B2B au format Factur-X (PDF + XML CII intégré) et connexion à au moins une Plateforme de Dématérialisation Partenaire. Obligatoire pour les TPE françaises à compter du **1er septembre 2027**. Effort : 1-2 semaines. À planifier au printemps 2027. **Seule échéance ferme et non négociable de cette liste.**

---

## 🔮 Envisagé (sous conditions)

- **Version iOS** via Kotlin Multiplatform — la base `data/`, `core/country/`, `core/money/`, `core/pdf/` est déjà du Kotlin pur, donc partageable. UI à réécrire en SwiftUI. **Déclencheur** : ≥ 50 utilisateurs actifs sur Android. Sans cette traction, l'effort (~2 mois solo + 99 $/an Apple Developer + validation App Store) n'est pas justifié.
- **Profils pays supplémentaires** (Canada, UK, Belgique…) — l'architecture `CountryProfile` (sealed interface) permet d'en ajouter sans toucher au reste. À faire dès qu'un utilisateur d'un autre pays se manifeste.
- **Assistant IA companion** — page web séparée (`snapfacture.com/assistant`) propulsée par Claude, pour les cas où l'IA apporte vraiment de la valeur : coaching déclaration fiscale, suggestion de catalogue à l'onboarding selon le métier, rédaction d'email de relance. **Construite hors de l'app** pour préserver la promesse « 100 % local, zéro réseau » de Snapfacture. Déclencheur : ≥ 50 utilisateurs actifs.
- **LLM on-device** (Apple Intelligence, Gemini Nano) — pour dictée vocale → facture sur le terrain. À envisager d'ici 18-24 mois quand l'API système devient standard sur Android et iOS.

---

## ❌ Hors-scope

Refus par design, alignés avec la doctrine **minimalisme + cible TPE solo payée au comptant**. À ne pas re-proposer sans nouvel élément :

- **Gestion de stocks** — autre métier (ERP léger type Odoo / Stocky). Mélange l'UX de facturation et impose une saisie quotidienne lourde.
- **Templates PDF thermique 58 mm** — la cible envoie les factures par email / WhatsApp, pas par imprimante de comptoir.
- **Relances impayés** — la cible est payée au comptant (espèces, CB, virement instantané). Pas d'impayés à relancer.
- **Multi-utilisateurs / workflows de validation** — fonctionnalités PME, pas TPE solo.
- **Backend / synchronisation cloud** — la confiance vient de l'absence totale de serveur. Toucher à ça casse la promesse principale.
- **Intégration IA cloud dans l'app** — même raison que ci-dessus. L'IA passe par un companion externe optionnel (cf. 🔮).
- **Bouton de changement de pays après l'onboarding** — cas rarissime (un Français qui devient Américain), inutile à exposer en permanence. Si un utilisateur a vraiment besoin de changer, il efface les données et refait l'onboarding.

---

## Roadmap (English)

Status: ✅ shipped · 🔨 planned · 🔮 considered · ❌ out of scope

### ✅ Shipped
Few-tap invoicing, credit notes, gapless chronological numbering, SHA-256 audit chain (FR only), per-invoice company snapshot, FR / US country profiles (invisible after onboarding), B2B mentions, VAT-free mode, configurable default sales tax (US), catalog, FR-only CSV import/export, local PDF + share, SAF backup/restore, biometric lock, stats dashboard, blocking onboarding, FR/EN i18n, **full Snapfacture rebrand** (`com.snapfacture`, clean v1 schema, `snapfacture-android` repo), JUnit tests for CountryProfile, CI APK release, sample `Plomberie Saadi` database + three PDF screenshots in the README.

### 🔨 Planned (priority order)
0. **Find the first users** — the product is ready; everything below waits on real feedback rather than being built blind.
1. **FEC accounting export** — French statutory accounting CSV. ~1 h.
2. **Interactive CSV import** — column mapper for any third-party format (FR + US). ~3 h.
3. **Tax filing screens** — quarterly VAT (FR) / state sales tax (US) summaries. ~2 h.
4. **Quotes / estimates** — non-binding document converted to an invoice on client approval. Missing today despite being an artisan's #1 document — the single biggest product opportunity identified so far. Effort unscoped: to validate with early user feedback before building.
5. **Tax precision in basis points** — replace permille (1/1000) with basis points (1/10000) to represent 6.25 %, 7.25 % exactly. ~30 min.
6. **Factur-X / PDP integration** — mandatory for B2B in France from September 2027. ~1-2 weeks, plan for spring 2027. The only firm, non-negotiable deadline on this list.

### 🔮 Considered (conditional)
- **iOS version** via Kotlin Multiplatform once Android reaches ≥ 50 active users.
- **More country profiles** (Canada, UK, …) on demand.
- **AI companion page** powered by Claude, hosted at `snapfacture.com/assistant`, separate from the app to preserve the 100 % local promise.
- **On-device LLM** (Apple Intelligence, Gemini Nano) for voice-to-invoice on site, in 18-24 months.

### ❌ Out of scope
Inventory, thermal-printer PDF templates, unpaid-invoice reminders, multi-user / approval workflows, cloud backend, in-app cloud AI, post-onboarding country switcher.
