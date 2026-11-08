# Car HeadUnit Dock (Pour autoradio Android & ZLink)

Application conçue spécialement pour autoradios Android avec support complet de **ZLink / Android Auto**.
Elle ajoute une barre de contrôle nocturne permanente tout en bas de l'écran avec redimensionnement réel de l'affichage (*Display Inset / Overscan*), garantissant que **ZLink et les applications GPS ne sont jamais masqués**.

---

## 🎛️ Boutons intégrés (Bas de l'écran)

1. **Retour (◀) :** Exécute l'action Retour système sur toutes les applications.
2. **Accueil (⌂) :** Quitte ZLink / Android Auto instantanément en 1 clic pour revenir au menu d'origine de la voiture.
3. **Mute (🔇) :** Coupe et rétablit le son instantanément avec retour visuel rouge.
4. **Volume Moins (−) :** Clic simple ou appui continu pour baisser rapidement.
5. **Volume Plus (+) :** Zone tactile élargie avec halo lumineux ambré pour un repérage immédiat de nuit.

---

## 🚀 Installation & Première configuration

### Étape 1 : Compiler ou installer l'APK
1. Ouvrez ce dossier dans **Android Studio** ou lancez `./gradlew assembleDebug`.
2. Copiez le fichier `app-debug.apk` généré sur une **clé USB**.
3. Branchez la clé USB sur votre autoradio et installez l'APK via le gestionnaire de fichiers (*File Manager*).

### Étape 2 : Activer les autorisations (Depuis l'application)
1. Ouvrez l'application **Car HeadUnit Dock** sur l'autoradio.
2. Cliquez sur **Activer** pour la permission **Affichage au-dessus des applications**.
3. Cliquez sur **Activer** pour le **Service d'Accessibilité** (indispensable pour les boutons Retour et Home).

### Étape 3 : Activer le redimensionnement d'écran (Méthode 2)
- Si votre autoradio est rooté (cas classique de nombreuses tablettes chinoises FYT / Topway / UIS7862) :
  - Cliquez simplement sur le bouton **"Activer Inset (52px)"** directement dans l'application !
- Si votre autoradio n'est pas rooté :
  - Connectez-vous une seule fois en ADB (via Wi-Fi ou câble USB) et tapez :
    ```bash
    adb shell wm overscan 0,0,0,52
    ```
  - *(Pour réinitialiser à tout moment si besoin : `adb shell wm overscan reset`)*.

### Étape 4 : Réglage indispensable dans ZLink
1. Ouvrez **ZLink / Zlink5** sur votre autoradio.
2. Allez dans les paramètres de ZLink (icône engrenage).
3. Assurez-vous que l'option **"Full Screen"** est désactivée (ou activez **"Keep Navigation Bar" / "Conserver la barre"**).
4. Connectez votre téléphone : **Android Auto se calera automatiquement au millimètre au-dessus de votre nouvelle barre** !
