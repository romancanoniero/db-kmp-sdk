# db-kmp-sdk

SDK Kotlin Multiplatform (Android · iOS · JS) **API-compatible** con la superficie
Firebase que consume el ecosistema IAN, pero respaldado por **infraestructura
propia**: habla un protocolo **WebSocket** contra
[`db-realtime-gateway`](../db-realtime-gateway) (Ktor + PostgreSQL en un VPS), en
lugar de los SDK nativos de Firebase.

> Objetivo: reducir el costo de acceso a base de datos de Firebase (egress +
> conexiones de listeners) sin obligar a cambiar las capas de negocio de los
> clientes. Plan completo: `IANKMP/docs/DB_KMP_SDK_PLAN_INTEGRAL.md`.

## Por qué es más simple que firebase-kmp-sdk

`firebase-kmp-sdk` necesita `expect/actual` pesados porque envuelve tres SDK
nativos (Firebase Android/iOS/JS). `db-kmp-sdk` **no envuelve nada nativo**:
el transporte es **Ktor client**, multiplataforma. Resultado:

- **~90% del código en `commonMain`** (cliente WS, suscripciones, parsing, push-id).
- **`expect/actual` mínimo**: solo el engine de Ktor (`Platform.kt`).
- **Listeners idénticos a Firebase**: `ValueEventListener` / `ChildEventListener`
  / `valueEvents: Flow`. La app no distingue Firebase de WebSocket.

## Módulos

| Módulo | Rol |
|--------|-----|
| `db-core` | `DbApp`, `DbOptions` (endpoint + tokenProvider). |
| `db-database` | API RTDB-like (`DatabaseReference`, `Query`, `DataSnapshot`, listeners), protocolo WS y cliente Ktor. Target JS expone fachada `@JsExport` (`DbWeb`) para el CRM React/TS. |

## Targets

`androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`, `js(IR)` (browser + nodejs).

## Uso (paridad con Firebase)

```kotlin
DbApp.initialize(
    DbOptions(
        endpoint = "wss://db.midominio.com/db",
        tokenProvider = { firebaseAuth.getIdToken(false) }, // Fase 1: token Firebase
    ),
)

val ref = DbDatabase.getInstance().getReference("events_followers/EVT")
ref.addValueEventListener(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) { /* ... */ }
    override fun onCancelled(error: DatabaseError) { /* ... */ }
})
```

## Cómo encaja en cada cliente

- **ian-tv-kmp** (usa firebase-kmp-sdk): swap de dependencia (módulo `compat` a futuro).
- **IANKMP / IanOperators**: repuntar los `actuals` de `services/firebase/` a este SDK.
- **ian-crm-web**: consumir la fachada `@JsExport` (`DbWeb`) desde `src/lib/firebase/`.

## Estado

**Fase 0 (scaffold).** Compila la estructura KMP y define el contrato. Pendiente
de endurecer: reconexión/backoff, cache offline (`keepSynced`), `ServerValue`,
transacciones y motor de reglas en el gateway. Ver TODOs en `RealtimeClient.kt`.

## Build

```bash
./gradlew :db-core:compileKotlinMetadata :db-database:compileKotlinMetadata
```
