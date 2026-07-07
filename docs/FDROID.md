# Publier Snapfacture sur F-Droid

F-Droid est le canal de distribution naturel de Snapfacture : gratuit, mises à jour automatiques via le client F-Droid, et un badge de confiance (« aucune anti-feature ») qui prouve exactement la promesse de l'app — pas de réseau, pas de pistage.

## Prérequis (déjà en place dans ce dépôt)

- ✅ Licence libre (MIT) et code 100 % open source
- ✅ Aucune dépendance propriétaire (pas de Google Play Services, pas de Firebase)
- ✅ Aucune permission réseau
- ✅ Wrapper Gradle commité, build reproductible depuis un clone frais
- ✅ Métadonnées fastlane (`fastlane/metadata/android/`) — F-Droid les lit automatiquement (titre, descriptions, captures)

## Reste à faire

1. **Publier une première release taguée signée** (`v1.1.0`) — voir `docs/RELEASING.md`. F-Droid construit depuis les tags.
2. **Ajouter des captures d'écran** dans `fastlane/metadata/android/fr-FR/images/phoneScreenshots/` (et en-US) — fortement recommandé pour la fiche.
3. **Soumettre l'app** :
   - Forker https://gitlab.com/fdroid/fdroiddata
   - Créer `metadata/com.snapfacture.yml` :

```yaml
Categories:
  - Money
License: MIT
AuthorName: Karim Abbes
SourceCode: https://github.com/karim-abbes/snapfacture-android
IssueTracker: https://github.com/karim-abbes/snapfacture-android/issues

AutoName: Snapfacture

RepoType: git
Repo: https://github.com/karim-abbes/snapfacture-android.git

Builds:
  - versionName: 1.1.0
    versionCode: 1
    commit: v1.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 1
```

   - Ouvrir une merge request sur fdroiddata ; le robot de F-Droid vérifie que le build passe.
4. **Après acceptation** : chaque nouveau tag `v*` est détecté et construit automatiquement (`AutoUpdateMode: Version`).

Notes :
- F-Droid signe avec **ses propres clés** (ou supporte la reproducible-build signature) — les utilisateurs F-Droid et GitHub Releases ne peuvent pas mélanger les deux sources sans réinstaller. C'est normal et documenté par F-Droid.
- Le `versionCode` du yml doit correspondre à celui de l'APK du tag. Si le versionCode CI (run number) pose problème pour F-Droid, figer `versionCode` dans `app/build.gradle.kts` à chaque release est l'alternative simple.
