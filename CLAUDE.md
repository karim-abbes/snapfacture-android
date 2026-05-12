# Ohmybattery — facturation Android

Application de facturation Android pour Ohmybattery (vente de batteries auto + pose à domicile, SIREN 887714228).

## Principe directeur

**Le minimalisme est la sophistication suprême.**

Toute décision de design, d'UX et d'architecture doit respecter ce principe :

- Préférer **une action en 1 tap** à un formulaire en plusieurs étapes.
- Cacher tout ce qui n'est pas essentiel à l'instant T (champs optionnels repliés, options avancées dans des sous-écrans).
- **Pas d'écran « pour faire joli »** : chaque écran a un seul objectif principal.
- **Pas de configuration superflue** : les défauts doivent suffire à 95 % des usages réels.
- **Pas d'abstraction prématurée** dans le code : trois lignes similaires valent mieux qu'une factorisation trop tôt.
- **Pas de commentaires** sauf si la raison du « pourquoi » n'est pas évidente (voir guidelines du système).
- Privilégier les **boutons gros et clairs** (utilisation au comptoir / sur la route, gants possibles) plutôt que les menus profonds.

Quand un compromis se présente entre « plus de fonctionnalités » et « plus simple » → choisir **plus simple** par défaut. Demander avant d'ajouter.

## Posture face aux propositions de l'utilisateur

Le rôle attribué est celui de **co-stratège**, pas d'exécutant silencieux. À chaque proposition de l'utilisateur :

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
- Room (SQLite local), Hilt (DI), Coroutines/Flow
- 100 % local : pas de backend, pas de cloud — sauvegarde via export CSV / partage de fichiers

## Conventions métier

- **Montants stockés en Long cents** (jamais Double) pour éviter toute dérive virgule flottante. HT dérivé du TTC via `vatRatePermille` (1000-base) pour matcher le format français.
- **Numérotation chronologique sans rupture** (obligation légale art. 242 nonies A CGI). Démarrage à 1693 (continue après l'export historique fourni).
- **Factures émises = immuables**. Une correction passe par une facture d'avoir (à implémenter). `AuditLog` chaîne SHA-256 pour traçabilité loi anti-fraude TVA 2018.
- **Lignes figées au moment de l'émission** : changer le prix d'un produit du catalogue n'affecte que les factures futures.

## Branche de travail

`claude/android-invoicing-app-KGGzw` — tout commit/push se fait sur cette branche.

## CI

`.github/workflows/build-apk.yml` — chaque push déclenche un build qui publie l'APK debug dans la release `latest-debug`. Sur échec, un log est commit sur `.ci-logs/last-failure.md` pour diagnostic à distance.
