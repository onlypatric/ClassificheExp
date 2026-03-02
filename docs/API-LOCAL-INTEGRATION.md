# API Locale - Integrazione Con Altri Plugin

## Obiettivo
Spiegare come un plugin Paper nello stesso server puo usare `ClassificheExp` tramite API pubblica locale.

## Panoramica
- Contratto pubblico: `it.patric.classificheexp.api.LeaderboardApi`
- Accesso: `it.patric.classificheexp.api.LeaderboardApiProvider`
- Pattern consigliato: ottenere l'API in `onEnable()` e riutilizzarla nel plugin consumer.

## Prerequisiti Plugin Consumer
Nel `plugin.yml` del tuo plugin:

```yml
name: MyConsumer
version: 1.0.0
main: com.example.myconsumer.MyConsumerPlugin
api-version: "1.21"
softdepend: [ClassificheExp]
```

Note:
- Usa `softdepend` se vuoi che il tuo plugin funzioni anche senza `ClassificheExp`.
- Usa `depend` solo se il tuo plugin non puo avviarsi senza questa API.

## Accesso Sicuro All'API

### Variante safe (`get`)
```java
import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.api.LeaderboardApiProvider;

import java.util.Optional;

Optional<LeaderboardApi> apiOpt = LeaderboardApiProvider.get();
if (apiOpt.isEmpty()) {
    getLogger().warning("ClassificheExp API non disponibile.");
    return;
}
LeaderboardApi api = apiOpt.get();
```

### Variante fail-fast (`require`)
```java
import it.patric.classificheexp.api.LeaderboardApi;
import it.patric.classificheexp.api.LeaderboardApiProvider;

LeaderboardApi api = LeaderboardApiProvider.require(); // IllegalStateException se non registrata
```

## Contratto Metodi (Freeze)
- `int getScore(String name)`
  - Ritorna `0` se il player non esiste in classifica.
- `List<LeaderboardEntry> getTop(int limit)`
  - Ritorna lista vuota se `limit <= 0`.
- `CompletionStage<Void> addScore(String name, int points)`
- `CompletionStage<Void> removeScore(String name, int points)`
- `CompletionStage<Void> setScore(String name, int points)`
  - Operazioni async: gli errori arrivano nel `CompletionStage`.

## Snippet Java Pronti

### Leggere score
```java
int score = api.getScore("Muffin299");
getLogger().info("Score attuale: " + score);
```

### Leggere top N
```java
api.getTop(10).forEach(entry ->
        getLogger().info(entry.name() + " -> " + entry.score())
);
```

### Mutazione async con gestione errore
```java
import org.bukkit.Bukkit;

api.addScore("Muffin299", 5).whenComplete((ignored, throwable) -> {
    if (throwable != null) {
        Throwable cause = unwrap(throwable);
        Bukkit.getScheduler().runTask(this, () ->
                getLogger().warning("addScore fallita: " + cause.getMessage()));
        return;
    }

    int total = api.getScore("Muffin299");
    Bukkit.getScheduler().runTask(this, () ->
            getLogger().info("Nuovo totale: " + total));
});

private static Throwable unwrap(Throwable throwable) {
    return throwable.getCause() == null ? throwable : throwable.getCause();
}
```

## Threading E Best Practices
- Non chiamare `.join()` o `.get()` sul main thread Bukkit per operazioni async.
- Se devi inviare messaggi/chat/comandi da callback async, fai handoff sul main thread con scheduler Bukkit.
- Valida sempre input utente prima di invocare `add/remove/set`.

## Failure Modes
- `IllegalStateException`
  - `LeaderboardApiProvider.require()` chiamato quando API non registrata.
- `IllegalArgumentException`
  - nome invalido, punti invalidi (`<= 0` per add/remove, `< 0` per set).
- Errori infrastrutturali
  - ritornano come completion exceptional nelle operazioni async.

## Errori Comuni
- API richiesta in `onLoad()` invece di `onEnable()`.
- Uso di `require()` senza controllare `depend/softdepend`.
- Operazioni async bloccate con `.join()` sul main thread.

## Checklist Rapida
- `plugin.yml` con `softdepend` o `depend` su `ClassificheExp`.
- Accesso API in `onEnable()`.
- Gestione `CompletionStage` con `whenComplete`.
- Nessun blocco del main thread.
- Fallback e error handling gestiti in log/UX.

## Smoke Test Locale
1. Avvia server con `ClassificheExp` + plugin consumer.
2. In `onEnable` del consumer, ottieni API con `get()`/`require()`.
3. Esegui `setScore("alpha", 10)`, poi `getScore("alpha")`.
4. Verifica `getTop(5)` contenga `alpha`.

