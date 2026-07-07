# Changelog

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/). Les versions correspondent aux tags git `v*`.

## [Non publié]

### Corrigé
- **Arrondi de TVA calculé sur le total de ligne** (et non plus par unité multipliée par la quantité) : fini les écarts de plusieurs dizaines de centimes sur les grosses quantités. Arithmétique entière pure, plus aucun nombre flottant dans les calculs d'argent.
- **Émission d'avoir atomique** : un crash au mauvais moment ne peut plus consommer un numéro de facture sans créer l'avoir (numérotation sans rupture garantie).
- **Plus de double émission possible** : si la génération du PDF échoue après l'émission, l'app navigue vers la facture créée (le PDF se régénère) au lieu de laisser le panier prêt à ré-émettre.
- Le compteur « prochain n° de facture » ne peut plus être abaissé sous le dernier numéro émis.
- Mode sombre : le bouton « Nouvelle facture » et les cartes colorées respectent enfin la palette de l'app.
- Le bouton « Encaisser » ne passe plus sous la barre de navigation gestuelle.
- Échec d'émission d'avoir désormais affiché (plus d'échec silencieux).
- Purge du nom « Facturix » (prompt biométrique, export CSV, message de restauration).
- Sauvegarde : le vidage du journal WAL est vérifié — plus de sauvegardes silencieusement amputées des dernières factures.
- Restauration : validation complète du fichier (intégrité SQLite, tables attendues, version de schéma) **avant** tout écrasement, avec copie de secours automatique de la base actuelle.
- PDF : un nom d'entreprise long ne chevauche plus le titre « FACTURE N° X » (réduction de taille puis troncature avec « … »).
- PDF : les champs entreprise vides n'affichent plus de ponctuation résiduelle (virgule seule, puces orphelines, « SIREN » sans valeur) — les fragments vides disparaissent, la ligne entière est sautée si rien ne reste.
- Landing : taille réelle de l'APK affichée (~19 Mo).
- Liste des factures : carte CA compacte (une ligne) au lieu d'une carte pleine hauteur — plus de factures visibles sans défiler.

### Ajouté
- **Date d'intervention optionnelle** à la création de facture (mention légale, art. 242 nonies A CGI : la date de la prestation doit figurer si elle diffère de la date d'émission). Repliée par défaut derrière un lien « + Date d'intervention différente ? » — rien ne change pour la facture du jour. La date choisie s'imprime sur le PDF (« Livraison ») ; l'ancienne heuristique qui datait automatiquement les prestations avec note est retirée.
- **Barre de navigation basse** persistante (Factures / Devis / Stats) avec icône + libellé, accessible au pouce — remplace les icônes sans texte tassées dans la barre du haut. Les Réglages restent une icône (destination secondaire).
- **Bouton « Nouveau devis »** sur l'écran Devis : créer un devis sans passer par l'écran Facture.
- **Pastille de statut** sur les devis (« Facturé » / « Expiré »), au même niveau visuel que le titre.
- **Récap TVA / sales tax** (Réglages) : base HT et taxe ventilées par taux, trimestre par trimestre, avoirs déduits — les chiffres exacts à recopier dans la déclaration.
- **Export comptable FEC** (Réglages, profil France) : génère le Fichier des Écritures Comptables normalisé (art. A47 A-1 du LPF) — une écriture de vente équilibrée par facture ou avoir (client 411 / ventes 706 / TVA collectée 44571), nom de fichier réglementaire SIRENFECdate.txt, partage direct au comptable.
- **Devis** : création depuis le même écran que les factures (bouton « Devis »), numérotation propre (indépendante de la séquence légale des factures), PDF avec validité 30 jours, liste et détail dédiés, et conversion en facture en 1 tap avec les lignes et prix figés. Un devis facturé ne peut pas l'être deux fois.
- **Mentions légales complètes sur le PDF** : n° de TVA intracommunautaire, forme juridique, conditions d'escompte (« néant »). Champs correspondants dans Réglages → Entreprise.
- **Chaîne anti-fraude vérifiable** : le journal d'audit couvre désormais tout le contenu légal de chaque facture et peut être recalculé ; bouton « Vérifier l'intégrité » dans Réglages (profil France) qui détecte toute modification des factures ou du journal hors de l'app.
- Chèque proposé comme mode de paiement à l'encaissement.
- Export CSV disponible aussi sur le profil États-Unis.
- Rotation des sauvegardes (30 fichiers conservés).
- Releases taguées : APK release **signé** avec une clé stable (mises à jour sans désinstallation), tests obligatoires avant publication.
- Suite de tests : invariants d'argent, numérotation, avoirs, détection de falsification, parseur CSV.
- Métadonnées F-Droid (fastlane) et guides `docs/RELEASING.md` / `docs/FDROID.md`.
- `CONTRIBUTING.md`, wrapper Gradle commité, export du schéma de base versionné.

### Modifié
- Interface entièrement localisée : plus aucun message d'erreur français codé en dur (l'anglais est la langue par défaut, le français suit la langue du téléphone).
- README/PRIVACY : formulation honnête du stockage (espace privé de l'app protégé par le chiffrement du disque Android ; la sauvegarde exportée n'est pas chiffrée).

## [1.0.0] — 2026-05-17

Version initiale publique : facturation en quelques taps, avoirs, numérotation légale, PDF + partage, profils France/États-Unis, franchise TVA, catalogue, stats, import/export CSV, sauvegarde SAF, verrou biométrique, i18n FR/EN.
