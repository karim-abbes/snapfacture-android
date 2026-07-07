# Snapfacture — facturation Android open source

Application de facturation Android **minimaliste, 100 % locale, open source (MIT)**, distribuée via GitHub Releases.

## Contexte & cible

- Produit pensé pour **artisans, auto-entrepreneurs, indépendants 1 à 3 personnes**, payés au comptant (espèces, CB, virement instantané).
- Marchés visés : **France** (priorité, marché mal servi face à Pennylane / Sellsy trop chers) et **États-Unis** (profil pays existant, à activer si traction FR).
- **Historique** : ce repo a été extrait d'une app interne Ohmybattery (vente + pose de batteries auto). Cette version-là est conservée dans un fork séparé. Ici, on cible le grand public et on garde le code générique — pas de retour en arrière sur les concepts spécifiques (véhicule, batteries, etc.).

## Principe directeur

**Le minimalisme est la sophistication suprême.**

- Préférer **une action en 1 tap** à un formulaire en plusieurs étapes.
- Cacher tout ce qui n'est pas essentiel à l'instant T (champs optionnels repliés, options avancées dans des sous-écrans).
- **Pas d'écran « pour faire joli »** : chaque écran a un seul objectif principal.
- **Pas de configuration superflue** : les défauts doivent suffire à 95 % des usages réels.
- **Pas d'abstraction prématurée** dans le code : trois lignes similaires valent mieux qu'une factorisation trop tôt.
- **Pas de commentaires** sauf si la raison du « pourquoi » n'est pas évidente (voir guidelines du système).
- Privilégier les **boutons gros et clairs** (usage au comptoir / sur la route, gants possibles) plutôt que les menus profonds.
- Refuser par défaut toute fonctionnalité « PME » (multi-utilisateurs, workflows de validation, relances impayés systématiques, etc.) — ce n'est pas la cible.

Quand un compromis se présente entre « plus de fonctionnalités » et « plus simple » → choisir **plus simple** par défaut. Demander avant d'ajouter.

## Posture face aux propositions de l'utilisateur

Rôle de **co-stratège**, pas d'exécutant silencieux. **Avant de coder quoi que ce soit**, à chaque proposition :

1. **Dire explicitement si l'idée est bonne ou mauvaise** — pas d'acquiescement implicite par mise à exécution.
2. Donner un **avis objectif** : pour, contre, alternatives, conséquences cachées.
3. Être direct si la proposition est mauvaise, incomplète, ou en contradiction avec le principe minimaliste.
4. Si plusieurs chemins sont défendables, dire lequel je recommanderais **et pourquoi**.
5. Ne jamais valider passivement par politesse. Mieux vaut un désaccord argumenté maintenant qu'un refactor douloureux plus tard.

## Façon de parler

- **Parler simplement, sans jargon technique** quand il n'est pas nécessaire.
- Si un terme technique est utilisé (Room, locale, migration, etc.), l'expliquer en une phrase courte juste après — pas après que l'utilisateur l'a demandé.
- Réponses courtes par défaut. Détailler uniquement quand l'utilisateur le demande.

## Stack technique

- Kotlin 2.0 + Jetpack Compose + Material 3
- MVVM (Clean light) — `data/`, `domain/` léger, `ui/`
- Room (SQLite local), Hilt (DI), Coroutines/Flow, DataStore Preferences
- BiometricPrompt (verrou optionnel au démarrage)
- Storage Access Framework pour la sauvegarde / restauration vers un dossier choisi par l'utilisateur (Drive, OneDrive, local…)
- **100 % local** : aucun backend, aucun cloud, aucune permission réseau dans Android
- Internationalisation FR + EN (anglais = défaut, français = `values-fr`)
- Profils pays via `CountryProfile` (sealed interface : `FranceProfile`, `UsaProfile`) — le profil est dérivé du champ `country` de la fiche entreprise, pas de la langue du téléphone

## Conventions métier

- **Montants stockés en Long cents** (jamais Double) pour éviter toute dérive virgule flottante. HT dérivé du TTC via `vatRateBp` (basis points, base 10 000 : 2000 = 20 %, 625 = 6,25 % — les taux US sont exacts). Sérialisation canonique en permille dans le payload d'audit (`TaxRate.canonicalPermille` : « 200 », pas « 2000 ») pour que les factures d'avant la migration v6 restent vérifiables.
- **Quantités en Long millièmes d'unité** (`quantityMilliUnits`, 1500 = 1,5) — même pattern entier que les taux. Sérialisation canonique dans le payload d'audit (`Quantity.canonical` : « 2 », pas « 2000 ») pour que les factures d'avant la migration v4 restent vérifiables.
- **Numérotation chronologique sans rupture** (obligation légale art. 242 nonies A CGI). Démarrage à 1 par défaut, modifiable depuis Réglages → Entreprise.
- **Factures émises = immuables**. Une correction passe par une facture d'avoir (déjà implémentée, accessible en 1 tap depuis le détail d'une facture). `AuditLog` chaîne SHA-256 pour traçabilité loi anti-fraude TVA 2018.
- **Lignes figées au moment de l'émission** : changer le prix d'un produit du catalogue n'affecte que les factures futures.
- **Snapshot entreprise par facture** : nom, SIREN, adresse, gérant, régime TVA et SIRET client sont copiés sur l'`InvoiceEntity` à l'émission — modifier la fiche entreprise n'altère jamais les factures passées.
- **Mentions B2B** (SIRET client + pénalités L441-10 + indemnité 40 € D441-5) imprimées automatiquement sur le PDF dès qu'un SIRET client est renseigné.
- **Mentions réforme e-facturation 2026** (schéma Room v5) : catégorie d'opération (`CompanyEntity.operationCategory`, réglage entreprise) et option TVA sur les débits (`vatOnDebits`, masquée en franchise) — snapshotées sur la facture (`*AtIssue`) à l'émission ; adresse de livraison optionnelle par facture (`deliveryAddress`), imprimée seulement si différente de celle du client. **Aucun de ces champs n'entre dans le payload d'audit** (changer son format casserait la vérification des factures existantes).

## Onboarding & premier lancement

- Les seeds sont vides (entreprise et catalogue) : un nouvel utilisateur tombe sur un écran d'accueil bloquant tant que **nom de l'entreprise + nom du gérant + pays** ne sont pas saisis.
- Les autres champs (adresse, SIREN/EIN, IBAN, téléphone…) se complètent ensuite depuis Réglages → Entreprise.

## Distribution

- Licence **MIT** (`LICENSE`)
- README bilingue FR/EN à la racine
- Politique de confidentialité dans `PRIVACY.md` (zéro donnée collectée, zéro permission réseau)
- Captures d'écran : à déposer dans `docs/screenshots/` puis dé-commenter le bloc dans le README

## Branche de travail

`claude/app-overview-status-qzny2p` — tout commit/push se fait sur cette branche.

## CI & releases

- `.github/workflows/build-apk.yml` — chaque push déclenche build + tests unitaires et publie l'APK debug dans la release `latest-debug`.
- `.github/workflows/build-tagged-apk.yml` — un tag `v*` publie un APK **release signé** (keystore en secrets GitHub, voir `docs/RELEASING.md`) avec version dérivée du tag. Le workflow échoue volontairement tant que les secrets keystore ne sont pas configurés.

## État au 7 juillet 2026 (reprise de session)

Un **audit complet** a été réalisé (`docs/AUDIT-2026-07.md`, ~170 constats) puis 4 lots correctifs livrés et validés par la CI (10 commits) :
1. Légal/argent : arrondi TVA au niveau ligne (arithmétique entière, `Money.lineAmounts`), mentions PDF ajoutées (TVA intracom, forme juridique, escompte), avoirs transactionnels, anti-double-émission (PDF = best-effort après émission), garde-fou sur « prochain n° ».
2. Chaîne anti-fraude rendue **vérifiable** (payload en clair en base, schéma Room **v2** + migration, `verifyAuditChain()` + bouton Réglages → « Vérifier l'intégrité »), backup durci (checkpoint WAL vérifié, validation restore + `.bak`, rotation 30 fichiers), **27 tests** (Money, Robolectric sur numérotation/avoirs/falsification, CsvParser).
3. i18n complète (plus de français en dur dans le code), métadonnées F-Droid (`fastlane/`), `docs/FDROID.md`, `CHANGELOG.md`.
4. UX premier lancement : états vides guidés, **ligne libre** au panier (produit transient à id négatif, jamais persisté), chips clients récents, `rememberSaveable` sur les formulaires critiques.

**Livré le 7 juillet 2026 (suite)** : **devis** (numérotation propre, PDF validité 30 j, conversion 1 tap en facture — atomique, testée) ; **export FEC** (Réglages, profil FR, écritures 411/706/44571 équilibrées, nom réglementaire) ; **récap TVA/sales tax** (ventilation par taux et par trimestre, avoirs déduits) ; fixes PDF (collision nom/titre via mesure+réduction+troncature, champs vides sans ponctuation orpheline via `joinNonBlank`) ; landing à ~19 Mo réels. UX inspirée VTCFact (barre de nav basse Factures/Devis/Stats, FAB devis, CA compact, pastille statut) ; **date d'intervention optionnelle** (lien replié, DatePicker M3, normalisation minuit UTC → midi local) ; **quantités décimales** (`quantityMilliUnits`, saisie au tap sur la ligne du panier, schéma Room **v4** avec migration ×1000, payload d'audit canonique rétro-compatible) ; **inscription email optionnelle** sur la landing page (Formspree, jamais bloquante avant le téléchargement, `PRIVACY.md` reformulé en conséquence) ; **mentions réforme e-facturation 2026** (catégorie d'opération + TVA sur débits + adresse de livraison, schéma Room **v5**, snapshots à l'émission) ; **import CSV interactif** (écran de correspondance des colonnes pré-rempli par détection des en-têtes, séparateur ,/; auto, montants FR/US, import ouvert au profil US) ; **taux de taxe en basis points** (`vatRateBp`, schéma Room **v6** avec migration ×10, payload d'audit canonique permille rétro-compatible, saisie « 6,25 » via `TaxRate`). La CI affiche les messages d'assertion complets.

**Fusion** : tout ce qui précède a été fusionné dans `main` (PR #1 et #2) — la landing page GitHub Pages et `main` reflètent désormais l'état réel de l'app. Le développement continue sur `claude/app-overview-status-qzny2p`, à refusionner périodiquement.

**En attente côté utilisateur** : (1) keystore + 4 secrets GitHub (`docs/RELEASING.md`) puis tag `v1.1.0` ; (2) captures d'écran dans `fastlane/metadata/android/*/images/phoneScreenshots/` puis soumission F-Droid.

**Prochaines étapes recommandées** : acquisition des premiers utilisateurs ; **Factur-X avant sept. 2027** (seule échéance ferme). Reporté volontairement : presets de catalogue par métier (prix pré-remplis faux = pire qu'un catalogue vide ; variante « libellés sans prix » discutée).
