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

Rôle de **co-stratège**, pas d'exécutant silencieux. À chaque proposition :

1. Donner un **avis objectif** avant de coder — pour, contre, alternatives, conséquences cachées.
2. Être direct si la proposition est mauvaise, incomplète, ou en contradiction avec le principe minimaliste.
3. Si plusieurs chemins sont défendables, dire lequel je recommanderais **et pourquoi**.
4. Ne jamais valider passivement par politesse. Mieux vaut un désaccord argumenté maintenant qu'un refactor douloureux plus tard.

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

- **Montants stockés en Long cents** (jamais Double) pour éviter toute dérive virgule flottante. HT dérivé du TTC via `vatRatePermille` (1000-base) pour matcher le format français.
- **Numérotation chronologique sans rupture** (obligation légale art. 242 nonies A CGI). Démarrage à 1 par défaut, modifiable depuis Réglages → Entreprise.
- **Factures émises = immuables**. Une correction passe par une facture d'avoir (déjà implémentée, accessible en 1 tap depuis le détail d'une facture). `AuditLog` chaîne SHA-256 pour traçabilité loi anti-fraude TVA 2018.
- **Lignes figées au moment de l'émission** : changer le prix d'un produit du catalogue n'affecte que les factures futures.
- **Snapshot entreprise par facture** : nom, SIREN, adresse, gérant, régime TVA et SIRET client sont copiés sur l'`InvoiceEntity` à l'émission — modifier la fiche entreprise n'altère jamais les factures passées.
- **Mentions B2B** (SIRET client + pénalités L441-10 + indemnité 40 € D441-5) imprimées automatiquement sur le PDF dès qu'un SIRET client est renseigné.

## Onboarding & premier lancement

- Les seeds sont vides (entreprise et catalogue) : un nouvel utilisateur tombe sur un écran d'accueil bloquant tant que **nom de l'entreprise + nom du gérant + pays** ne sont pas saisis.
- Les autres champs (adresse, SIREN/EIN, IBAN, téléphone…) se complètent ensuite depuis Réglages → Entreprise.

## Distribution

- Licence **MIT** (`LICENSE`)
- README bilingue FR/EN à la racine
- Politique de confidentialité dans `PRIVACY.md` (zéro donnée collectée, zéro permission réseau)
- Captures d'écran : à déposer dans `docs/screenshots/` puis dé-commenter le bloc dans le README

## Branche de travail

`claude/android-invoicing-app-KGGzw` — tout commit/push se fait sur cette branche.

## CI

`.github/workflows/build-apk.yml` — chaque push déclenche un build qui publie l'APK debug dans la release `latest-debug`. Sur échec, un log est commit sur `.ci-logs/last-failure.md` pour diagnostic à distance.
