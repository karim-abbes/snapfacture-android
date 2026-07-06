# Politique de confidentialité — Snapfacture

**En une phrase** : Snapfacture ne collecte rien, n'envoie rien et ne te suit pas. Tes données restent sur ton téléphone.

## Données stockées sur ton appareil

L'application stocke localement, dans son espace privé Android (inaccessible aux autres apps, protégé par le chiffrement du disque de ton téléphone) :

- Les informations de ton entreprise (nom, adresse, SIREN/EIN, etc.)
- Les informations de tes clients (nom, téléphone, email, adresse, SIRET le cas échéant)
- Tes factures et avoirs (lignes, montants, dates, mode de paiement, PDF générés)
- Ton catalogue de produits/services
- Un journal d'audit anti-fraude (hash SHA-256 chaînés) — obligation légale française

**Ces données ne quittent jamais ton téléphone**, sauf si **tu décides toi-même** de :

- Partager un PDF de facture (par email, message, AirDrop, etc.) — c'est toi qui choisis le destinataire
- Exporter en CSV vers ton stockage local
- Activer la sauvegarde vers un dossier de ton choix (Google Drive, OneDrive, stockage local…) — Snapfacture **ne sait pas** ce que devient le fichier après l'avoir déposé dans le dossier, c'est ton fournisseur de stockage qui gère. **Ce fichier de sauvegarde n'est pas chiffré** : il contient tes factures et fiches clients en clair, protège donc l'accès à ton compte cloud (mot de passe fort, double authentification)

## Données collectées par l'application

**Aucune.**

- Pas d'analytique
- Pas de télémétrie
- Pas de crash reporting envoyé en ligne
- Pas de publicité
- Pas de compte utilisateur
- Pas de connexion à un serveur (l'app fonctionne 100 % hors-ligne)

L'application ne demande **aucune permission réseau** dans Android.

## Permissions Android demandées

| Permission | Pourquoi |
|---|---|
| `USE_BIOMETRIC` | Verrouillage optionnel par empreinte ou code de l'appareil |
| Accès au dossier de sauvegarde choisi par toi (SAF) | Écrire le fichier `.db` de sauvegarde dans le dossier que tu sélectionnes |

C'est tout. Aucune permission réseau, GPS, contacts, appareil photo, micro, etc.

## Suppression de tes données

Désinstalle l'app depuis Android → toutes les données sont effacées. Si tu avais activé une sauvegarde, le fichier `.db` que tu as déposé dans ton dossier reste là-bas — supprime-le manuellement si tu le souhaites.

## Contact

Pour toute question : ouvre une issue sur le dépôt GitHub.

---

# Privacy Policy — Snapfacture (English)

**In one sentence**: Snapfacture collects nothing, sends nothing, and does not track you. Your data stays on your phone.

## Data stored on your device

The app stores locally, in its private Android storage (inaccessible to other apps, protected by your phone's disk encryption):

- Your business info (name, address, SIREN / EIN, etc.)
- Your customer info (name, phone, email, address, business ID when applicable)
- Your invoices and credit notes (line items, amounts, dates, payment method, generated PDFs)
- Your product / service catalog
- An anti-fraud audit log (chained SHA-256 hashes) — required by French law

**This data never leaves your phone**, unless **you choose** to:

- Share an invoice PDF (email, messaging, AirDrop, etc.) — you pick the recipient
- Export to CSV on local storage
- Enable backups to a folder you select (Google Drive, OneDrive, local…) — Snapfacture **does not know** what happens to the file after dropping it in the folder; that's between you and your storage provider. **The backup file is not encrypted**: it contains your invoices and customer records in the clear, so secure your cloud account (strong password, two-factor authentication)

## Data collected by the app

**None.**

- No analytics
- No telemetry
- No crash reporting sent online
- No advertising
- No user account
- No server connection (the app is 100 % offline)

The app requests **no network permission** in Android.

## Android permissions requested

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Optional fingerprint or device credential lock |
| Access to the backup folder you choose (SAF) | Write the backup `.db` file into the folder you select |

That is all. No network, GPS, contacts, camera, microphone, etc.

## Deleting your data

Uninstall the app from Android → all data is wiped. If you had enabled backups, the `.db` file you dropped in your folder stays there — delete it manually if needed.

## Contact

Open an issue on the GitHub repository for any question.
