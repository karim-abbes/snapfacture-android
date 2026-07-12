# Assistant IA local — plan d'intégration (design, pas d'implémentation)

> Document d'architecture, juillet 2026. Objectif : permettre à l'utilisateur d'interagir avec
> Snapfacture en langage naturel (texte d'abord, voix ensuite) via un LLM 100 % local
> (famille Gemma, runtime LiteRT-LM / Google AI Edge), sans jamais laisser le modèle
> toucher aux données.

## 0. Avis préalable — est-ce une bonne idée ?

**Oui, mais seulement sous une forme précise.** Trois réserves honnêtes avant tout :

1. **Le clavier bat souvent l'IA.** Créer une facture aujourd'hui = 3 taps (client récent →
   produit → Émettre). Taper « fais une facture de deux batteries pour Dupont » est plus
   *lent*. La vraie valeur est ailleurs : **la voix en mobilité** (artisan au volant, mains
   prises) et **l'interrogation** (« combien de TVA je dois ce trimestre ? », « Dupont m'a
   payé combien cette année ? »). Le plan est donc construit lecture-d'abord, voix en cible.
2. **Le poids.** L'app fait ~19 Mo. Un modèle utilisable pèse de ~300 Mo (FunctionGemma
   270M quantisé) à ~3 Go (Gemma 3n E2B), et le runtime d'inférence ajoute des dizaines de
   Mo de code natif à l'APK. Imposer ça à tous les utilisateurs contredirait le
   minimalisme → l'assistant doit être **strictement opt-in**, idéalement dans un APK
   séparé (flavor), et le modèle importé par l'utilisateur.
3. **La qualité en français des petits modèles n'est pas garantie.** D'où une **phase 0 de
   validation avec critère go/no-go explicite** avant d'écrire la moindre ligne de code
   produit.

Si ces trois contraintes sont acceptées, la fonctionnalité est cohérente avec l'ADN du
produit : c'est même l'une des rares apps du marché qui peut promettre « un assistant IA
qui ne voit jamais vos factures quitter votre téléphone ».

---

## 1. État de l'art vérifié (juillet 2026)

Éléments vérifiés en ligne, car l'écosystème bouge vite :

- **LiteRT-LM** est le framework d'inférence on-device de Google (production : Chrome,
  ChromeOS, Pixel Watch). Depuis la v0.9 (2026) il intègre nativement le **function
  calling avec décodage contraint** — la sortie du modèle est *forcée* par la grammaire à
  être un appel de fonction syntaxiquement valide, ce qui élimine une classe entière
  d'erreurs de parsing.
- **Google AI Edge Function Calling SDK** (`com.google.ai.edge.localagents:localagents-fc`,
  v0.1.x) au-dessus de l'API LLM Inference (`com.google.mediapipe:tasks-genai`) : API
  Kotlin `FunctionDeclaration` / `Tool` / `GenerativeModel` / `ChatSession`, formatter
  Gemma fourni, décodage contraint supporté **pour les modèles Gemma uniquement**
  (`ConstraintOptions` + `enableConstraint`). Boucle : `hasFunctionCall()` → exécution côté
  app → `FunctionResponse` renvoyée au modèle.
- **FunctionGemma** (déc. 2025) : Gemma 3 270M spécialisé « langage naturel → appel de
  fonction ». Minuscule (~300 Mo quantisé), tokenizer 256k multilingue. **Mais** : 58 %
  de précision en prompt seul, 85 % après fine-tuning sur le domaine — inutilisable sans
  fine-tuning, et faible en conversation générale.
- **Gemma 3n puis Gemma 4 E2B / E4B** (génération courante, mai 2026) : généralistes
  efficaces pour mobile, format `.litertlm`, ~52 tok/s en décodage GPU sur Android pour
  E2B. E2B ≈ 3 Go sur disque, ~3 Go de RAM requis en usage.
- Les modèles sont **trop gros pour être embarqués dans l'APK** ; la doc Google prévoit un
  téléchargement à l'exécution — ce qui entre en collision frontale avec notre
  « zéro permission réseau » (voir §6.3).

---

## 2. Architecture cible

Principe cardinal, non négociable : **le LLM est un interprète d'intention, jamais un
acteur**. Il ne lit la base qu'à travers des tools de lecture exécutés par du code Kotlin,
et il n'écrit **rien** — ses « intentions d'écriture » produisent un brouillon typé que
seul un tap humain sur un bouton natif transforme en écriture, via les repositories
existants (transaction, numérotation sans rupture, snapshots, chaîne d'audit intacts).

```
                        ┌────────────────────────────────────────────┐
                        │                UI (Compose)                 │
                        │  AssistantScreen ── AssistantViewModel      │
                        │       │  affiche messages + CARTES natives  │
                        │       │  (brouillon facture → bouton        │
                        │       │   « Émettre » = flux existant)      │
                        └───────┼────────────────────────────────────┘
                                │ AssistantEvent / AssistantUiState
                ┌───────────────▼───────────────────────────┐
                │        assistant/AssistantOrchestrator     │
                │  boucle de conversation :                  │
                │  prompt système + historique → engine      │
                │  → FunctionCall ? ──┐                      │
                │                     │                      │
                │   tool LECTURE ─────┤ tool PROPOSITION     │
                │   exécute, renvoie  │ N'EXÉCUTE PAS :      │
                │   FunctionResponse  │ valide, construit    │
                │   au modèle         │ un Draft typé,       │
                │                     │ termine le tour      │
                └───────┬─────────────┴──────────┬───────────┘
                        │                        │
        ┌───────────────▼──────────┐   ┌─────────▼──────────────┐
        │ assistant/ToolRegistry   │   │ Drafts typés (Kotlin)  │
        │ + AssistantToolExecutor  │   │ InvoiceDraft, Quote-   │
        │ (validation stricte des  │   │ Draft, CreditDraft     │
        │  arguments : Money/      │   └─────────┬──────────────┘
        │  Quantity/TaxRate.parse) │             │ tap utilisateur
        └───────┬──────────────────┘             │
                │ lecture seule          ┌───────▼──────────────────┐
        ┌───────▼──────────────────┐    │ Repositories EXISTANTS    │
        │ Repositories existants   │    │ InvoiceRepository.issue() │
        │ (observeAll, search,     │    │ QuoteRepository.create()  │
        │  vatBreakdown, revenue…) │    │ issueCredit()             │
        └──────────────────────────┘    │ (transaction + audit +    │
                                        │  numérotation intactes)   │
                                        └───────────────────────────┘
        ┌──────────────────────────────────────────────────────────┐
        │ assistant/engine/AssistantEngine (interface)              │
        │   └ LiteRtAssistantEngine : tasks-genai + localagents-fc  │
        │     (GemmaFormatter, ConstraintOptions = décodage         │
        │      contraint, session GPU/CPU)                          │
        │ assistant/model/ModelManager : import SAF, SHA-256,       │
        │   filesDir/models/, états Missing/Verifying/Ready/…       │
        └──────────────────────────────────────────────────────────┘
```

Trois barrières successives entre le modèle et les données :

| Barrière | Mécanisme |
|---|---|
| 1. Grammaire | Décodage contraint : le modèle ne peut émettre qu'un appel conforme aux `FunctionDeclaration` |
| 2. Validation | `AssistantToolExecutor` re-parse chaque argument avec `Money`/`Quantity`/`TaxRate`/résolution client ; tout argument invalide → erreur renvoyée au modèle, jamais d'exécution partielle |
| 3. Humain | Aucun tool d'écriture n'existe : les intentions d'écriture produisent un `Draft` affiché en carte native ; l'écriture ne part que d'un tap sur un bouton Compose, dans le flux transactionnel existant |

---

## 3. Composants à ajouter

Nouveau package `com.snapfacture.assistant` (au même niveau que `core`, `data`, `ui`) :

### 3.1 `assistant/engine/`
- **`AssistantEngine`** (interface) — contrat minimal :
  `suspend fun startSession(tools: List<ToolSpec>, systemPrompt: String): AssistantSession`,
  `AssistantSession.send(text | toolResponse): EngineReply` où
  `EngineReply = Text(String) | ToolCall(name, argsJson) | Error`.
  Le reste de l'app ne voit jamais les types MediaPipe → runtime remplaçable, et testable
  avec un `FakeAssistantEngine` en JVM.
- **`LiteRtAssistantEngine`** — implémentation : `LlmInference` (tasks-genai) +
  `GenerativeModel`/`ChatSession` (localagents-fc), `GemmaFormatter`, décodage contraint
  activé, backend GPU avec repli CPU, libération mémoire agressive (`close()` quand
  l'écran assistant quitte la composition — un modèle chargé = 2-3 Go de RAM).

### 3.2 `assistant/model/`
- **`ModelManager`** — cycle de vie du fichier modèle :
  import via SAF (`ACTION_OPEN_DOCUMENT`, même pattern que `BackupManager`), copie en
  streaming vers `filesDir/models/`, vérification SHA-256 contre un petit manifeste
  embarqué des modèles supportés, exposition d'un
  `StateFlow<ModelState>` : `Missing / Importing(progress) / Verifying / Ready(info) /
  Unsupported / InsufficientRam`. Gating RAM via `ActivityManager` (seuil par modèle).
  Suppression du modèle depuis Réglages (récupérer les Go).

### 3.3 `assistant/tools/`
- **`ToolSpec`** — description déclarative d'un tool (nom, description localisée pour le
  prompt, schéma des paramètres) convertie en `FunctionDeclaration` par l'engine.
- **`ToolRegistry`** — le catalogue (voir §5), séparé en `readTools` et `proposalTools`.
- **`AssistantToolExecutor`** — dispatch + validation. Injecte les repositories existants.
  Toute conversion texte→entier passe par les parseurs existants (`Quantity.parse`,
  `TaxRate.parsePercentToBp`, parseur de montant réutilisant la logique CSV FR/US).
  Les montants circulent dans le JSON **en chaînes décimales** (« 120,50 »), jamais en
  flottants, et le modèle ne calcule jamais rien : tous les totaux viennent de `Money`.

### 3.4 `assistant/`
- **`AssistantOrchestrator`** — la boucle : construit le prompt système (langue, date du
  jour, devise et taux par défaut depuis `CountryProfile`, consignes), envoie le message,
  exécute les tools de lecture (max N itérations, garde-fou anti-boucle), s'arrête sur un
  tool de proposition en produisant un `AssistantDraft` (sealed :
  `InvoiceDraft(IssueInvoiceInput-like)`, `QuoteDraft`, `CreditDraft`). Aucune dépendance
  Android → testable en JVM pur avec le fake engine.
- **`AssistantDraftHolder`** — `@Singleton` en mémoire, passe le brouillon à
  `CreateInvoiceViewModel` quand l'utilisateur choisit « Modifier avant émission »
  (évite de sérialiser un objet complexe dans un argument de navigation).

### 3.5 `ui/assistant/`
- **`AssistantScreen`** — un seul écran : fil de messages, champ texte, et **cartes
  natives** pour les résultats structurés (liste de factures cliquable, récap TVA,
  brouillon de facture avec lignes + total calculé par `Money` + boutons
  « Émettre » / « Modifier » / « Annuler »). État `ModelState.Missing` → écran
  d'installation du modèle (instructions + bouton d'import SAF), pas de chat fantôme.
- **`AssistantViewModel`** — relie orchestrateur et écran ; l'action « Émettre » appelle
  `InvoiceRepository.issue()` exactement comme `CreateInvoiceViewModel.issue()` le fait
  (même chemin PDF best-effort ensuite). Conversation **éphémère, jamais persistée**
  (vie du ViewModel) — choix délibéré : zéro impact base, zéro question de confidentialité
  nouvelle, cohérent avec PRIVACY.md.

### 3.6 `assistant/di/AssistantModule`
Module Hilt fournissant `AssistantEngine` (et rien d'autre — le reste est
constructor-injected comme le pattern actuel).

---

## 4. Composants existants à modifier

Volontairement peu nombreux :

| Fichier | Modification |
|---|---|
| `ui/navigation/Routes.kt` | + route `assistant` |
| `ui/SnapfactureRoot.kt` | + destination ; point d'entrée : **icône dans la barre du haut** des 3 écrans top-level (pas de 4e onglet — la barre basse reste Factures/Devis/Stats) ; icône absente si `ModelState != Ready` et non-flavor IA |
| `ui/invoices/create/CreateInvoiceViewModel.kt` | + `fun applyDraft(draft: InvoiceDraft)` (pré-remplissage client/panier/paiement depuis `AssistantDraftHolder`) — aucune autre logique touchée |
| `ui/settings/SettingsScreen.kt` + VM | + section « Assistant IA » : état du modèle, import, suppression, lien vie privée |
| `data/repository/InvoiceRepository.kt` | + 1-2 lectures ciblées si besoin (`findByNumber(n)`) — signatures existantes inchangées |
| `app/build.gradle.kts` | + **product flavors `standard` / `assistant`** (voir §8.1) + dépendances `tasks-genai`, `localagents-fc` dans le flavor `assistant` uniquement |
| `.github/workflows/*.yml` | build des 2 flavors, publication des 2 APK |
| `PRIVACY.md`, `README` | mention de l'assistant : modèle local, aucune conversation stockée, aucune donnée sortante |

**Rien d'autre ne bouge** : ni `AppDatabase` (reste v6), ni les DAOs, ni `Money`/
`Quantity`/`TaxRate`, ni le flux d'émission, ni la chaîne d'audit.

---

## 5. Stratégie tools / function calling

### 5.1 Catalogue v1 — volontairement petit (8 tools)

Un LLM de cette taille choisit d'autant mieux que le menu est court. On refuse le réflexe
« un tool par méthode de repository ».

**Lecture (exécutés immédiatement, résultat renvoyé au modèle) :**

| Tool | Paramètres | Implémentation |
|---|---|---|
| `search_clients` | `query` | `ClientRepository.search` |
| `list_invoices` | `period?`, `client_name?`, `type?` | `InvoiceRepository.observeAll` + filtre |
| `get_invoice` | `number` | `findByNumber` |
| `get_revenue` | `period` (`month/quarter/year` + offset) | `observeRevenueSince` / agrégats Stats |
| `get_tax_summary` | `quarter`, `year` | `InvoiceDao.vatBreakdown` |
| `list_products` | — | `ProductRepository.observeActive` |

**Proposition (jamais exécutés — terminent le tour avec un Draft) :**

| Tool | Paramètres | Produit |
|---|---|---|
| `propose_invoice` | `client_name`, `lines[{description, quantity, unit_price, vat_rate?}]`, `payment_method?` | `InvoiceDraft` → carte de confirmation |
| `propose_quote` | idem sans paiement | `QuoteDraft` |
| `propose_credit_note` | `invoice_number`, `reason?` | `CreditDraft` |

### 5.2 Règles du contrat

- **Montants et quantités en chaînes décimales** dans le JSON ; parsing exact côté Kotlin ;
  taux de TVA optionnel → défaut `CompanyEntity.defaultTaxBp` ; le modèle a interdiction
  (prompt) et impossibilité (l'app recalcule tout) de faire de l'arithmétique.
- **Ambiguïté = question, pas invention** : `propose_invoice` avec un client inconnu ne
  crée rien — l'executor renvoie les candidats proches (`search_clients`) et le modèle
  doit demander. Client réellement nouveau : le Draft porte `newClient = true`, la carte
  l'affiche clairement (« Nouveau client sera créé »), et c'est `upsertByName` — le chemin
  existant — qui tranchera à l'émission.
- **Erreurs de validation renvoyées au modèle** en langage clair (« taux 19,6 % inconnu ;
  taux valides : … ») avec 2 tentatives max, puis message d'excuse et main rendue à l'UI.
- **Décodage contraint activé** dès qu'on attend un tool ; texte libre autorisé pour les
  réponses finales de synthèse.
- **Prompt système localisé** (FR/EN selon la locale du profil pays), incluant date du
  jour, devise, libellé de taxe (`taxLabel`), et la consigne d'identité (« tu ne peux rien
  modifier ; tu proposes, l'utilisateur dispose »).

### 5.3 Ce que ça garantit

Le pire cas d'un modèle qui hallucine complètement : une carte de brouillon absurde que
l'utilisateur annule d'un tap. La numérotation, l'immuabilité, les snapshots et la chaîne
SHA-256 sont hors de portée du modèle **par construction**, pas par politesse de prompt.

---

## 6. Impacts

### 6.1 Base de données : **aucun**
Schéma Room reste **v6**, aucune migration. Les conversations ne sont pas persistées
(choix assumé : simplicité + confidentialité ; on pourra le revisiter si les utilisateurs
le demandent). Le manifeste des modèles supportés est un fichier embarqué, pas une table.

### 6.2 Repositories / use cases : **quasi nuls**
L'executor consomme l'API publique existante. Ajouts probables : `findByNumber`, peut-être
un agrégat « CA par client sur période » si `get_revenue` doit filtrer par client. Aucune
signature existante modifiée — les 27+ tests actuels ne bougent pas.

### 6.3 Le nœud : obtenir le modèle sans permission réseau
Le modèle (0,3–3 Go) ne peut pas être dans l'APK. Trois options :

| Option | Verdict |
|---|---|
| **A. Import manuel via SAF** : l'utilisateur télécharge le `.litertlm`/`.task` (Hugging Face) avec son navigateur, puis « Réglages → Assistant → Importer le modèle ». SHA-256 vérifié. | **Retenue.** Zéro permission ajoutée, PRIVACY.md reste vrai à la lettre, compatible F-Droid, et le pattern SAF existe déjà (backup). Friction réelle mais assumée pour une fonctionnalité power-user opt-in ; l'écran d'installation guide pas à pas. |
| B. Flavor avec `INTERNET` + téléchargeur intégré | Rejetée en v1 : brise l'argument marketing central (« zéro permission réseau »), ajoute du code réseau, de la reprise de téléchargement, etc. Réévaluable plus tard si la friction de A tue l'adoption. |
| C. Play Services AICore / Gemini Nano | Rejetée : dépend de Google Play (app distribuée par GitHub/F-Droid), parc d'appareils restreint, pas de contrôle du modèle. |

### 6.4 ViewModels
Un nouveau (`AssistantViewModel`), un enrichi (`CreateInvoiceViewModel.applyDraft`), zéro
modifié ailleurs.

### 6.5 Voix (phase ultérieure)
- **Entrée** : `SpeechRecognizer` système avec préférence hors-ligne + permission
  `RECORD_AUDIO` (déclarée **uniquement dans le flavor assistant**). Risque : sur les
  appareils sans services Google (cible F-Droid !), la reconnaissance hors-ligne peut être
  absente → détection à l'exécution, bouton micro masqué sinon. Alternative future :
  whisper.cpp tiny (~75 Mo) embarqué, mais c'est un second moteur natif à maintenir — pas
  en v1.
- **Sortie** : `TextToSpeech` système, optionnel.
- PRIVACY.md devra expliquer que l'audio est traité localement.

---

## 7. Risques techniques

| # | Risque | Gravité | Mitigation |
|---|---|---|---|
| 1 | **Français médiocre des petits modèles** (intentions mal comprises, tutoiement bancal) | Bloquant potentiel | Phase 0 : jeu d'éval de ~40 requêtes FR réalistes, critère go/no-go ≥ 85 % de tool-calls corrects sur les cas lecture |
| 2 | Hallucination d'arguments (prix, client) | Élevée | Triple barrière §2 ; jamais d'écriture sans tap humain |
| 3 | RAM / OOM sur appareils modestes (cible artisans ≠ flagships) | Élevée | Gating `ModelState.InsufficientRam` ; modèle libéré hors écran ; recommandation E2B ≥ 6 Go RAM |
| 4 | Latence (plusieurs secondes/réponse) | Moyenne | Streaming des tokens dans l'UI, indicateur clair, GPU par défaut |
| 5 | SDK jeune (`localagents-fc` en 0.1.x, API mouvante) | Moyenne | Interface `AssistantEngine` = unique point de contact ; montée de version localisée |
| 6 | Poids APK (+50–100 Mo de natif runtime) imposé à tous | Élevée | **Flavors `standard`/`assistant`** : l'APK standard reste ~19 Mo |
| 7 | F-Droid : binaires natifs précompilés de tasks-genai probablement refusés | Moyenne | F-Droid ne construit que le flavor `standard` ; flavor `assistant` via GitHub Releases uniquement |
| 8 | Corruption / mauvais fichier modèle importé | Faible | SHA-256 contre manifeste + validation de chargement avant `Ready` |
| 9 | Reconnaissance vocale absente hors Play Services | Moyenne | Détection runtime, micro masqué, texte toujours disponible |
| 10 | Dérive de scope (« le chat peut tout faire ») | Moyenne | Catalogue de tools fermé, revu à chaque ajout ; pas de tool « exécuter du SQL » évidemment |

---

## 8. Choix d'architecture — avantages / inconvénients

### 8.1 Deux flavors (`standard` / `assistant`) — retenu
- ✅ APK standard inchangé (~19 Mo), promesse minimaliste intacte ; F-Droid propre ;
  permission `RECORD_AUDIO` cantonnée au flavor IA.
- ❌ Deux APK à builder/publier (CI ×2), matrice de test élargie, code assistant derrière
  une interface no-op dans le flavor standard.
- Alternative rejetée : un seul APK avec tout dedans — plus simple côté CI mais +100 Mo
  pour 100 % des utilisateurs alors qu'une minorité activera l'assistant. Contraire au
  principe directeur.

### 8.2 Runtime : LLM Inference API + FC SDK (pile Google AI Edge) — retenu
- ✅ API Kotlin native, formatter Gemma fourni, **décodage contraint** (unique sur le
  marché on-device), maintenu par Google, format `.litertlm` aligné sur les releases
  officielles Gemma.
- ❌ SDK jeune (0.1.x), boîte relativement noire, binaires précompilés (souci F-Droid,
  géré par 8.1).
- Alternatives : **llama.cpp** (plus de modèles via GGUF, grammaires GBNF ≈ décodage
  contraint, mais intégration JNI à maintenir nous-mêmes et pas d'API function calling
  clé en main) ; **ONNX Runtime / MLC** (généralistes, encore plus d'assemblage manuel).
  Si la pile Google déçoit, l'interface `AssistantEngine` permet de basculer sans toucher
  ni aux tools ni à l'UI — c'est précisément sa raison d'être.

### 8.3 Modèle : Gemma 4 E2B (ou 3n E2B) d'abord, FunctionGemma fine-tuné comme optimisation — retenu
- ✅ E2B : le meilleur rapport compréhension française / function calling *sans
  fine-tuning* ; permet de valider le produit avant d'investir dans l'entraînement.
- ❌ ~3 Go à télécharger et ~3 Go de RAM — exclut les appareils bas de gamme en v1.
- FunctionGemma 270M (~300 Mo) est la cible séduisante à terme, **mais** 58 % de précision
  sans fine-tuning est inacceptable (une facture proposée fausse sur deux ?). Le
  fine-tuning sur notre catalogue de tools (dataset synthétique FR/EN) est un chantier ML
  distinct — phase 5, pas un prérequis.
- Le `ModelManager` est multi-modèles par construction (manifeste), donc ce choix n'est
  pas gravé dans le marbre.

### 8.4 Écritures = brouillon + confirmation native — retenu (non négociable)
- ✅ Respecte à la lettre « le LLM ne modifie jamais les données » ; l'utilisateur voit un
  récapitulatif calculé par `Money` avant tout engagement légal (numérotation !) ;
  réutilise le flux d'émission testé.
- ❌ Un tap de plus que « fais-le et c'est fait ». C'est un coût assumé : une facture émise
  est **immuable et numérotée sans rupture** — l'émettre sur une hallucination serait
  une faute produit et légale.

### 8.5 Conversations éphémères — retenu
- ✅ Zéro schéma, zéro migration, zéro question RGPD nouvelle, cohérent PRIVACY.md.
- ❌ Pas d'historique entre sessions. Acceptable : l'assistant est un outil, pas un journal.

---

## 9. Auto-challenge : existe-t-il plus simple ?

Examiné honnêtement avant de valider le plan :

1. **Parseur de commandes sans LLM** (« facture 120 dupont » → regex/grammaire).
   ~50 Ko, déterministe, instantané. Mais : ce n'est plus du langage naturel, c'est une
   syntaxe à apprendre — pire qu'un formulaire pour la cible ; et la voix (le vrai cas
   d'usage) produit du langage naturel, pas des commandes. **Rejeté**, mais il fixe la
   barre : si le LLM ne fait pas *nettement* mieux que ça en phase 0, on arrête.
2. **Classifieur d'intentions léger + slots** (petit modèle de classification, pas
   génératif). Plus léger, mais toute la difficulté (extraction des entités « deux
   batteries à 120 € pour Dupont ») reste entière, sans la souplesse conversationnelle.
   Complexité comparable, plafond bien plus bas. **Rejeté.**
3. **LLM cloud** — violerait le 100 % local, pilier du produit. **Rejeté sans débat.**
4. **Tout mettre dans l'APK unique** — voir 8.1. **Rejeté.**
5. **Laisser le LLM écrire via des tools d'écriture validés** (sans confirmation humaine).
   Techniquement défendable avec le décodage contraint + validation... mais une seule
   hallucination émettrait une facture légalement immuable. **Rejeté** — la carte de
   confirmation est le bon compromis simplicité/sûreté.

Conclusion du challenge : l'architecture retenue est déjà la version minimale qui
respecte les quatre contraintes de départ. Sa seule graisse potentielle est le flavor
split — supprimable si on accepte +100 Mo pour tous, ce qu'on refuse.

---

## 10. Roadmap d'implémentation

Chaque tâche est atomique (≈ un commit) ; les dépendances sont indiquées. Les phases 2+
ne démarrent que si la phase 0 est « go ».

### Phase 0 — Validation (spike jetable, hors app de prod)
> Livrable : décision go/no-go documentée. Aucun code mergé dans l'app.

| # | Tâche | Dépend de |
|---|---|---|
| 0.1 | Mini-app harnais (ou écran debug) : tasks-genai + localagents-fc + Gemma 4 E2B chargé depuis `/data/local/tmp` | — |
| 0.2 | Jeu d'évaluation : ~40 requêtes FR + ~20 EN réalistes (lecture, création, ambiguïtés, pièges) avec tool-call attendu | — |
| 0.3 | Déclarer les 8 tools du §5.1 dans le harnais, décodage contraint activé | 0.1 |
| 0.4 | Passer l'éval sur 2 appareils (milieu de gamme + flagship) ; mesurer précision, latence 1er token, RAM pic | 0.2, 0.3 |
| 0.5 | Décision : go (≥85 % lecture, ≥75 % proposition, latence acceptable) / no-go / go-avec-réserves — consignée dans ce document | 0.4 |

### Phase 1 — Socle (invisible pour l'utilisateur)
| # | Tâche | Dépend de |
|---|---|---|
| 1.1 | Flavors `standard`/`assistant` dans `build.gradle.kts` ; dépendances IA côté `assistant` ; CI build les 2 APK | 0.5 |
| 1.2 | `AssistantEngine` (interface) + `FakeAssistantEngine` de test | 0.5 |
| 1.3 | `LiteRtAssistantEngine` (flavor assistant) ; no-op côté standard | 1.1, 1.2 |
| 1.4 | `ModelManager` : import SAF streaming, SHA-256, manifeste, `StateFlow<ModelState>`, gating RAM, suppression | 1.1 |
| 1.5 | `AssistantModule` (Hilt) | 1.3 |
| 1.6 | Section Réglages → Assistant (état modèle, import guidé avec URL/QR, suppression) | 1.4 |
| 1.7 | Tests unitaires `ModelManager` (Robolectric : import, checksum KO, RAM insuffisante) | 1.4 |

### Phase 2 — Lecture seule (première valeur utilisateur)
| # | Tâche | Dépend de |
|---|---|---|
| 2.1 | `ToolSpec` + `ToolRegistry` (6 tools lecture) | 1.2 |
| 2.2 | `AssistantToolExecutor` lecture : dispatch, parsing strict, formatage résultats | 2.1 |
| 2.3 | `InvoiceRepository.findByNumber` (+ test) | — |
| 2.4 | `AssistantOrchestrator` : prompt système localisé, boucle lecture, garde-fou N itérations | 2.2 |
| 2.5 | Tests JVM orchestrateur avec `FakeAssistantEngine` (tool correct, args invalides→retry, boucle coupée) | 2.4 |
| 2.6 | `AssistantScreen` + `AssistantViewModel` : chat, streaming, cartes résultats (liste factures cliquable, récap TVA) | 2.4 |
| 2.7 | Route + icône d'entrée conditionnelle dans `SnapfactureRoot` | 2.6 |
| 2.8 | i18n FR/EN de tout l'écran + prompts | 2.6 |
| 2.9 | Libération mémoire du modèle hors écran (lifecycle) | 2.6 |

### Phase 3 — Propositions d'actions
| # | Tâche | Dépend de |
|---|---|---|
| 3.1 | Tools `propose_invoice/quote/credit_note` + validation (résolution client, parsing lignes) | 2.2 |
| 3.2 | `AssistantDraft` sealed + `AssistantDraftHolder` | 3.1 |
| 3.3 | Carte de confirmation Compose (lignes, totaux via `Money`, badge « nouveau client », boutons Émettre/Modifier/Annuler) | 3.2, 2.6 |
| 3.4 | « Émettre » → `InvoiceRepository.issue` / `QuoteRepository.create` / `issueCredit` + PDF best-effort (même séquence que les VMs existants) | 3.3 |
| 3.5 | `CreateInvoiceViewModel.applyDraft` + navigation « Modifier avant émission » | 3.2 |
| 3.6 | Tests Robolectric bout-en-bout : phrase simulée (fake engine scripté) → draft → issue → facture conforme + chaîne d'audit intacte | 3.4 |
| 3.7 | Mise à jour PRIVACY.md + README (section assistant) | 3.4 |

### Phase 4 — Voix
| # | Tâche | Dépend de |
|---|---|---|
| 4.1 | `RECORD_AUDIO` dans le manifest du flavor assistant uniquement | 3.x livré |
| 4.2 | Détection runtime de la reco hors-ligne ; bouton micro conditionnel | 4.1 |
| 4.3 | Intégration `SpeechRecognizer` (préférence offline) → champ texte pré-rempli, envoi manuel | 4.2 |
| 4.4 | (Option) lecture TTS de la réponse, désactivable | 4.3 |
| 4.5 | PRIVACY.md : traitement audio local | 4.3 |

### Phase 5 — Optimisation (si traction)
| # | Tâche | Dépend de |
|---|---|---|
| 5.1 | Dataset synthétique FR/EN (requête → tool-call) sur notre catalogue | phase 3 |
| 5.2 | Fine-tuning FunctionGemma 270M, éval vs phase 0, publication du modèle (HF, licence Gemma) | 5.1 |
| 5.3 | Entrée manifeste FunctionGemma dans `ModelManager` (seuils RAM abaissés) | 5.2 |
| 5.4 | Splits ABI pour l'APK assistant si le poids natif le justifie | phase 2 |

---

## Sources

- [LiteRT-LM Overview — Google AI Edge](https://developers.google.com/edge/litert-lm/overview)
- [Blazing fast on-device GenAI with LiteRT-LM — Google Developers Blog](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/)
- [AI Edge Function Calling guide for Android](https://developers.google.com/edge/mediapipe/solutions/genai/function_calling/android)
- [LLM Inference guide for Android](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android)
- [google-ai-edge/LiteRT-LM — GitHub](https://github.com/google-ai-edge/LiteRT-LM)
- [FunctionGemma — InfoQ](https://www.infoq.com/news/2026/01/functiongemma-edge-function-call/)
- [gemma-4-E2B-it-litert-lm — Hugging Face](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
