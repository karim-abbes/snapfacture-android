# Contribuer à Snapfacture

Merci de vouloir aider ! Trois règles simples avant de commencer.

## 1. Lire la roadmap d'abord

[ROADMAP.md](ROADMAP.md) liste ce qui est prévu, envisagé **et refusé par design** (section « Hors-scope »). Une PR qui ajoute une fonctionnalité hors-scope sera fermée, même bien codée — ce n'est pas contre vous, c'est le principe du produit : *le minimalisme est la sophistication suprême*.

Pour une nouvelle fonctionnalité, **ouvrez une issue avant de coder** pour valider l'idée.

## 2. Compiler et tester

```bash
git clone https://github.com/karim-abbes/snapfacture-android.git
cd snapfacture-android
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # tests unitaires
```

Prérequis : JDK 17 + Android SDK (via Android Studio ou `sdkmanager`). L'APK sort dans `app/build/outputs/apk/debug/`.

## 3. Style et attentes

- Kotlin idiomatique, Jetpack Compose, MVVM — suivez le style des fichiers voisins.
- Montants **toujours en Long cents**, jamais en Double.
- Pas de commentaire sauf pour expliquer un « pourquoi » non évident.
- Toute logique d'argent, de numérotation ou de conformité doit arriver **avec des tests** (voir `app/src/test/`).
- Aucune permission réseau, jamais — c'est la promesse fondatrice du produit.
- Textes UI dans `strings.xml` (EN) **et** `values-fr/strings.xml` (FR), jamais en dur dans le code.

Les PR passent par la CI (build + tests). Une PR courte et ciblée est relue vite ; une PR fourre-tout attendra.

## Signaler un bug

Ouvrez une issue avec : version de l'app (Réglages ou nom de l'APK), version d'Android, étapes de reproduction, et ce que vous attendiez.
