# ATS Audio Player (ATS-2835P DSP Engine)

Reproductor de música profesional para **Android 14 (API 34)** con soporte nativo de **TODOS los formatos de audio** y ecualizador DSP de 32 bandas paramétricas con compresión multibanda (MDRC).

## 🚀 Compatibilidad Total con Formatos de Audio
Gracias a **AndroidX Media3 (ExoPlayer 1.2.1)** y sus extractores de códec nativos, este reproductor decodifica y reproduce:
- **FLAC** (Lossless Hi-Res Audio hasta 24-bit / 192 kHz)
- **WAV** (Linear PCM sin compresión y IEEE Float 32-bit)
- **MP3** (MPEG-1 Audio Layer III con soporte de etiquetas ID3v2)
- **AAC / M4A / MP4** (Advanced Audio Coding, AAC-LC, HE-AAC v1/v2, ALAC)
- **OGG / Vorbis** (Ogg audio de código abierto)
- **OPUS** (Códec ultra-eficiente de baja latencia)
- **WMA** (Windows Media Audio mediante decodificador del sistema)
- **AIFF** (Audio Interchange File Format)
- **ALAC** (Apple Lossless Audio Codec)

## 📱 Preparado para Android 14 (API 34)
- **Permisos Granulares:** Cumple estrictamente con `READ_MEDIA_AUDIO` y `POST_NOTIFICATIONS`.
- **Servicio en Primer Plano:** Declarado con `foregroundServiceType="mediaPlayback"` requerido por Android 14.
- **Audio DSP:** Arquitectura sobre `android.media.audiofx.DynamicsProcessing` (PreEQ de 32 bandas, MDRC de 5 bandas, PostEQ y Peak Limiter).

## 🛠️ Cómo subir este proyecto a GitHub y generar el APK
1. Descarga el proyecto haciendo clic en **"Descargar Proyecto (.ZIP)"**.
2. Descomprime el archivo en tu computadora o celular.
3. En GitHub (https://github.com/new), crea un nuevo repositorio llamado por ejemplo `ats-audio-player`.
4. Abre la terminal en la carpeta descomprimida y ejecuta:
```bash
git init
git add .
git commit -m "Initial commit - ATS Audio Player Android 14"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/ats-audio-player.git
git push -u origin main
```
*(También puedes arrastrar todos los archivos descomprimidos directamente en la interfaz web de GitHub con el botón "Upload files")*.

5. **Compilación automática**: Al hacer push, el flujo de **GitHub Actions** (`.github/workflows/build-apk.yml`) compilará automáticamente el archivo APK en unos 2 minutos.
6. Ve a la pestaña **Actions** en tu repositorio de GitHub, selecciona la última ejecución y descarga tu archivo **APK** listo para instalar en cualquier teléfono con Android 14.
