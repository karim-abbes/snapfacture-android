# spike-assistant — harnais d'évaluation phase 0

Mini-app **jetable** (tâches 0.1–0.3 de `docs/DESIGN-ASSISTANT-IA.md`) pour décider go/no-go
sur l'assistant IA local. Elle n'est **jamais distribuée aux utilisateurs** : c'est un banc
d'essai qui mesure si un Gemma local comprend assez bien le français d'un artisan pour
piloter les 9 tools du catalogue cible (§5.1 du design).

## Ce que fait l'app

1. Charge un modèle `.task` / `.litertlm` (API LLM Inference + SDK Function Calling,
   décodage contraint Gemma activé).
2. Rejoue les ~64 requêtes de `assets/eval_cases.json` (43 FR, 21 EN) : lecture,
   propositions de facture/devis/avoir, ambiguïtés, pièges.
3. Compare chaque appel de fonction produit au résultat attendu (matching tolérant :
   accents, virgule/point, `1.5` == `1,50`, préfixe `~` = « contient »).
4. Affiche le score par catégorie + latence médiane/p90 + mémoire, et écrit un rapport
   JSON dans `Android/data/com.snapfacture.spike/files/`.

## Protocole (tâche 0.4)

### 1. Récupérer l'APK
Chaque push CI publie `spike-assistant-debug.apk` dans la release `latest-debug`.

### 2. Récupérer un modèle
Modèle recommandé pour la validation : **Gemma 4 E2B** (ou Gemma 3n E2B) au format LiteRT,
depuis la collection Hugging Face [litert-community](https://huggingface.co/litert-community)
(licence Gemma, compte HF requis). Pour un premier essai rapide sur un appareil modeste :
`Gemma3-1B-IT` quantisé q8 (~1 Go) — attendez-vous à des scores plus faibles.

> Si un fichier `.litertlm` refuse de charger, c'est probablement la version de
> `tasks-genai` (épinglée à 0.10.24, le couple documenté avec `localagents-fc` 0.1.0).
> Monter `tasksGenai` dans `gradle/libs.versions.toml` est le premier bouton à tourner.

### 3. Pousser le modèle sur le téléphone
Deux options :
- **adb** : `adb shell mkdir -p /data/local/tmp/llm && adb push model.task /data/local/tmp/llm/model.task`
  (chemin par défaut de l'app) ;
- **sans PC** : télécharger le fichier avec le navigateur du téléphone puis bouton
  « Importer… » dans l'app (copie dans le stockage privé — prévoir 2× la taille du modèle).

### 4. Lancer
Charger (GPU d'abord ; en cas d'échec, désactiver le switch → CPU), puis « Éval FR »,
puis « Tout ». Faire les mesures sur **2 appareils** : un milieu de gamme (~4-6 Go RAM)
et un récent. Noter aussi le ressenti : latence acceptable au comptoir ?

### 5. Récupérer le rapport
Bouton « Copier le rapport JSON », ou
`adb pull /sdcard/Android/data/com.snapfacture.spike/files/`.

## Grille go/no-go (tâche 0.5)

| Critère | Seuil |
|---|---|
| Lecture (`read`) | **≥ 85 %** |
| Propositions (`propose`) | **≥ 75 %** |
| Latence médiane | ≤ ~8 s sur milieu de gamme (jugement) |
| Pièges (`trap`) | informatif — un score faible ici = prompt système à durcir, pas un no-go |

- **Go** : les deux seuils tenus sur au moins un appareil réaliste → phase 1.
- **Go avec réserves** : seuils tenus seulement sur flagship → phase 1 avec gating RAM strict.
- **No-go** : consigner les scores dans `docs/DESIGN-ASSISTANT-IA.md` et arrêter là
  (l'alternative « parseur de commandes » redevient sur la table).

## Sortie de phase 0

Quel que soit le verdict, reporter les chiffres dans `docs/DESIGN-ASSISTANT-IA.md`
(section 10, tâche 0.5) puis **supprimer ce module** (`settings.gradle.kts` + dossier)
avant la phase 1 — c'est un spike, pas un produit.
