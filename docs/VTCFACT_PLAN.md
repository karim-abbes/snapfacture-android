# VTCfact — Plan stratégique et produit

Document de fondation pour le futur repo `karimlancien/vtcfact-android`. Fork de Snapfacture adapté au marché des VTC indépendants français.

> **Statut** : pré-création. Plan validé sur la base de l'analyse marché + reverse-engineering de BonVTC.fr + un premier contact VTC intéressé.

---

## 1. Contexte stratégique

### Le déclencheur
Un VTC privé a contacté Karim en disant qu'il était intéressé par une app de facturation adaptée à son métier. Discussion → besoins très spécifiques (TVA 10 %, bon de commande obligatoire, workflow bon → facture) que Snapfacture artisan ne couvre pas naturellement.

### Pourquoi un fork plutôt qu'une extension de Snapfacture
- Branding : "Snapfacture" ne parle pas à un VTC, "VTCfact" parle immédiatement
- Workflow différent : Snapfacture = 1 facture émise. VTC = 1 bon avant + 1 facture après.
- Champs spécifiques (eVTC, immat, conducteur, prise en charge, destination) qui polluent l'UX d'un artisan
- Cible distincte : on touche des chauffeurs, pas des plombiers, donc canaux marketing distincts
- Modèle économique potentiellement différent (gratuit comme Snapfacture vs freemium avec PRO si besoin futur)

### Cible précise
**VTC hors plateforme** : chauffeurs qui prennent des clients **directement** (mariages, transferts aéroport, sociétés sous contrat, événementiel, tourisme premium). Pas les chauffeurs Uber/Bolt/Heetch qui reçoivent leurs bons directement de la plateforme.

Volume estimé : 30-40 % des ~55 000 VTC actifs en France → marché adressable de 15 000 à 22 000 utilisateurs potentiels.

---

## 2. Analyse concurrentielle — BonVTC.fr

### Constat clé
BonVTC est leur app de référence mais c'est une **PWA web** (raccourci homescreen), pas une vraie app Android. Gratuit, avec système de parrainage.

### Forces à respecter
- Création de bon conforme légalement (mentions Arrêté 14 février 1986 / 6 janvier 1993)
- Géolocalisation pour la prise en charge
- Bons récurrents (courses régulières pour clients société)
- Confirmation SMS/email au client (FR ou EN) en 1 clic après création
- Profil entreprise exhaustif (eVTC, RCS, RM, capital social, mentions libres)
- Mentions complémentaires libres (texte libre pour clauses spécifiques)

### Faiblesses à exploiter
- **Web app, pas native** : lent, dépendant du réseau, mauvaise UX mobile
- **Compte obligatoire** (login/password) → friction + risque perte données
- **« Mes statistiques » : Disponible prochainement »** dans l'app → trou fonctionnel
- **« Mes clients » : Disponible prochainement »** dans l'app → trou fonctionnel
- **Témoignages contradictoires** : ils citent des avis utilisateurs disant "ça permet d'historiser et de voir les stats" alors que ces fonctions ne sont pas implémentées
- UX vieillotte (design 2015, palette criarde)
- Pas de mode hors-ligne fiable
- Pas évident si conversion bon → facture est intégrée (probablement non, ou réservée à une version future)

### Différenciation VTCfact

| Critère | BonVTC | VTCfact |
|---|---|---|
| Type | PWA (raccourci web) | App native Android |
| Compte | Obligatoire | Aucun |
| Hors-ligne | Non fiable | Garanti |
| Stats | "Disponible prochainement" | **Réelles dès J+0** |
| Répertoire clients | "Disponible prochainement" | **Réel dès J+0** |
| Bon récurrent | Oui | Oui |
| Confirmation SMS/email FR/EN | Oui | Oui |
| Workflow bon → facture | Flou | **1 tap, clair** |
| Conformité légale | Oui | Oui (mêmes arrêtés) |
| 100 % local | Non (cloud) | **Oui** |
| Prix | Gratuit | Gratuit + open source |

### Le bon généré par Uber (pour info, hors cible)
Uber génère ses propres bons dans l'app Uber Driver, avec mentions très détaillées (Article R3120-2 Code des transports, Arrêté du 6 août 2025, montant brut + minimal garanti, distance précise au mètre, liens KBIS/SIRENE actifs 5 min). Ce segment est captif d'Uber, **pas la cible VTCfact**.

---

## 3. Spec produit MVP

### Modèle de données

#### `BonEntity` (nouvelle table)
- `id` (PK auto)
- `number` (chrono séparé des factures, série BON-XXX)
- `clientId` (FK vers ClientEntity)
- `reservationDate` : date + heure de la réservation
- `pickupDate` : date + heure de prise en charge
- `pickupAddress` : adresse complète
- `dropoffAddress` : adresse complète (optionnelle)
- `estimatedDistanceMeters` : Long (optionnel)
- `estimatedPriceCents` : Long (optionnel)
- `driverName` : String (chauffeur, peut différer du gérant)
- `vehiclePlate` : String (plaque obligatoire)
- `vehicleModel` : String (optionnel, "Mercedes Classe E")
- `status` : enum `DRAFT`, `CONFIRMED`, `COMPLETED`, `INVOICED`, `CANCELLED`
- `linkedInvoiceId` : Long? (si déjà converti en facture)
- `pdfPath` : String? (PDF du bon généré)
- `companyNameAtIssue`, etc. (snapshot société comme dans Snapfacture)
- `evtcNumberAtIssue` : numéro carte professionnelle VTC
- `createdAt` : Long

#### `InvoiceEntity` (réutilisée de Snapfacture)
Ajout de `linkedBonId: Long?` pour le lien inverse.

#### `CompanyEntity` (étendue par rapport à Snapfacture)
Ajouts :
- `evtcNumber` : N° carte professionnelle VTC (obligatoire)
- `legalForm` : Forme juridique (EI, SARL, SASU…)
- `rcsNumber` : N° RCS (Registre du Commerce)
- `rmNumber` : N° RM (Registre des Métiers)
- `socialCapital` : Capital social en euros
- `additionalMentions` : Mentions complémentaires libres (textarea)

#### `VehicleEntity` (nouvelle table — optionnel pour le MVP)
Un VTC peut avoir 2-3 véhicules.
- `id`, `model`, `plate`, `isDefault`

### Workflow utilisateur

1. **Onboarding** :
   - Nom de l'entreprise + nom + N° eVTC + plaque véhicule principal + pays
   - Par défaut : France, TVA 10 % (ou franchise si auto-entrepreneur)
2. **Création d'un bon** (3 clics) :
   - Tap "+ Nouveau bon"
   - Adresse de prise en charge (avec géoloc)
   - Date/heure prise en charge (par défaut maintenant + 10 min)
   - Client (nom + tél), pré-rempli si client récurrent
   - Optionnel : destination, prix estimé
   - Tap "Créer le bon"
   - PDF généré + écran de partage SMS/email pré-rempli
3. **Conversion en facture** :
   - Depuis le détail d'un bon : tap "Course effectuée → Facturer"
   - Pré-remplit la facture avec les infos du bon
   - Le VTC ajuste le prix réel si différent de l'estimé
   - Tap "Émettre"
   - Bon passe en statut `INVOICED`
4. **Bons récurrents** :
   - Tap "Nouveau bon récurrent"
   - Sélection des jours de la semaine
   - Heure + lieu + client + destination + tarif
   - Génère automatiquement les bons à la chaîne (sur 4 semaines à l'avance par exemple)

### Mentions légales obligatoires sur le PDF du bon

> **BILLET COLLECTIF (Arrêté du 14 février 1986 — Article 5)**
> et
> **ORDRE DE MISSION (Arrêté du 6 janvier 1993 — Article 3)**

Plus :
- **Article R3120-2 du Code des transports** (à vérifier — Uber cite "Arrêté du 6 août 2025", à confirmer que c'est la version la plus à jour)
- Nom + adresse + SIRET + tél de l'exploitant
- N° eVTC du conducteur
- Identité du conducteur
- Plaque du véhicule
- Identité du passager + téléphone
- Date/heure de réservation
- Date/heure de prise en charge
- Lieu de prise en charge
- (Optionnel mais recommandé) Destination

### Features qui battent BonVTC

1. **Stats fonctionnelles dès le jour 1**
   - CA mensuel / annuel
   - Nombre de courses
   - Prix moyen par course
   - Top 5 clients (CA et fréquence)
   - Top 5 destinations
2. **Répertoire clients fonctionnel dès le jour 1**
   - Liste avec recherche
   - Détail client : historique courses, CA total, dernière course, prochaine course récurrente
3. **Confirmation SMS/email pré-rempli au client**
   - Intent Android natif (`smsto:` ou `mailto:`)
   - Choix langue FR/EN au moment de l'envoi
   - Texte type : "Bonjour [Nom], votre course est confirmée pour le [date] à [heure], prise en charge au [lieu]. À bientôt, [Chauffeur]."
4. **Mode hors-ligne réel**
   - Tout fonctionne sans 4G
   - Stockage Room local
5. **PDF moderne et lisible**
   - Layout propre (vs BonVTC très basique)
   - Toutes les mentions légales en pied de page

### Features hors-scope (pour rester focus)

- Page web société (BonVTC offre `bonvtc.fr/[nom]`) — casse 100 % local
- Annuaire de VTC — out of scope
- Intégration Google Maps API pour distance auto — complexe + payant à grande échelle
- Synchronisation cloud / multi-device — casse 100 % local
- Système de parrainage — pas notre business model

---

## 4. Phases d'exécution

### Phase 0 — Décisions stratégiques (à valider avant tout)
- [ ] Nom du produit final : **VTCfact** (par défaut, à valider)
- [ ] Nom du repo GitHub : `vtcfact-android`
- [ ] Domaine : `vtcfact.fr` ou `.com` (à vérifier dispo Gandi)
- [ ] Modèle économique : **gratuit dès le départ** (BonVTC est gratuit aussi, donc pas d'argument prix)
- [ ] Choisir 2-3 VTC beta testeurs parmi tes contacts

### Phase 1 — Setup technique du fork (1 jour)
- [ ] Créer le repo `karimlancien/vtcfact-android` (public)
- [ ] Cloner snapfacture-android comme base
- [ ] Renommer package Kotlin `com.snapfacture` → `com.vtcfact`
- [ ] Renommer applicationId → `com.vtcfact`
- [ ] Renommer DB filename → `vtcfact.db`
- [ ] Renommer classes internes `Snapfacture*` → `VTCfact*`
- [ ] Update CI workflows
- [ ] Reset des fichiers persistants (README, CLAUDE.md, ROADMAP)
- [ ] Supprimer ce qui n'a pas de sens VTC : catalogue produits artisan, données démo Plomberie Saadi, captures Snapfacture

### Phase 2 — Modèle de données (1 jour)
- [ ] Créer entity `BonEntity` avec champs ci-dessus
- [ ] Étendre `CompanyEntity` avec eVTC + RCS + RM + capital + mentions libres + forme juridique
- [ ] Optionnel : créer `VehicleEntity` (multi-véhicules)
- [ ] Migration Room v1 → v2 (ou reset clean v1)
- [ ] Adapter `InvoiceEntity` : ajouter `linkedBonId`

### Phase 3 — Logique métier (1 jour)
- [ ] TVA 10 % par défaut (CGI art. 279-b quinquies)
- [ ] Mode franchise TVA si auto-entrepreneur (< 39 100 € CA)
- [ ] Workflow bon → facture en 1 tap
- [ ] PDF du bon (nouveau template avec mentions Arrêté 1986/1993)
- [ ] PDF de la facture (template existant Snapfacture adapté)
- [ ] Snapshot des infos chauffeur/véhicule à l'émission

### Phase 4 — UI/UX (2-3 jours)
- [ ] Écran de création de bon (3 clics, géoloc, pré-remplissage)
- [ ] Liste des bons (filtres : à effectuer, complétés, facturés)
- [ ] Détail d'un bon avec bouton "Convertir en facture"
- [ ] Liste des factures (réutilisée de Snapfacture)
- [ ] Stats VTC (CA, courses, top clients, top destinations)
- [ ] Répertoire clients (liste + détail historique)
- [ ] Onboarding adapté VTC
- [ ] Bons récurrents (sélection jours semaine)
- [ ] Bouton "Confirmer au client" → SMS/email pré-rempli FR/EN

### Phase 5 — Branding visuel (0.5 jour)
- [ ] Icône de l'app (voiture stylisée + document ?)
- [ ] Palette de couleurs différente de Snapfacture (suggestion : noir + accent rouge ou vert)
- [ ] Wording : remplacer toutes occurrences "facture / client / artisan" par "course / passager / chauffeur" où pertinent
- [ ] Splash screen + Welcome screen avec hook VTC

### Phase 6 — Conformité légale (0.5 jour + vérification externe)
- [ ] Vérifier les mentions exactes obligatoires avec un VTC expérimenté ou un expert-comptable transport
- [ ] Confirmer **Article R3120-2** du Code des transports + Arrêté du 6 août 2025 (le plus récent vu sur les bons Uber)
- [ ] Durée légale de conservation (5 ans pour bons + factures)
- [ ] Mentions B2B (SIRET + pénalités) déjà OK depuis Snapfacture

### Phase 7 — Tests utilisateurs (1-2 semaines)
- [ ] Installation APK chez 2-3 VTC beta
- [ ] Faire 1 vraie course complète (bon → effectuer → facturer → envoyer)
- [ ] Récolter feedback en 1 semaine
- [ ] Itérer sur les 5 points les plus douloureux
- [ ] 2e tour de feedback en semaine 3

### Phase 8 — Distribution (0.5 jour)
- [ ] Landing page VTCfact (forkée de Snapfacture, adaptée VTC)
- [ ] Captures d'écran réelles : bon + facture + liste + stats
- [ ] Demo data : 1 bon en cours + 1 facture émise (style "Saadi Transport Premium")
- [ ] CI publie l'APK à chaque push
- [ ] Stratégie de partage :
  - Forums : forum-vtc.com, vtc-paris.fr
  - Groupes Facebook "VTC Indépendants France"
  - DMs à des comptes Insta VTC influents
  - Bouche-à-oreille via tes 2-3 beta

### Phase 9 — Monétisation (à décider après validation)
À ne traiter qu'**après confirmation** par les beta que le produit est utilisé sérieusement.

- [ ] Choisir modèle : gratuit à vie ? freemium ? PRO pour Factur-X / PDP 2027 ?
- [ ] Si payant : Stripe ou Apple Pay / Google Pay
- [ ] Conditions générales si payant

### Phase 10 — Mesure de succès
- **KPIs J+30** : installs, bons générés, factures émises, retours utilisateurs
- **KPIs J+60** : utilisateurs actifs (>5 bons), taux conversion bon → facture
- **KPIs J+90** : si payant, taux conversion gratuit → payant
- **Décision** : continuer / pivoter / abandonner sur base de chiffres réels

---

## 5. Questions en attente (envoyées au contact VTC)

1. **Tu fais combien de courses sur plateforme (Uber/Bolt) vs courses directes ?**
2. **Tes courses directes sont des particuliers ou des sociétés ?**
3. **Tu fais combien de courses RÉCURRENTES par mois ?**
4. **Tu utilises BonVTC ou autre chose ? Si oui, qu'est-ce qui te gonfle le plus ?**
5. **À quel moment dans la course tu donnes le bon au client ?**

Les réponses orientent les priorités MVP. Mettre à jour ce document dès réception.

---

## 6. Effort total estimé

- **Setup + dev MVP** : 4-5 jours de travail concentré
- **Tests utilisateurs + itérations** : 2-3 semaines
- **MVP testable** : 3-4 semaines après go
- **Validation commerciale** : 2 mois après lancement

---

## 7. Risques identifiés

| Risque | Probabilité | Mitigation |
|---|---|---|
| BonVTC réagit (ajoute stats + clients) | Moyenne | On a 3-4 mois d'avance, native reste un avantage durable |
| Mentions légales mal interprétées | Faible | Faire valider par un VTC expérimenté + expert-comptable |
| Pas de traction (< 5 utilisateurs actifs à J+60) | Moyenne | Abandonner ou pivoter, ne pas s'entêter |
| Concurrence d'un nouveau player payant | Faible | Notre gratuité + open source = barrière |
| Réforme e-facturation B2B 2027 change la donne | Élevée | Plan Factur-X / PDP déjà en roadmap Snapfacture, applicable à VTCfact |

---

## 8. Réutilisation depuis Snapfacture

Ce qui se reprend tel quel :
- Architecture MVVM + Compose + Hilt + Room
- `CountryProfile` (France/US — US peut servir pour limos US ?)
- `Money.kt`
- PDF generator (à adapter pour le bon)
- Backup SAF + restauration
- Verrouillage biométrique
- Anti-fraude SHA-256 (loi française)
- Numérotation chronologique sans rupture
- Mentions B2B (SIRET client + L441-10 + D441-5)
- Stats de base (à enrichir avec champs VTC)
- I18n FR/EN
- CI build APK

Ce qu'on retire :
- Catalogue produits (un VTC n'a pas de catalogue, juste des courses)
- Logique "service à domicile" (toutes les courses sont à domicile par nature)
- Données démo Plomberie Saadi (créer "Saadi Transport Premium" à la place)
