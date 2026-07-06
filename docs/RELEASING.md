# Publier une release signée

Une release Snapfacture est un APK **release** (minifié, non debuggable), signé avec **une clé stable**. C'est cette stabilité de clé qui permet aux utilisateurs de mettre à jour l'app sans désinstaller — donc sans perdre leurs factures.

## 1. Créer le keystore (une seule fois, à conserver précieusement)

```bash
keytool -genkeypair -v \
  -keystore snapfacture-release.keystore \
  -alias snapfacture \
  -keyalg RSA -keysize 4096 -validity 10000
```

⚠️ **Sauvegarder ce fichier et ses mots de passe hors du dépôt** (gestionnaire de mots de passe + copie hors ligne). Perdre le keystore = impossible de publier une mise à jour installable par-dessus l'existant.

## 2. Enregistrer les secrets GitHub (une seule fois)

Dans *Settings → Secrets and variables → Actions* du dépôt :

| Secret | Contenu |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 snapfacture-release.keystore` |
| `RELEASE_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `RELEASE_KEY_ALIAS` | `snapfacture` |
| `RELEASE_KEY_PASSWORD` | mot de passe de la clé |

## 3. Publier

```bash
git tag v1.1.0
git push origin v1.1.0
```

Le workflow `build-tagged-apk.yml` lance les tests, construit l'APK release signé avec `versionName = 1.1.0` (dérivé du tag) et un `versionCode` croissant, puis publie une release GitHub figée.

## Build local

Sans les variables d'environnement `RELEASE_KEYSTORE_*`, `./gradlew assembleRelease` produit un APK non signé — normal, la signature n'est faite qu'en CI.
