# Roadmap

État vivant de ce qui est livré, de ce qui est prévu, et de ce qui ne sera **pas** fait — pour cadrer les contributions et éviter les revirements.

Statuts : ✅ livré · 🔨 prévu · 🔮 envisagé · ❌ hors-scope

---

## ✅ Livré

- Création de facture en quelques taps, optimisée pour usage au comptoir / sur la route
- Avoirs (notes de crédit) en 1 tap depuis une facture
- Numérotation chronologique sans rupture (conforme art. 242 nonies A CGI)
- Chaîne anti-fraude SHA-256 (loi anti-fraude TVA 2018) — active uniquement sur profil France
- Snapshot des informations entreprise par facture (immuabilité légale)
- Profils pays France / États-Unis (devise, format de date, libellé taxe, mentions B2B, anti-fraude)
- Mentions B2B automatiques sur PDF (SIRET client, pénalités L441-10, indemnité D441-5) — France uniquement
- Mode franchise TVA (auto-entrepreneur)
- Taux de sales tax par défaut configurable (US)
- Catalogue produits avec note de service optionnelle
- Import / export CSV
- Génération PDF locale + partage par email / message / AirDrop
- Sauvegarde manuelle ou automatique vers un dossier de l'utilisateur (Google Drive, OneDrive, local…)
- Restauration depuis un fichier `.db` avec validation d'en-tête SQLite
- Verrouillage biométrique optionnel au démarrage
- Tableau de bord (CA mensuel/annuel, TVA collectée, top produits, top clients)
- Onboarding bloquant au premier lancement (dialogue centré, 3 champs)
- Internationalisation français / anglais (anglais = défaut, français en `values-fr`)
- CI publie un APK debug à chaque push
- README bilingue, LICENSE MIT, politique de confidentialité

---

## 🔨 Prévu (par priorité)

1. **Export comptable FEC**
   CSV au format Fichier des Écritures Comptables, importable directement par un expert-comptable français. Effort : ~1 h. Gain : utile à chaque bilan trimestriel.

2. **Import CSV interactif**
   Sélecteur de colonnes pour absorber le format de n'importe quel outil tiers (pas seulement le format figé actuel). Effort : ~3 h. Utile dès qu'un utilisateur migre depuis Pennylane / Sellsy / Excel.

3. **Écrans de déclaration fiscale**
   Rapport trimestriel TVA (France) et déclaration sales tax par état (US), au format prêt à recopier dans le portail des impôts. Effort : ~2 h.

4. **Précision taxe en basis points**
   Passer du stockage `permille` (1/1000) au `basisPoints` (1/10000) pour exactement représenter 6,25 % / 7,25 % / etc. Effort : ~30 min, à faire dès qu'un utilisateur US se plaint.

5. **Captures d'écran dans le README**
   Le dossier `docs/screenshots/` est prêt. Manquent 3 PNG (liste / création / détail).

---

## 🔮 Envisagé (sous conditions)

- **Version iOS** via Kotlin Multiplatform — la base `data/`, `core/country/`, `core/money/`, `core/pdf/` est déjà du Kotlin pur, donc partageable. UI à réécrire en SwiftUI. **Déclencheur** : ≥ 50 utilisateurs actifs sur Android. Sans cette traction, l'effort (~2 mois solo + 99 $/an Apple Developer + validation App Store) n'est pas justifié.
- **Profils pays supplémentaires** (Canada, UK, Belgique…) — l'architecture `CountryProfile` (sealed interface) permet d'en ajouter sans toucher au reste. À faire dès qu'un utilisateur d'un autre pays se manifeste.

---

## ❌ Hors-scope

Refus par design, alignés avec la doctrine **minimalisme + cible TPE solo payée au comptant**. À ne pas re-proposer sans nouvel élément :

- **Gestion de stocks** — autre métier (ERP léger type Odoo / Stocky). Mélange l'UX de facturation et impose une saisie quotidienne lourde.
- **Templates PDF thermique 58 mm** — la cible envoie les factures par email / WhatsApp, pas par imprimante de comptoir.
- **Relances impayés** — la cible est payée au comptant (espèces, CB, virement instantané). Pas d'impayés à relancer.
- **Multi-utilisateurs / workflows de validation** — fonctionnalités PME, pas TPE solo.
- **Backend / synchronisation cloud** — la confiance vient de l'absence totale de serveur. Toucher à ça casse la promesse principale.

---

## Roadmap (English)

Status: ✅ shipped · 🔨 planned · 🔮 considered · ❌ out of scope

### ✅ Shipped
Few-tap invoicing, credit notes, gapless chronological numbering, SHA-256 audit chain (FR only), per-invoice company snapshot, FR / US country profiles, B2B mentions, VAT-free mode, configurable default sales tax (US), catalog, CSV import/export, local PDF + share, SAF backup/restore, biometric lock, stats dashboard, blocking onboarding, FR/EN i18n, CI APK release.

### 🔨 Planned (priority order)
1. **FEC accounting export** — French statutory accounting CSV. ~1 h.
2. **Interactive CSV import** — column mapper for any third-party format. ~3 h.
3. **Tax filing screens** — quarterly VAT (FR) / state sales tax (US) summaries. ~2 h.
4. **Tax precision in basis points** — replace permille (1/1000) with basis points (1/10000) to represent 6.25 %, 7.25 % exactly. ~30 min.
5. **README screenshots** — drop 3 PNGs into `docs/screenshots/`.

### 🔮 Considered (conditional)
- **iOS version** via Kotlin Multiplatform once Android reaches ≥ 50 active users.
- **More country profiles** (Canada, UK, …) on demand.

### ❌ Out of scope
Inventory, thermal-printer PDF templates, unpaid-invoice reminders, multi-user / approval workflows, cloud backend.
