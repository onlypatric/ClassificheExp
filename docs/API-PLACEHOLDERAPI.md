# PlaceholderAPI - Guida Integrazione

## Obiettivo
Documentare placeholder supportate da `ClassificheExp` e come usarle in scoreboard/chat/HUD.

## Prerequisiti
- PlaceholderAPI installata nel server.
- `ClassificheExp` attivo.
- Config:

```yml
placeholders:
  enabled: true
```

## Placeholder Supportate
- `%classificheexp_score%`
  - score del player corrente (richiedente).
- `%classificheexp_score_<name>%`
  - score di un nome specifico.
- `%classificheexp_top_<n>%`
  - riga `n` della top, formattata con `top-entry-format`.

## Semantica `top_<n>`
- `n` e 1-based (`1` = primo posto).
- range valido: `1..100`.
- rank non presente -> `placeholders.top-empty-value`.
- parse invalido (es. `top_x`) -> `placeholders.missing-value`.

## Config Rendering
```yml
placeholders:
  enabled: true
  missing-value: "N/A"
  top-entry-format: "<gray>%rank%)</gray> <yellow>%name%</yellow>: <green>%score%</green>"
  top-separator: " <dark_gray>|</dark_gray> "
  top-empty-value: "<gray>Nessun dato in classifica.</gray>"
```

Variabili supportate in `top-entry-format`:
- `%rank%`
- `%name%`
- `%score%`

Nota:
- con semantica attuale line-based, `top-separator` non viene usato da `%classificheexp_top_<n>%` (ogni placeholder ritorna una singola riga).

## Esempio Scoreboard Multiline Affidabile
Usa una riga per rank:
- Riga 1: `%classificheexp_top_1%`
- Riga 2: `%classificheexp_top_2%`
- Riga 3: `%classificheexp_top_3%`
- ...
- Riga 10: `%classificheexp_top_10%`

Questo approccio e piu affidabile del multiline dentro un singolo placeholder.

## Esempi Pratici

### Score giocatore corrente
`%classificheexp_score%`

### Score giocatore specifico
`%classificheexp_score_Muffin299%`

### Prime 3 righe top
- `%classificheexp_top_1%`
- `%classificheexp_top_2%`
- `%classificheexp_top_3%`

## Aggiornamento Dati
- Se il punteggio viene aggiornato tramite comandi/API/bridge del plugin, placeholder riflettono il nuovo valore.
- Se il DB viene modificato esternamente (query manuali), serve reload cache (`reloadFromPrimary`) per riflettere i cambi.

## Limiti Noti
- Output placeholder e stringa raw; il rendering MiniMessage dipende dal plugin consumer (scoreboard/chat plugin).
- UUID top non e esposto da placeholder MVP.

## Errori Comuni
- PlaceholderAPI non installata ma integrazione attesa.
- `placeholders.enabled=false`.
- Uso di `%classificheexp_top_0%` o `%classificheexp_top_x%`.
- aspettarsi sync realtime dopo update SQL manuale esterno.

## Checklist Rapida
- PlaceholderAPI presente.
- `placeholders.enabled: true`.
- placeholder corrette nel formato `%classificheexp_*%`.
- testate almeno `%classificheexp_score%` e `%classificheexp_top_1%`.
- per top multiline: usare `%classificheexp_top_1..10%` su righe separate.

## Smoke Test
1. `/leaderboard set alpha 10`
2. `/leaderboard set beta 20`
3. Parse `%classificheexp_top_1%` -> atteso beta
4. Parse `%classificheexp_top_2%` -> atteso alpha
5. Parse `%classificheexp_top_10%` -> `top-empty-value` se non ci sono 10 entry

