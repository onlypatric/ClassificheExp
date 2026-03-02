# API Cross-Server - Integrazione Via Proxy Messaging

## Obiettivo
Spiegare come un plugin consumer invoca leaderboard su altri server della rete tramite `CrossServerLeaderboardApi`.

## Architettura
- Provider: `it.patric.classificheexp.api.CrossServerLeaderboardApiProvider`
- Contratto: `it.patric.classificheexp.api.CrossServerLeaderboardApi`
- Transport runtime: proxy messaging (canale Bungee), autenticazione HMAC nel bridge.

Prerequisiti runtime:
- `cross-server.enabled=true`
- configurazione cross-server valida su backend
- almeno 1 player online nel server chiamante (vincolo plugin messaging carrier)

## Config Necessaria (`ClassificheExp/config.yml`)
```yml
cross-server:
  enabled: true
  provider: proxy-messaging
  server-id: survival-1
  channel: classificheexp:bridge
  request-timeout-ms: 3000
  auth:
    shared-key: "change-me-long-random-change-me-long-random"
    max-clock-skew-ms: 30000
    nonce-ttl-ms: 120000
    reject-unsigned: true
```

Valori consigliati produzione:
- `request-timeout-ms`: 2000-5000
- `shared-key`: almeno 32 char, random forte
- `max-clock-skew-ms`: 10000-30000 con clock sincronizzati (NTP)

## Auth E Sicurezza
- Firma HMAC SHA-256 su envelope bridge.
- Anti-replay con nonce TTL.
- Verifica skew temporale.
- Messaggi invalidi/auth fail vengono scartati lato transport.

Nota per consumer API:
- normalmente gli errori auth non arrivano come codice dedicato al chiamante;
- il caso tipico e `timeout` o errore completato eccezionalmente.

## Contratto Metodi Cross-Server
- `CompletionStage<Integer> getScore(String targetServerId, String name)`
- `CompletionStage<List<LeaderboardEntry>> getTop(String targetServerId, int limit)`
- `CompletionStage<Void> addScore(String targetServerId, String name, int points)`
- `CompletionStage<Void> removeScore(String targetServerId, String name, int points)`
- `CompletionStage<Void> setScore(String targetServerId, String name, int points)`

Semantica errori applicativi:
- `VALIDATION_ERROR` (input non valido)
- `INTERNAL_ERROR` (errore backend remoto)
- timeout request (infrastrutturale)

## Snippet Java Pronti

### Accesso provider cross-server
```java
import it.patric.classificheexp.api.CrossServerLeaderboardApi;
import it.patric.classificheexp.api.CrossServerLeaderboardApiProvider;

CrossServerLeaderboardApi api = CrossServerLeaderboardApiProvider.require();
```

### Get score remoto
```java
String targetServer = "survival-2";
api.getScore(targetServer, "Muffin299").whenComplete((score, throwable) -> {
    if (throwable != null) {
        getLogger().warning("getScore remoto fallito: " + unwrap(throwable).getMessage());
        return;
    }
    getLogger().info("Score remoto: " + score);
});
```

### Mutazione remota con callback robusta
```java
String targetServer = "survival-2";
api.setScore(targetServer, "Muffin299", 150).whenComplete((ignored, throwable) -> {
    if (throwable != null) {
        Throwable cause = unwrap(throwable);
        getLogger().warning("setScore remoto fallito: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
        return;
    }
    getLogger().info("setScore remoto completata.");
});

private static Throwable unwrap(Throwable throwable) {
    return throwable.getCause() == null ? throwable : throwable.getCause();
}
```

## Troubleshooting
- `CrossServerLeaderboardApi not registered`
  - cross-server disabilitato o bootstrap fallito.
- timeout frequenti
  - target server-id errato, no player carrier online, key mismatch, canale diverso.
- errori di validazione
  - `name` blank, `points` invalidi, `limit` invalidi.
- chiamate intermittenti
  - clock non sincronizzato tra nodi, rete proxy instabile.

## Errori Comuni
- `server-id` duplicato tra backend.
- `channel` diverso tra nodi.
- `shared-key` diversa tra server.
- test su server vuoto senza player online.

## Checklist Rapida
- `cross-server.enabled=true` su nodi coinvolti.
- `server-id` univoco su ogni nodo.
- `channel` uguale su tutti i nodi.
- `shared-key` uguale su tutti i nodi.
- almeno 1 player online sul nodo chiamante.
- gestione timeout/errori in callback async.

## Smoke Test Multi-Server
1. Server A e B online dietro proxy.
2. Da plugin su A: `setScore("B", "alpha", 10)`.
3. Da plugin su A: `getScore("B", "alpha")` -> atteso `10`.
4. Spegni/rompi auth key su B e riprova -> atteso timeout/errore.

