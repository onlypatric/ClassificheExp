# ClassificheExp - ROADMAP implementativa

## 0. Regole di lavoro

- `ACTIONPLAN.md` resta il documento architetturale di riferimento.
- `ROADMAP.md` è il piano operativo eseguibile in ordine.
- Le decisioni bloccate non vengono ridefinite durante l'implementazione MVP.
- Ogni fase deve chiudersi con DoD verificabile prima di passare alla successiva.
- Tutte le modifiche devono mantenere compatibilità con Java 21 e Paper 1.21.11.

Decisioni bloccate:

- Granularità: 10-14 fasi dettagliate
- Formato: `ROADMAP.md` separato
- Cross-server: milestone post-MVP
- MySQL stack: HikariCP + JDBC puro
- Async model: `CompletableFuture` + executor dedicato
- Test scope iniziale: unit + integrazione repository

## 1..12 Fasi

## Fase 1 - Fondazioni dominio e contratti

### Obiettivo

Stabilire tipi e interfacce core senza logica infrastrutturale.

### Prerequisiti

- Progetto Gradle bootstrap funzionante
- Package root definito: `it.patric.classificheexp`

### Attività

- Definire `domain` (`LeaderboardEntry`, `LeaderboardId`, `LeaderboardSnapshot`).
- Definire contratti `application.LeaderboardService`.
- Definire contratti `persistence.LeaderboardRepository`.
- Definire contratti `api.LeaderboardApi`.

### Deliverable

- Interfacce/tipi compilabili e coerenti con Java 21.

### Definition of Done

- Nessuna dipendenza da Bukkit/Paper nei contratti dominio/applicazione.

### Test e Validazione

- Build `./gradlew build` verde.
- Verifica firme API allineate alle decisioni bloccate.

### Rischi e Mitigazioni

- Rischio: API incomplete o troppo accoppiate.
- Mitigazione: review firme prima di introdurre implementazioni.

## Fase 2 - Config e bootstrap tecnico

### Obiettivo

Centralizzare caricamento config e wiring iniziale.

### Prerequisiti

- Fase 1 completata.

### Attività

- Implementare `config.PluginConfig` e `ConfigLoader`.
- Validazione config con fallback sicuri.
- Preparare `PluginBootstrap` per dependency wiring.

### Deliverable

- Bootstrap inizializzato in `Main`.

### Definition of Done

- Plugin parte con config valida e logga errori chiari su config invalida.

### Test e Validazione

- Startup con config default.
- Startup con valori mancanti/non validi e log atteso.

### Rischi e Mitigazioni

- Rischio: crash su config incompleta.
- Mitigazione: default espliciti e validazione preventiva.

## Fase 3 - Infrastruttura async e naming rules

### Obiettivo

Standardizzare esecuzione async e regole dato.

### Prerequisiti

- Fase 2 completata.

### Attività

- `util.AsyncExecutor` dedicato.
- `NameNormalizer` e `ScoreValidator`.
- Policy main-thread vs async-thread documentata nel codice.

### Deliverable

- Utility riusabili da service/repository.

### Definition of Done

- Nessuna operazione bloccante sul main thread.

### Test e Validazione

- Unit test su normalizzazione nomi.
- Unit test su vincoli punteggio (`>= 0`).

### Rischi e Mitigazioni

- Rischio: race condition thread.
- Mitigazione: boundary chiaro tra async I/O e stato runtime.

## Fase 4 - Repository YML fallback

### Obiettivo

Avere persistenza locale funzionante end-to-end.

### Prerequisiti

- Fasi 1-3 completate.

### Attività

- Implementare `YamlLeaderboardRepository`.
- Mapping file ↔ entry.
- Gestione assenza/corruzione file con recovery safe.

### Deliverable

- CRUD repository YML asincrono.

### Definition of Done

- Load/save/delete validati con test integrazione locali.

### Test e Validazione

- Persistenza entry nuove.
- Aggiornamento score esistente.
- Delete entry.
- Recovery su file mancante/corrotto.

### Rischi e Mitigazioni

- Rischio: perdita dati su write parziale.
- Mitigazione: write atomica (temp + replace).

## Fase 5 - Repository MySQL primario (HikariCP + JDBC)

### Obiettivo

Implementare source of truth persistente.

### Prerequisiti

- Fasi 1-4 completate.
- Config MySQL disponibile.

### Attività

- `MySqlConnectionFactory` con HikariCP.
- DDL bootstrap tabella.
- `MySqlLeaderboardRepository` con SQL esplicito JDBC.

### Deliverable

- Repository MySQL completo + health check `isAvailable`.

### Definition of Done

- Pool stabile, query parametrizzate, no SQL injection.

### Test e Validazione

- Startup con DB vuoto + bootstrap tabella.
- CRUD completo su DB.
- Health check true/false coerente.

### Rischi e Mitigazioni

- Rischio: leak connessioni.
- Mitigazione: try-with-resources e chiusura pool in shutdown.

## Fase 6 - StorageCoordinator (strategia MySQL + YML)

### Obiettivo

Formalizzare policy primaria/fallback.

### Prerequisiti

- Fasi 1-5 completate.

### Attività

- Startup flow: prova MySQL, fallback YML.
- Runtime write-through con fallback degradato.
- Riconnessione e risincronizzazione MySQL -> YML.

### Deliverable

- `StorageCoordinator` orchestrante.

### Definition of Done

- Rispetta la regola: YML non sovrascrive mai MySQL.

### Test e Validazione

- Startup MySQL up/down.
- Write con MySQL down e fallback YML.
- Reconnect MySQL con riallineamento corretto.

### Rischi e Mitigazioni

- Rischio: divergenza tra fonti.
- Mitigazione: MySQL source of truth + resync forzato.

## Fase 7 - Service layer con cache centrale

### Obiettivo

Implementare logica di business e cache runtime.

### Prerequisiti

- Fasi 1-6 completate.

### Attività

- `DefaultLeaderboardService` con mappa in-memory normalizzata.
- Operazioni score (`get`, `add`, `remove`, `set`, `top`).
- `reloadFromPrimary()` per resync.

### Deliverable

- Service pronto per API/comandi.

### Definition of Done

- Letture sync da cache, scritture async persistenti.

### Test e Validazione

- Unit test su operazioni score e top ordering.
- Test `remove` con floor a 0.
- Test `reloadFromPrimary`.

### Rischi e Mitigazioni

- Rischio: cache stale.
- Mitigazione: refresh esplicito e flussi di aggiornamento centralizzati.

## Fase 8 - API pubblica per plugin esterni

### Obiettivo

Esporre contratti stabili per integrazioni.

### Prerequisiti

- Fasi 1-7 completate.

### Attività

- Implementare `LeaderboardApi` adapter sul service.
- `LeaderboardApiProvider` per accesso sicuro.
- Registrazione/esposizione in lifecycle plugin.

### Deliverable

- API interna consumabile da altri plugin.

### Definition of Done

- Nessuna dipendenza da command layer nell'API.

### Test e Validazione

- Test accesso API da plugin mock.
- Verifica coerenza risultati API vs service.

### Rischi e Mitigazioni

- Rischio: coupling con Main/plugin internals.
- Mitigazione: provider dedicato e interfacce stabili.

## Fase 9 - Command layer e permessi

### Obiettivo

Rendere disponibile gestione via `/leaderboard`.

### Prerequisiti

- Fasi 1-8 completate.

### Attività

- `LeaderboardCommandExecutor`.
- Subcommands (`add/remove/set/get/top`).
- `LeaderboardTabCompleter`.
- Messaggistica chiara e validazione argomenti.

### Deliverable

- Comando completo e coerente con `plugin.yml`.

### Definition of Done

- Permessi applicati correttamente, error handling leggibile.

### Test e Validazione

- Test parsing argomenti.
- Test permessi OP/non-OP.
- Smoke test manuale su server locale.

### Rischi e Mitigazioni

- Rischio: UX comando confusa.
- Mitigazione: usage uniforme e messaggi brevi ma espliciti.

## Fase 10 - Thread safety, failure handling, observability

### Obiettivo

Rendere robusta l'esecuzione in produzione.

### Prerequisiti

- Fasi 1-9 completate.

### Attività

- Garantire update stato plugin solo su main thread.
- Timeout/retry/backoff per MySQL.
- Logging strutturato (contesto operazione, causa errore).

### Deliverable

- Policy runtime robuste e tracciabili.

### Definition of Done

- Nessun deadlock noto, degradazione controllata.

### Test e Validazione

- Test fail transienti MySQL.
- Verifica log su errori e recovery.

### Rischi e Mitigazioni

- Rischio: retry aggressivi.
- Mitigazione: backoff progressivo + cap retry.

## Fase 11 - Test suite MVP (unit + integrazione)

### Obiettivo

Coprire logica critica e repository.

### Prerequisiti

- Fasi 1-10 completate.

### Attività

- Unit test su service/validator/normalizer.
- Integration test repository YML.
- Integration test repository MySQL (test container o DB dedicato dev).
- Test scenari fallback e riconnessione.

### Deliverable

- Suite automatica minima affidabile.

### Definition of Done

- Copertura delle path critiche e passaggio CI locale.

### Test e Validazione

- Esecuzione test completa con report verde.
- Verifica scenari obbligatori MVP.

### Rischi e Mitigazioni

- Rischio: test fragili su ambiente DB.
- Mitigazione: fixture isolate e cleanup deterministico.

## Fase 12 - Hardening e release MVP

### Obiettivo

Chiudere MVP pronto per uso server.

### Prerequisiti

- Fasi 1-11 completate.

### Attività

- Rifinitura config defaults e messaggi.
- Verifica compatibilità Paper 1.21.11.
- Smoke test su server Paper reale.
- Changelog e pacchetto release.

### Deliverable

- Jar MVP rilasciabile.

### Definition of Done

- Checklist release completata senza blocker P0/P1.

### Test e Validazione

- Build release pulita.
- Test funzionale finale su ambiente di staging.

### Rischi e Mitigazioni

- Rischio: regressioni tardive.
- Mitigazione: freeze feature + smoke test pre-release.

## Criteri di accettazione finali MVP

- Startup con MySQL disponibile funziona senza errori bloccanti.
- Startup con MySQL non disponibile usa fallback YML.
- Runtime: `addScore` con MySQL down non perde aggiornamento (fallback).
- Reconnect MySQL esegue resync corretto MySQL -> YML.
- Validazioni input rispettate (`points < 0`, nome vuoto/non valido).
- `top n` rispetta limiti configurati.
- API pubbliche allineate alle firme congelate.
- Comando `/leaderboard` operativo con permessi coerenti.

## Milestone post-MVP: Cross-server

Fasi dedicate dopo rilascio MVP:

1. Definizione `CrossServerBus` + eventi versionati (`eventId`, `originServerId`, `leaderboardId`, payload score).
2. Implementazione `RedisCrossServerBus` (pub/sub).
3. `CrossServerSyncService` con deduplica TTL e no-loop.
4. Recovery: bus offline -> resync da MySQL con `reloadFromPrimary`.
5. Test multi-nodo (2+ server) su consistency/event-ordering best effort.

Assunzioni cross-server:

- Eventual consistency tra nodi.
- MySQL resta source of truth.
- Cross-server non blocca la release MVP.

## Registro rischi e mitigazioni

- Divergenza dati MySQL/YML.
- Recovery incompleta dopo outage DB.
- Concorrenza su update simultanei.
- Errori di configurazione in produzione.
- Dipendenze esterne (DB/Redis) non stabili.

Mitigazioni obbligatorie:

- Policy source-of-truth esplicita.
- Health check periodici + logging strutturato.
- Retry/backoff controllati.
- Test fallback/reconnect in CI locale.
- Documentazione operativa minima (`docs/`).

## Checklist release

- Build e test verdi.
- Verifica versioni (`Java 21`, `Paper 1.21.11`).
- Config defaults validati.
- Permessi/comandi verificati manualmente.
- Changelog aggiornato.
- Jar generata e archiviata.
- Piano rollback definito (ripristino jar/config precedente).

## API pubbliche congelate per MVP

`LeaderboardService`:

- `int getScore(String name)`
- `List<LeaderboardEntry> getTop(int limit)`
- `CompletionStage<Void> addScore(String name, int points)`
- `CompletionStage<Void> removeScore(String name, int points)`
- `CompletionStage<Void> setScore(String name, int points)`
- `CompletionStage<Void> reloadFromPrimary()`

`LeaderboardRepository`:

- `CompletionStage<Map<String, LeaderboardEntry>> loadAll()`
- `CompletionStage<Void> save(LeaderboardEntry entry)`
- `CompletionStage<Void> delete(String normalizedName)`
- `CompletionStage<Boolean> isAvailable()`

`LeaderboardApi`:

- `int getScore(String name)`
- `List<LeaderboardEntry> getTop(int limit)`
- `CompletionStage<Void> addScore(String name, int points)`
- `CompletionStage<Void> removeScore(String name, int points)`
- `CompletionStage<Void> setScore(String name, int points)`

## Test cases e scenari obbligatori

- Startup con MySQL disponibile.
- Startup con MySQL indisponibile e fallback YML.
- Runtime: MySQL down durante `addScore`.
- Runtime: reconnect MySQL e sync corretta.
- Input invalidi (`points < 0`, nome vuoto/non valido).
- `top n` con limiti.
- Concorrenza su update simultanei.
- (Post-MVP) Evento cross-server duplicato con stesso `eventId`.

## Assunzioni e default espliciti

- Java 21 e Paper 1.21.11 restano target.
- Single leaderboard MVP (`leaderboard.default-name`) prima del multi-board.
- MySQL è sempre verità; YML solo fallback/backup.
- No ORM nel MVP.
- Cross-server non blocca rilascio MVP.
