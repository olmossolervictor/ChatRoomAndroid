# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Full check (build + lint + tests)
./gradlew check

# Clean build
./gradlew clean
```

## Architecture Overview

**ChatRoomAndroid** is a real-time group and private chat app with QR-based room access and 120-minute auto-expulsion sessions.

**Stack:**
- Frontend: Native Android (Java), minSdk 24, compileSdk 36
- Networking: Retrofit 3.0 + Gson against a PHP/MySQL backend on Railway
- QR scanning: ML Kit Barcode + CameraX
- Location: Google Play Services Location API
- Real-time updates: 3-second polling via `Handler`/`Runnable`

**Activity flow:**
```
LoginActivity → HomeActivity → ScannerActivity (QR scan) → MainActivity (group chat)
                             ↘ PrivateChatActivity (1:1 chat)
```

**Package structure under `com.example.chat`:**

| Package | Contents |
|---------|----------|
| `activities` | `LoginActivity`, `HomeActivity`, `RegisterActivity`, `ScannerActivity`, `MainActivity`, `PrivateChatActivity` |
| `adapters` | `MensajeAdapter` (chat bubbles), `SalaAdapter` (room list) |
| `models` | `Mensaje`, `Sala` |
| `network` | `ChatApiServices` (Retrofit interface), `RetrofitClient` (singleton), `FormUrlEncoded` (custom annotation) |

**Key class responsibilities:**

| Class | Role |
|-------|------|
| `network/ChatApiServices` | All API endpoints; add new endpoints here |
| `network/RetrofitClient` | Singleton Retrofit/OkHttp — do not modify |
| `activities/HomeActivity` | Shows user's active rooms list + Escanear QR button |
| `activities/ScannerActivity` | Scans QR → calls `unirseASala` → fetches room info → returns to Home |
| `activities/MainActivity` | Group chat; polls messages + time + geofence every 3s |
| `models/Sala` | Room model: `id_sala`, `nombre`, `latitud`, `longitud`, `radio_metros`, `tiempo_maximo` |

**Session persistence:** user ID and display name stored in `SharedPreferences` (`ChatPrefs`).

**Room join flow:**
1. User scans QR with room name (e.g. `GENERAL`) in `ScannerActivity`
2. Scanner calls `unirseASala` then `getSalaInfo` (lat/lng/radius)
3. Returns to `HomeActivity` via `onActivityResult`; Home starts `MainActivity` with room + geo data as Intent extras (`ID_SALA_QR`, `SALA_LATITUD`, `SALA_LONGITUD`, `SALA_RADIO`)
4. `MainActivity` polls every 3s: messages, session time (`verificarSesionSala`), and geofence distance
5. Either check expels user → `finish()` back to Home

**Geofence:** `SALA_RADIO = 0` means no location restriction for that room.

**Message sending:** Every outgoing group message has the sender's GPS coordinates appended as `\n(Lat: X, Lon: Y)` by `obtenerUbicacionYEnviar`. If location permission is denied, the message is not sent.

**Private chat trigger:** Tapping another user's message bubble in `MainActivity` calls `crearChatPrivado` and opens `PrivateChatActivity` with extras `ID_CHAT_PRIVADO`, `CURRENT_USER_ID`, `OTHER_USER_ID`, `OTHER_USER_NAME`.

**Package note:** The root `com.example.chat` package contains empty stub files (one-line moved comments). All real source lives in the subpackages listed above.

## Backend API

The PHP backend runs on Railway (HTTPS). The base URL is a constant in `RetrofitClient.BASE_URL`. All requests use `application/x-www-form-urlencoded`. The database schema includes: `usuarios`, `salas`, `usuario_sala`, `log_mensajes_grupal`, `chats_privados`, `log_mensajes_privados`.

## Dependency Versions

Managed via `gradle/libs.versions.toml`. Key versions: Retrofit 3.0.0, CameraX 1.3.0, ML Kit Barcode 17.2.0, Play Services Location 21.1.0.
