# ACTION PLAN - ClassificheExp

Documento operativo ad alto livello per l'implementazione del plugin leaderboard su Paper.

## ✅ Obiettivo

- Gestire classifiche generiche (`<nome, punteggio>`)
- Esporre API interne pubbliche per altri plugin
- Usare MySQL come storage primario + YML come fallback
- Offrire comandi admin chiari e minimali
- Target: Paper `1.21.11`, Java `21`, Gradle

## 🧱 Struttura progetto proposta

Package root: `it.patric.classificheexp`

```text
it.patric.classificheexp
├── bootstrap
│   ├── Main
│   └── PluginBootstrap
├── api
│   ├── LeaderboardApi
│   └── LeaderboardApiProvider
├── application
│   ├── LeaderboardService
│   ├── DefaultLeaderboardService
│   ├── NameNormalizer
│   └── ScoreValidator
├── domain
│   ├── LeaderboardEntry
│   ├── LeaderboardId
│   └── LeaderboardSnapshot
├── persistence
│   ├── LeaderboardRepository
│   ├── mysql
│   │   ├── MySqlLeaderboardRepository
│   │   └── MySqlConnectionFactory
│   ├── yaml
│   │   └── YamlLeaderboardRepository
│   └── StorageCoordinator
├── command
│   ├── LeaderboardCommandExecutor
│   ├── LeaderboardTabCompleter
│   └── subcommand
│       ├── AddSubcommand
│       ├── RemoveSubcommand
│       ├── SetSubcommand
│       ├── GetSubcommand
│       └── TopSubcommand
├── config
│   ├── PluginConfig
│   └── ConfigLoader
└── util
    ├── AsyncExecutor
    └── MessageFormatter
```

## 🔌 Interfacce chiave

### 1) Service (`application`)

`LeaderboardService` (contratto logico, indipendente da storage):

- `int getScore(String name)`
- `List<LeaderboardEntry> getTop(int limit)`
- `CompletionStage<Void> addScore(String name, int points)`
- `CompletionStage<Void> removeScore(String name, int points)`
- `CompletionStage<Void> setScore(String name, int points)`
- `CompletionStage<Void> reloadFromPrimary()`

Note:

- Letture da cache in memoria (veloci, sync)
- Scritture con persistenza async (`CompletionStage`)

### 2) Repository (`persistence`)

`LeaderboardRepository` (astrazione storage):

- `CompletionStage<Map<String, LeaderboardEntry>> loadAll()`
- `CompletionStage<Void> save(LeaderboardEntry entry)`
- `CompletionStage<Void> delete(String normalizedName)`
- `CompletionStage<Boolean> isAvailable()`

Implementazioni:

- `MySqlLeaderboardRepository` (source of truth)
- `YamlLeaderboardRepository` (fallback locale)

### 3) API pubblica (`api`)

`LeaderboardApi` (esposta ad altri plugin):

- `int getScore(String name)`
- `List<LeaderboardEntry> getTop(int limit)`
- `CompletionStage<Void> addScore(String name, int points)`
- `CompletionStage<Void> removeScore(String name, int points)`
- `CompletionStage<Void> setScore(String name, int points)`

`LeaderboardApiProvider`:

- punto unico di accesso da altri plugin
- evita dipendenza diretta dalla classe `Main`

### 4) Comandi (`command`) - interfacce Paper/Bukkit

Uso delle interfacce disponibili:

- `org.bukkit.command.CommandExecutor`
- `org.bukkit.command.TabCompleter`

Comando radice:

- `/leaderboard add <name> <points>`
- `/leaderboard remove <name> <points>`
- `/leaderboard set <name> <points>`
- `/leaderboard get <name>`
- `/leaderboard top [n]`

## 🗄️ Strategia storage: MySQL + YML

Regole:

- MySQL è sempre la verità
- YML è backup/fallback operativo
- YML non sovrascrive mai MySQL

Flusso:

1. Startup: tentativo MySQL
2. Se MySQL disponibile: load da MySQL e sync su YML
3. Se MySQL non disponibile: load da YML
4. Runtime write: prima MySQL; se KO, fallback su YML + stato degradato
5. Riconnessione MySQL: riallineamento da MySQL verso YML

## 🧠 Cache e thread model

- Cache centrale: `Map<String, LeaderboardEntry>` con chiavi normalizzate
- Tutte le mutazioni cache sul main thread Bukkit
- I/O (MySQL/YML) su thread async dedicato
- Risultati async riapplicati sul main thread quando aggiornano stato/plugin

## ⚙️ Configurazione

`config.yml` minimo:

- `mysql.host`
- `mysql.port`
- `mysql.database`
- `mysql.username`
- `mysql.password`
- `mysql.table`
- `fallback.enabled`
- `leaderboard.default-name`

Requisiti:

- default validi
- validazione all'avvio
- log esplicito in caso di config invalida

## 🧩 Lifecycle plugin

`onEnable()`:

1. load config
2. init repository/coordinator
3. warmup cache
4. registrazione comando/tab
5. esposizione API

`onDisable()`:

1. flush pending write
2. close pool/connessioni
3. log stato finale

## 🔐 Validazione e regole dati

- nomi case-insensitive (`lowercase`)
- punteggio minimo `0`
- `remove` non va sotto `0`
- `top n` con limite max configurabile (es. `100`)

## 🧪 Test plan (minimo)

- unit test su `DefaultLeaderboardService`
- test fallback: MySQL down => YML
- test riconnessione: MySQL up => riallineamento YML
- test parsing comandi e permessi

## 🌐 Idea cross-server (high level)

Obiettivo:

- mantenere leaderboard coerente tra piu server Paper (hub, survival, minigame)
- propagare aggiornamenti quasi real-time
- evitare dipendenza da polling continuo sul database

Componenti proposti:

- `CrossServerBus` (interfaccia): pubblica/sottoscrive eventi leaderboard
- `RedisCrossServerBus` (implementazione consigliata): pub/sub su Redis
- `CrossServerSyncService`: applica eventi remoti in modo sicuro
- `ServerIdentityProvider`: id univoco del server corrente (`server-id`)

Eventi minimi:

- `ScoreAddedEvent`
- `ScoreRemovedEvent`
- `ScoreSetEvent`
- `LeaderboardSnapshotRequestEvent` (opzionale)
- `LeaderboardSnapshotResponseEvent` (opzionale)

Metadata evento:

- `eventId` (UUID per deduplica)
- `originServerId`
- `leaderboardId`
- `playerNameNormalized`
- `delta` o `newScore`
- `timestamp`
- `version` (per evoluzione formato messaggi)

Regole operative:

1. Scrittura locale: aggiorna cache locale + persiste (MySQL primario)
2. Pubblica evento sul bus
3. Server remoti ricevono evento
4. Se `originServerId == localServerId`, ignorano (no loop)
5. Se `eventId` gia processato, ignorano (idempotenza)
6. Altrimenti aggiornano cache locale e, se serve, persistono

Consistenza:

- modello eventual consistency tra nodi
- MySQL resta source of truth finale
- bus e usato per latenza bassa di propagazione, non come verita dati

Failure mode:

- bus offline: server continua a funzionare localmente
- al ripristino: resync da MySQL (`reloadFromPrimary`) o snapshot on-demand
- finestra di desincronizzazione tollerata e recuperabile

Configurazione minima (`config.yml`):

- `cross-server.enabled`
- `cross-server.server-id`
- `cross-server.provider` (`redis`)
- `cross-server.redis.host`
- `cross-server.redis.port`
- `cross-server.redis.password`
- `cross-server.redis.channel`

Sicurezza e robustezza:

- validazione payload in ingresso
- deduplica eventi con cache TTL (`eventId`)
- rate limit/base backoff su reconnessione al bus
- logging strutturato per tracing (`eventId`, `originServerId`)

## 📈 Estensioni future

- supporto multi-leaderboard (`kills`, `money`, `exp`)
- reset stagionali
- integrazione PlaceholderAPI
- sync cross-server (Redis/pub-sub)

## 📌 Principi chiave

- MySQL = verità
- YML = fallback
- cache = fonte per letture runtime
- service indipendente da transport (comandi/API)
- separazione netta tra dominio, applicazione e persistenza
