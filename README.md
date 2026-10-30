<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - RT Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-rt-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-rt-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-rt-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-rt-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-rt-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot che recupera da pagoPA le ricevute di pagamento (RT) che mancano in GovPay e le consegna a GovPay come `paSendRTV2`.

## Sommario

Il batch esegue un job Spring Batch, `rtRetrieveJob`, sul database di GovPay. Il job ha tre step, eseguiti in sequenza a ogni esecuzione:

| Step | Cosa fa |
|------|---------|
| `rtRetrieveTasklet` | Recupero automatico: cerca le rendicontazioni degli ultimi `govpay.batch.finestra-temporale` giorni che hanno una pendenza ma nessun pagamento in GovPay (`esegui_recupero_rt` vero), scarica la ricevuta dall'API pagoPA dei *receipts* e la invia a GovPay |
| `rtRecuperoPuntualeStep` | Recupero puntuale: elabora le richieste in `rt_recuperi` inserite da un operatore tramite [govpay-console-api](https://github.com/link-it/govpay-console-api) (`POST /ricevute/recuperi`), senza limite di età del pagamento |
| `rtRecuperoRetentionStep` | Cancella le righe di `rt_recuperi` già elaborate senza successo, più vecchie di `govpay.batch.recupero-puntuale.retention-giorni` giorni |

L'URL dell'API pagoPA e le sue credenziali si ricavano dal connettore *Recupero RT* dell'intermediario del dominio, configurato in GovPay (tabella `connettori`). La ricevuta recuperata è inviata al servizio SOAP paForNode di GovPay, indicato da `govpay.url`, come se arrivasse dal Nodo dei Pagamenti. Ogni chiamata, verso pagoPA e verso GovPay, è registrata nel Giornale degli Eventi.

Il job si avvia da uno scheduler interno (modalità di default), una sola volta con il profilo `cron`, oppure a richiesta con l'API REST descritta in [Avvio manuale via REST](#avvio-manuale-via-rest). Le esecuzioni sono coordinate tra più nodi tramite i metadati Spring Batch sul database.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge `rendicontazioni`, `fr`, `singoli_versamenti`, `versamenti`, `applicazioni`, `rpt`, `domini`, `stazioni`, `intermediari`, `connettori`, `configurazione`, `operatori`, `utenze`; aggiorna `rendicontazioni.esegui_recupero_rt` e scrive e cancella le righe di `rt_recuperi`. Le tabelle sono create dagli script di installazione di GovPay (`rt_recuperi` da quelli di govpay-console-api), il batch non crea lo schema |
| Tabelle Spring Batch | runtime | sì | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), usati anche per evitare esecuzioni concorrenti tra nodi. Lo schema è nel pacchetto `sql.zip` (vedi [Compilazione](#compilazione)) e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: connettori, entità di anagrafica, coordinamento dei job, API REST del batch, client GDE, transaction id e livelli di log da database |
| API pagoPA dei receipts (Biz Events) | runtime | sì | `GET /organizations/{organizationfiscalcode}/receipts/{iur}/paymentoptions/{iuv}`. URL e chiave di sottoscrizione sono nel connettore indicato da `intermediari.cod_connettore_recupero_rt`: senza connettore la ricevuta del dominio non si recupera |
| Servizio paForNode di GovPay | runtime | sì | Riceve le ricevute recuperate come `paSendRTV2` (`govpay.url`, autenticazione Basic): `api-pagopa` del monolite o [govpay-pafornode-api](https://github.com/link-it/govpay-pafornode-api). Per il recupero puntuale la richiesta porta l'header `GOVPAY-ON-BEHALF-OF` con il principal dell'operatore |
| [govpay-fdr-batch](https://github.com/link-it/govpay-fdr-batch) | runtime | sì | Acquisisce da pagoPA i flussi di rendicontazione (`fr`, `rendicontazioni`, con `esegui_recupero_rt` vero) da cui parte il recupero automatico |
| [govpay-console-api](https://github.com/link-it/govpay-console-api) | runtime | no | Inserisce in `rt_recuperi` le richieste di recupero puntuale e avvia il batch con `GET /api/batch/run`; avvia e consulta il batch dalle operazioni (voce `RECUPERO_RT` del catalogo di default, `http://govpay-rt-batch:10001/api/batch`) |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Riceve gli eventi delle chiamate a pagoPA e a GovPay (`POST /eventi`), se il connettore `govpay_gde_api` è abilitato in GovPay. Un GDE non raggiungibile non blocca il batch |

Le modifiche ai livelli di log fatte dalla console (riga `log_level` della tabella `configurazione`) raggiungono il batch entro l'intervallo di refresh, senza riavvio.

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install                # profilo jar (default): fat jar eseguibile
mvn clean install -P war         # war per application server
```

Il profilo sceglie il packaging; il risultato è in `target/govpay-rt-batch.jar` o `target/govpay-rt-batch.war`. Il jar non contiene i driver JDBC, che si aggiungono a runtime (vedi [Esecuzione](#esecuzione)); il war usa il datasource dell'application server. La build genera il client dell'API pagoPA da `src/main/resources/bizEvents.yaml` e le classi paForNode da `src/main/resources/xsd`, esegue i test e l'analisi OWASP Dependency-Check delle dipendenze (esecuzione definita in `govpay-bom`); per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database e gli script di svecchiamento dei metadati, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

Il profilo `jar` va indicato insieme a `dist`: è quello che lascia i driver JDBC fuori dal jar.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot (es. `govpay.batch.finestra-temporale` → `GOVPAY_BATCH_FINESTRATEMPORALE`, `scheduler.rtRetrieveJob.fixedDelayString` → `SCHEDULER_RTRETRIEVEJOB_FIXEDDELAYSTRING`). Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Recupero delle ricevute

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.finestra-temporale` | `90` | Giorni, dalla data della rendicontazione, entro cui il recupero automatico la considera. Le rendicontazioni più vecchie ancora da recuperare non sono più ritentate: il batch ne scrive il numero nel log |
| `govpay.batch.recupero-puntuale.max-retries` | `3` | Tentativi della chiamata a pagoPA nel recupero puntuale, con attesa crescente da 2 a 10 secondi, in caso di errore HTTP o di rete (es. `429`) |
| `govpay.batch.recupero-puntuale.retention-giorni` | `7` | Giorni dopo cui le righe di `rt_recuperi` elaborate senza successo vengono cancellate |
| `scheduler.rtRetrieveJob.fixedDelayString` | `7200000` | Intervallo tra la fine di un'esecuzione e l'avvio della successiva, in ms (2 ore) |
| `scheduler.initialDelayString` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms |
| `govpay.batch.enabled` | `true` | Letta in `BatchProperties` ma non usata: per non schedulare il job usare il profilo `cron` o un profilo diverso da `default` |

### Invio delle ricevute a GovPay

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.url` | `http://localhost:8080/ws/` | URL del servizio SOAP paForNode di GovPay a cui inviare le ricevute (`paSendRTV2`); compare anche negli eventi del GDE |
| `govpay.auth.username` | `amministratore` | Utente per l'autenticazione Basic verso il servizio paForNode |
| `govpay.auth.password` | - | Password per l'autenticazione Basic verso il servizio paForNode |

L'autenticazione Basic è sempre inviata, anche con password vuota.

### Esecuzione e cluster

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.cluster-id` | `GovPay-RT-Batch` | Identificativo del nodo, registrato nelle esecuzioni e negli eventi del GDE; nel container è l'indirizzo IP |
| `govpay.batch.stale-threshold-minutes` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso è considerata bloccata e può essere superata |
| `govpay.batch.job-repository.jdbc.enabled` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, i job sono avviati dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `default` | `cron` per eseguire `rtRetrieveJob` una volta e terminare (vedi [Esecuzione](#esecuzione)) |
| `server.port` | `8080` (`10001` nel container) | Porta HTTP dell'API REST del batch e degli endpoint actuator |
| `spring.application.name` | `govpay-rt-batch` | Nome dell'applicazione nei log |
| `spring.main.banner-mode` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `Europe/Rome` | Fuso orario dell'applicazione e delle date scambiate con pagoPA |

### Connettori e client HTTP

URL e autenticazione dell'API pagoPA e del GDE non sono proprietà del batch: sono nei connettori di GovPay (tabella `connettori`), indicati dal campo `cod_connettore_recupero_rt` dell'intermediario e, per il GDE, dal connettore `govpay_gde_api`.

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.client.cache.enabled` | `false` | Carica i connettori all'avvio e li tiene in cache; la cache si svuota con `GET /api/batch/cache/clear` |
| `govpay.client.async.core-pool-size` | `10` | Thread minimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.max-pool-size` | `50` | Thread massimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.queue-capacity` | `100` | Eventi in coda in attesa di invio |
| `govpay.client.async.thread-name-prefix` | `async-http-` | Prefisso dei thread di invio |

I client verso pagoPA sono creati una volta per connettore e riusati: dopo una modifica del connettore in GovPay si svuotano con `GET /api/batch/cache/clear`.

### Database

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `spring.datasource.url` | - | URL JDBC del database di GovPay (obbligatoria) |
| `spring.datasource.jndi-name` | - | Nome JNDI del datasource, al posto di URL e credenziali (deploy del war) |
| `spring.datasource.username` | - | Utente del database |
| `spring.datasource.password` | - | Password del database |
| `spring.datasource.driver-class-name` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.hibernate.ddl-auto` | `none` (`create-drop` con database embedded) | Gestione dello schema: lasciare `none` o `validate`, lo schema è di GovPay |
| `spring.datasource.hikari.minimum-idle` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `10` | Connessioni massime del pool |
| `spring.datasource.hikari.connection-timeout` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi nel jar: vedi [Esecuzione](#esecuzione).

### Logging

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `logging.file.name` | - (solo console) | File di log; nel container `/var/log/govpay-rt/govpay-rt.log` |
| `logging.level.it.govpay` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id dell'esecuzione: il correlation id è quello della richiesta di avvio manuale, oppure generato per gli avvii schedulati |
| `govpay.logging.livelli-dinamici.enabled` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `60s` (variabile `LOG_LEVEL_REFRESH_INTERVAL`) | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | `it.govpay` | Prefissi dei logger modificabili da database |

### Monitoraggio

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `management.server.port` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` (variabile `MANAGEMENT_ENDPOINTS`) | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.health.db.enabled` | `true` | Include la connessione al database nell'health |
| `management.metrics.tags.application` | `govpay-rt-batch` | Tag che identifica il servizio nelle metriche |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL      | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema Spring Batch

Prima del primo avvio, nel database di GovPay devono esistere le tabelle dei metadati Spring Batch: si creano con lo script `sql/spring-batch/schema-<database>.sql` di `sql.zip`. L'immagine Docker lo esegue da sola se `GOVPAY_RT_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -Dgovpay.url=<url del servizio paForNode di GovPay> \
     -Dgovpay.auth.username=<utente> \
     -Dgovpay.auth.password=<password> \
     -jar target/govpay-rt-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo, esegue `rtRetrieveJob` ogni `scheduler.rtRetrieveJob.fixedDelayString` ms ed espone API REST e actuator |
| Esecuzione singola | `--spring.profiles.active=cron` | Esegue `rtRetrieveJob` una volta e termina: la pianificazione è affidata a un cron esterno. L'exit code è `0` anche se il job fallisce o è già in esecuzione su un altro nodo, `1` solo se non si riesce a superare un'esecuzione bloccata: l'esito va letto nei log |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -Dgovpay.url=<url del servizio paForNode di GovPay> \
     -Dgovpay.auth.username=<utente> \
     -Dgovpay.auth.password=<password> \
     -jar target/govpay-rt-batch.jar --spring.profiles.active=cron
```

### War su application server

Il war (profilo `war`) si installa su un servlet container Jakarta EE 11, per esempio Tomcat 11, e usa un datasource JNDI dell'application server, con il driver JDBC nelle librerie del server. Il datasource si indica con `spring.datasource.jndi-name`, le altre proprietà come per il jar, per esempio come proprietà di sistema in `JAVA_OPTS`. Il war risponde sotto il proprio context path (es. `/govpay-rt-batch/api/batch`, `/govpay-rt-batch/actuator/health`).

```bash
JAVA_OPTS="-Dspring.datasource.jndi-name=java:comp/env/jdbc/govpay \
           -Dgovpay.url=<url del servizio paForNode di GovPay> \
           -Dgovpay.auth.username=<utente> -Dgovpay.auth.password=<password>"
```

## Esecuzione con Docker

L'immagine è pubblicata su Docker Hub come [linkitaly/govpay-rt-batch](https://hub.docker.com/r/linkitaly/govpay-rt-batch) (release) e `linkitaly/govpay-rt-batch-dev` (sviluppo, a ogni push su `main`). Con lo scheduler interno il servizio risponde sulla porta `10001`, quella attesa da govpay-console-api; i driver JDBC si montano in `/opt/jdbc-drivers`.

```bash
docker run -d --name govpay-rt-batch -p 10001:10001 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  -e GOVPAY_URL=<url del servizio paForNode di GovPay> \
  -e GOVPAY_AUTH_USERNAME=<utente> \
  -e GOVPAY_AUTH_PASSWORD=<password> \
  linkitaly/govpay-rt-batch
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile (es. `GOVPAY_URL`, `GOVPAY_BATCH_FINESTRATEMPORALE=60`):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_RT_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_RT_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_RT_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: esecuzione singola con il profilo `cron`, senza server HTTP; il container termina a fine job e va pianificato dall'esterno (es. CronJob Kubernetes). Altrimenti scheduler interno |
| `GOVPAY_RT_BATCH_INTERVALLO_CRON` | no | `120` | Minuti tra un'esecuzione e l'altra con lo scheduler interno: l'entrypoint li converte in `SCHEDULER_RTRETRIEVEJOB_FIXEDDELAYSTRING`, sovrascrivendo un valore impostato direttamente |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database |
| `GOVPAY_RT_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle Spring Batch, se la tabella `GOVPAY_RT_DB_CHECK_TABLE` non esiste |
| `GOVPAY_RT_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_RT_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_RT_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_RT_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_RT_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_RT_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta la verifica della tabella e crea comunque lo schema. **Oggi con `TRUE` l'inizializzazione fallisce**: il comando SqlTool è definito solo dentro la verifica |
| `GOVPAY_RT_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi della verifica della tabella |
| `GOVPAY_RT_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_RT_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_RT_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_RT_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_RT_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_RT_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'identificativo del nodo (`govpay.batch.cluster-id`) è impostato dall'entrypoint, con la variabile `GOVPAY_BATCH_CLUSTER_ID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`) sono interne e non vanno impostate. Il log di avvio dell'entrypoint è in `/tmp/entrypoint_debug.log` dentro il container.

Scansione delle vulnerabilità dell'immagine: [docker/SCANSIONE.md](docker/SCANSIONE.md). Per costruire l'immagine in locale da un jar compilato: `cd docker && ./build_image.sh -l ../target/govpay-rt-batch.jar -t <repository>:<tag>`, dopo `mvn clean install -Pjar,dist` (lo script prende gli script SQL da `target/sql.zip`).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Per spostarli su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`. In alternativa i livelli si cambiano dalla console, tramite la tabella `configurazione`.

Con il profilo `cron` non c'è server HTTP: l'esito si ricava dall'exit code del processo e dai log.

## Avvio manuale via REST

Con lo scheduler interno il batch espone, sotto `/api/batch`, l'API comune ai batch GovPay (`AbstractBatchController` di `govpay-common`), la stessa che usa govpay-console-api. Non ha autenticazione: va esposta solo sulla rete interna.

| Metodo e path | Descrizione |
|---------------|-------------|
| `GET /api/batch/run` | Avvia `rtRetrieveJob` in modo asincrono (`202`, oppure `409` se è già in esecuzione) |
| `GET /api/batch/run?force=true` | Avvia `rtRetrieveJob` anche se risulta un'esecuzione in corso |
| `GET /api/batch/status` | Stato corrente di `rtRetrieveJob` |
| `GET /api/batch/lastExecution` | Ultima esecuzione completata |
| `GET /api/batch/nextExecution` | Prossima esecuzione stimata |
| `GET /api/batch/info` | Nome e descrizione del batch |
| `GET /api/batch/executions` | Elenco paginato delle esecuzioni |
| `GET /api/batch/executions/{executionId}` | Dettaglio di un'esecuzione |
| `DELETE /api/batch/executions/{executionId}` | Richiede l'arresto di un'esecuzione |
| `GET /api/batch/cache/clear` | Svuota la cache dei connettori e dei client verso pagoPA |

```bash
curl http://localhost:10001/api/batch/run
curl http://localhost:10001/api/batch/status
```

## Elaborazione

### Recupero automatico (`rtRetrieveTasklet`)

Step chunk-oriented, una rendicontazione per transazione:

- **Reader** (`RtRetrieveReader`): seleziona le rendicontazioni collegate a un singolo versamento, senza pagamento (`id_pagamento` nullo), con `esegui_recupero_rt` vero e data entro `govpay.batch.finestra-temporale` giorni.
- **Processor** (`RtRetrieveProcessor`): risale a dominio, stazione e intermediario, chiama l'API pagoPA con il connettore dell'intermediario e, se la ricevuta c'è, la invia a GovPay con `paSendRTV2`.
- **Writer** (`RtRetrieveWriter`): imposta `esegui_recupero_rt` a falso, anche quando l'invio a GovPay non riesce, salvo quando pagoPA risponde `404` (ricevuta non ancora disponibile): in quel caso la rendicontazione resta candidata e viene ritentata alle esecuzioni successive, finché esce dalla finestra temporale.

Un errore diverso dal `404` nella chiamata a pagoPA (es. `429`, `5xx`) o un dominio senza stazione, intermediario o connettore *Recupero RT* fanno fallire lo step e l'esecuzione: gli step successivi non vengono eseguiti, e la rendicontazione e quelle non ancora elaborate sono riprese all'esecuzione successiva.

### Recupero puntuale (`rtRecuperoPuntualeStep`)

Legge le righe di `rt_recuperi` con `esito` nullo, senza limite di età. Ogni riga è elaborata in modo indipendente, con i tentativi di `govpay.batch.recupero-puntuale.max-retries` verso pagoPA:

| Risultato | Effetto sulla riga |
|-----------|--------------------|
| Ricevuta recuperata e accettata da GovPay (anche `PAA_RECEIPT_DUPLICATA`) | Cancellata |
| Ricevuta non disponibile su pagoPA (`404`) | `esito = NON_DISPONIBILE` |
| Marca da bollo senza allegato nella ricevuta di pagoPA | `esito = MBT_ALLEGATO_MANCANTE` |
| Configurazione mancante, errore dopo i tentativi, invio a GovPay fallito | `esito = ERRORE` |

L'invio a GovPay porta l'header `GOVPAY-ON-BEHALF-OF` con il principal dell'operatore che ha chiesto il recupero (`rt_recuperi.id_operatore`). govpay-console-api legge l'esito dalla riga.

### Retention (`rtRecuperoRetentionStep`)

Cancella le righe di `rt_recuperi` con `esito` valorizzato e `data_ultimo_tentativo` più vecchia di `govpay.batch.recupero-puntuale.retention-giorni` giorni.

## Licenza

GovPay RT Batch è distribuito con licenza [GNU General Public License v3.0](LICENSE).
