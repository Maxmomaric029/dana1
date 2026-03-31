# PhantomOps — WebView APK

Dark ops tool UI · WebView Android app · Auto-compiled via GitHub Actions.

## Stack
- Pure HTML/CSS/JS frontend (no frameworks)
- Android WebView wrapper (Java)
- GitHub Actions CI → APK artifact

## Setup

### 1. Upload to GitHub
```
git init
git add .
git commit -m "init: PhantomOps"
git remote add origin https://github.com/TU_USUARIO/phantomops.git
git push -u origin main
```

### 2. GitHub Actions compila automático
Cada push a `main` dispara el workflow `.github/workflows/build.yml`.

### 3. Descargar el APK
- Ve a tu repo → **Actions** tab
- Abre el último workflow run
- En **Artifacts** → descarga `PhantomOps-debug`

## Estructura
```
phantomops/
├── .github/
│   └── workflows/
│       └── build.yml          ← CI pipeline
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── index.html     ← Tu UI aquí
│       ├── java/com/phantomops/
│       │   └── MainActivity.java
│       ├── res/values/
│       │   ├── strings.xml
│       │   └── styles.xml
│       ├── AndroidManifest.xml
│       └── build.gradle
├── build.gradle
├── settings.gradle
├── gradlew
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

## Personalizar la UI
Solo edita `app/src/main/assets/index.html` y pushea.
El APK se recompila solo en ~2-3 minutos.

## APK Info
- `minSdk` 21 (Android 5.0+)
- `targetSdk` 34
- Fullscreen · immersive mode
- Pantalla siempre encendida mientras corre
