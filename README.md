# Salatuk - صلاتك

Application de prière pour usage personnel — horaires, Qibla, rappels.

## Fonctionnalités

- 🕌 **Horaires des 5 prières + lever du soleil** — calculs astronomiques locaux
- 🧭 **Boussole Qibla** — direction de la Kaaba en temps réel
- ⏱️ **Compte à rebours** — temps restant avant la prochaine prière
- 📍 **Géolocalisation automatique** — détection de la ville et du fuseau horaire
- 🌙 **12 méthodes de calcul** — Habous (Maroc), MWL, ISNA, Umm al-Qura, Égypte, etc.
- ⚖️ **Écoles juridiques** — Shafi'i/Maliki et Hanafi pour le Asr
- 🌗 **Mode sombre/clair** automatique

## Méthodes de calcul

| Méthode | Fajr | Isha |
|---------|------|------|
| Habous (Maroc) | 19° | 17° |
| Ligue Mondiale | 18° | 17° |
| ISNA | 15° | 15° |
| Égypte | 19.5° | 17.5° |
| Umm al-Qura | 18.5° | 90 min |
| Diyanet (Turquie) | 18° | 17° |
| UOIF (France) | 12° | 12° |

## Contexte

Architecture et logique inspirées de l'analyse de l'application Salatuk originale via [REA](https://github.com/morluto/rea) (reverse engineering agent). Les calculs astronomiques sont implémentés de manière indépendante à partir des formules standard de position solaire.

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

## Licence

Usage personnel.
