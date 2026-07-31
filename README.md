# JetGo

App de streaming (Xtream Codes / M3U) con **un solo APK** que se adapta a:
- Teléfonos y tablets Android
- Android TV / Google TV
- TV Box genéricos (Android normal)

Construida con Kotlin + Jetpack Compose + Media3 (ExoPlayer).

## Requisitos para compilar
1. **Android Studio** (Koala o más reciente) — https://developer.android.com/studio
2. JDK 17 (Android Studio ya lo incluye)
3. Conexión a internet la primera vez (para descargar Gradle y dependencias)

## Cómo abrir y compilar
1. Abre Android Studio → **Open** → selecciona la carpeta `JetGo`.
2. Espera a que sincronice Gradle (la primera vez tarda unos minutos).
3. Para generar el APK: menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.
4. Para firmarlo y publicarlo: **Build → Generate Signed Bundle / APK**.

## Cómo instalar en cada dispositivo

### Teléfono/tablet Android
- Transfiere el APK y ábrelo, o usa `adb install app-debug.apk` con USB debugging activado.

### Android TV / Google TV
- Con el TV en la misma red: `adb connect <IP_DEL_TV>:5555` y luego `adb install app-debug.apk`.
- La app aparecerá automáticamente en la fila de canales/apps porque el `AndroidManifest.xml`
  ya declara la categoría `LEANBACK_LAUNCHER`.

### TV Box genérico (Android normal)
- Igual que un teléfono: instala el APK directamente o vía un gestor de archivos/USB.

## Configuración de tu contenido (primera pantalla)
Al abrir la app por primera vez pide:
- **Xtream Codes**: host (ej. `http://tuservidor.com:8080`), usuario y contraseña.
- **o alternativamente** una URL directa de lista **M3U**.

Esto se guarda localmente (DataStore) para no volver a pedirlo.

## Estructura del proyecto
```
app/src/main/java/com/jetgo/tv/
├── data/
│   ├── model/         -> Modelos de datos (Channel, Category, etc.)
│   ├── remote/        -> Cliente Xtream Codes (Retrofit) + parser M3U
│   ├── local/         -> DataStore (persistencia de configuración)
│   └── repository/    -> Unifica Xtream/M3U en una sola fuente de datos
├── player/            -> PlayerManager (ExoPlayer/Media3, overlay de bitrate)
├── ui/
│   ├── theme/         -> Colores y tema oscuro
│   ├── components/    -> CategoryCard, PlayerPanel, PromoBanner
│   └── screens/       -> SetupScreen, HomeScreen, ChannelListScreen
└── MainActivity.kt    -> Navegación (Setup -> Home -> Lista de canales)
```

## Funcionalidades incluidas (100% implementado)
- Conexión Xtream Codes o lista M3U directa.
- Navegación en 2 pasos por categoría (Vivo/Serie/Película/Anime/Especial → subcategoría real
  del servidor → contenido), para no descargar el catálogo completo de golpe.
- Reproducción en vivo, VOD y series (resuelve automáticamente el primer episodio).
- **Favoritos**: botón de estrella en cada tarjeta, persistente entre sesiones (DataStore),
  con su propia pantalla accesible desde el ícono de estrella del home.
- **Búsqueda global**: ícono de lupa en el home. Carga el catálogo completo una sola vez
  (se cachea en memoria) y filtra en tiempo real mientras escribes.
- Anime/Especial se resuelven detectando categorías VOD cuyo nombre contenga esas palabras
  (es como la mayoría de paneles Xtream reales organizan ese contenido).

## Generar el APK usando GitHub (sin instalar Android Studio)

El proyecto ya incluye 2 workflows de **GitHub Actions** listos para usar en
`.github/workflows/`. Compilan el APK en la nube; tú solo subes el código y descargas
el resultado.

### 1) Subir el proyecto a GitHub
```bash
cd JetGo
git init
git add .
git commit -m "Proyecto inicial JetGo"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git push -u origin main
```

### 2) APK de prueba (debug) — automático en cada push
No requiere configuración. Apenas subas el código (o cualquier cambio a `main`):
1. Ve a la pestaña **Actions** de tu repositorio en GitHub.
2. Verás el workflow **"Build APK (debug)"** corriendo (tarda 3-5 minutos la primera vez).
3. Cuando termine (ícono verde ✓), entra al run → sección **Artifacts** al final de la página →
   descarga **JetGo-debug** (es un .zip que contiene `app-debug.apk`).
4. Ese APK ya se puede instalar directamente en cualquier teléfono, Android TV, Google TV
   o TV Box (activando "orígenes desconocidos" en el dispositivo).

También puedes lanzarlo manualmente sin hacer push: **Actions → Build APK (debug) →
Run workflow**.

### 3) APK de release firmado (opcional, para publicar/distribuir en serio)
Un APK debug funciona perfecto para probar, pero para distribuir oficialmente conviene
un APK release firmado. Pasos:

**a) Generar tu propia llave de firma** (una sola vez, en tu computadora, con el JDK instalado):
```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias jetgo
```
Te pedirá una contraseña para el keystore y otra para la clave (puedes usar la misma).
**Guarda `release.jks` en un lugar seguro** — si lo pierdes, no podrás actualizar la app
con el mismo APK en el futuro.

**b) Convertirlo a base64** (para pegarlo como secreto de GitHub):
```bash
base64 -w0 release.jks > release.jks.base64.txt   # Linux
base64 -i release.jks -o release.jks.base64.txt   # macOS
certutil -encode release.jks release.jks.base64.txt  # Windows
```

**c) Agregar 4 secretos en tu repositorio de GitHub**:
Ve a **Settings → Secrets and variables → Actions → New repository secret** y crea:
| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | contenido completo de `release.jks.base64.txt` |
| `KEYSTORE_PASSWORD` | la contraseña del keystore que elegiste |
| `KEY_ALIAS` | `jetgo` (o el alias que hayas usado) |
| `KEY_PASSWORD` | la contraseña de la clave que elegiste |

**d) Publicar una versión**:
```bash
git tag v1.0.0
git push origin v1.0.0
```
Esto dispara el workflow **"Build & Release APK (signed)"**, que compila, firma y publica
automáticamente el APK en la sección **Releases** de tu repositorio de GitHub, listo para
compartir el link de descarga con quien quieras.

> Si subes una etiqueta sin haber configurado los 4 secretos, el workflow igual compila
> el APK (para que veas que todo funciona) pero queda **sin firmar** y no se publica como
> Release — solo como artefacto descargable, con una advertencia visible en el log.


Se generó un logo real con Python/Pillow (`/mnt/user-data/.../make_logo.py` si quieres reeditarlo):
un triángulo de "play" en degradado ámbar→rojo sobre un fondo degradado azul-marino casi negro,
coherente con los colores ya usados en la app.

Incluye TODOS los formatos que Android necesita para que se vea nítido en cualquier dispositivo:
- **Ícono adaptativo** (`mipmap-*/ic_launcher_foreground.png` + `ic_launcher_background.png`,
  referenciados desde `mipmap-anydpi-v26/ic_launcher.xml`): usado en Android 8+ (teléfonos,
  tablets, Android TV, Google TV, la mayoría de TV Box modernos). El triángulo está calculado
  matemáticamente para quedar dentro de la "zona segura" del 66% central, así ningún launcher
  (círculo, squircle, gota, rectángulo redondeado) lo recorta mal.
- **Ícono clásico auto-contenido** (`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` y
  `ic_launcher_round.png`): para TV Box y teléfonos con Android 7.1 o anterior, que no soportan
  íconos adaptativos.
- **Banner de Android TV / Google TV** (`drawable-xhdpi/tv_banner.png` 320×180 y
  `drawable-xxhdpi/tv_banner.png` 480×270): el triángulo + el nombre "JetGo", tal como se ve
  en la fila de apps del launcher de TV.
- **Ícono de Play Store** en `store-assets/ic_launcher_playstore.png` (512×512, sin transparencia),
  listo para subir a la ficha de la Play Store si algún día publicas la app.

Si más adelante quieres tu propio logo/marca en vez de este, solo reemplaza esos mismos archivos
manteniendo nombre y carpeta — no hace falta tocar código.

## Notas técnicas importantes
- `minSdk = 21` para máxima compatibilidad con TV Box antiguos.
- El foco naranja de las tarjetas (igual al de tu captura de referencia) funciona tanto
  con control remoto (D-pad) en TV como con toque en móvil, gracias a
  `Modifier.onFocusChanged` en `CategoryCard.kt` y `PlayerPanel.kt`.
- ExoPlayer detecta automáticamente HLS (`.m3u8`) vs. streams progresivos (TS/MP4).
- Los IDs de categoría se codifican (URL-encode) al navegar, para soportar nombres de
  categoría con espacios o símbolos sin romper la navegación.
