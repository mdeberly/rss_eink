# Lecture RSS

Lecteur de flux RSS calme, pensé pour un usage personnel sur liseuse/téléphone E Ink
(Mudita Kompakt), qui **affiche la durée de lecture estimée de chaque article**.

Inspiré du fonctionnement de [News](https://github.com/sacicz/news) (sources choisies,
pas de compte, pas d'algorithme), réécrit de zéro.

## Durée de lecture

- Chaque article de la liste indique sa durée à droite de la ligne « source · heure »,
  par ex. `franceinfo – Monde · 14:31        4 min`.
- Beaucoup de flux ne contiennent qu'un résumé : l'application télécharge alors la page de
  l'article en arrière-plan, en extrait le texte principal et compte les mots.
  - `4 min` : durée calculée sur l'article complet ;
  - `~1 min` : durée calculée sur le résumé du flux (article complet inaccessible) ;
  - `… min` : calcul en cours.
- Calcul : nombre de mots ÷ vitesse de lecture (180, 230, 280 ou 350 mots/min, réglable),
  plus quelques secondes par image (12 s pour la première, puis une de moins par image, minimum 3 s)
  si l'affichage des images est activé. Les caractères chinois/japonais/coréens comptent pour ½ mot.
- Filtre par durée en haut de la liste : Tous · ≤ 3 min · 3–10 min · > 10 min.
- La ligne d'état donne le nombre d'articles et le temps total de lecture des articles non lus.
- Dans le lecteur : durée totale et nombre de mots en haut, et **temps restant** à côté du
  numéro de page (`2 / 6 · reste 3 min`).

## Autres fonctions

- **Aujourd'hui** : les articles récents de toutes les sources (doublons fusionnés), paginés
  sans défilement (boutons ▲ ▼, glissement, touches de volume).
- **Lecteur épuré** : texte sans menus ni publicités, mis en page en pages (colonnes),
  police avec ou sans empattements, 4 tailles, images en niveaux de gris ou masquées.
- **Enregistrés** : étoile dans l'article ou appui long dans la liste ; lisibles hors ligne.
- **Sources** : saisir l'adresse d'un site (le flux est trouvé automatiquement), partager une
  page depuis le navigateur vers l'application, ou choisir dans les suggestions.
- Hors ligne : les derniers articles restent affichés.

## Installer

Chaque push déclenche le workflow GitHub Actions **Build APK** :
télécharger l'artefact `lecture-rss-apk` depuis l'onglet *Actions*
(ou, pour un tag `v*`, depuis *Releases*), puis l'installer sur le Kompakt avec Mudita Center.

L'APK est signé avec `app/personal.keystore` (clé personnelle, mot de passe `android`) afin que
chaque nouvelle version s'installe par-dessus la précédente. Ce n'est pas une clé à protéger :
l'application n'est pas destinée au Play Store.

## Compiler soi-même

```
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest
```

Nécessite JDK 17 et le SDK Android (API 34).
