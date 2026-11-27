# WakeAI

A hands-free Android voice assistant. Say the wake word **"Jarvis"**, ask a question, and hear the answer read back aloud.

## How it works

1. Tap **Start** and grant microphone permission.
2. [Porcupine](https://picovoice.ai/platform/porcupine/) listens on-device for the wake word **"Jarvis"**.
3. On detection, the app records your speech until about 1 second of silence.
4. The recording is sent to OpenAI Whisper (`whisper-1`) for transcription.
5. The transcript is sent to OpenAI (`gpt-4.1`, Responses API).
6. The reply is converted to speech with OpenAI TTS (`tts-1`, voice `nova`).
7. The audio plays through `MediaPlayer`, then the app returns to listening for the wake word.

The status text on screen shows each step: Sleeping, Listening, Transcribing, Loading, Playing response.

## Tech stack

- Kotlin, AppCompat, Material Components, ViewBinding and Data Binding
- [Picovoice Porcupine](https://picovoice.ai/) `3.0.2` for wake-word detection
- [OkHttp](https://square.github.io/okhttp/) `4.12.0` for the OpenAI REST calls
- Android `AudioRecord` for microphone capture (16 kHz mono PCM, wrapped as WAV)

## Requirements

- Android Studio (recent stable version)
- JDK 11+
- Android device or emulator with a microphone, API 24 (Android 7.0) or higher
- A [Picovoice AccessKey](https://console.picovoice.ai/)
- An [OpenAI API key](https://platform.openai.com/api-keys) with access to Whisper, Chat/Responses and TTS

## Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd WakeAI
   ```
2. Open the project in Android Studio and let Gradle sync.
3. Add your keys (see [Configuration](#configuration)).
4. Run the `app` configuration on a device.

## Configuration

Configure your own keys:

| Key | Location |
|-----|----------|
| Picovoice AccessKey | `PORCUPINE_API_KEY` in `app/src/main/java/com/example/wakeai/MainActivity.kt` |
| OpenAI API key | `OPEN_AI_API_KEY` in `app/src/main/java/com/example/wakeai/OpenAIApiService.kt` |

> **Warning:** Do not commit real keys. Move them to `local.properties` (excluded from git) and expose them through `BuildConfig`.

## Project structure

```
app/src/main/
├── java/com/example/wakeai/
│   ├── MainActivity.kt          # UI, permission flow, Porcupine wake-word pipeline, playback
│   ├── AudioRecorderWrapper.kt  # Mic capture, silence detection, PCM-to-WAV conversion
│   └── OpenAIApiService.kt      # Whisper, Responses (chat) and TTS HTTP calls
├── res/layout/activity_main.xml # Start button and status text
└── AndroidManifest.xml          # INTERNET and RECORD_AUDIO permissions
```

## Permissions

- `RECORD_AUDIO`: microphone access for the wake word and speech capture
- `INTERNET`: calls to the OpenAI API

## Known limitations

- API keys are stored in source code (see warning above).
- Network failures in `OpenAIApiService` are silently ignored (`onFailure` is empty), so the status text can stay on a step and never return to "Sleeping...".
- The wake word and conversation flow are not tied to any lifecycle handling. `onDestroy` calls `porcupineManager.stop()` even if it was never initialized, which can crash if the app closes before Start is pressed.
- Responses and audio are generated with the `gpt-4.1` and `tts-1` models, which incur OpenAI usage costs.
- No automated tests are included yet.

## License

Add your license here.
