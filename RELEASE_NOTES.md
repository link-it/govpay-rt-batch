# Release Notes

## 2.0.0 — 2026-07-10

Major release: migrazione dello stack a **Spring Boot 4.1 / Spring Framework 7 / Spring Batch 6** e a **Jackson 3**.

### Aggiornamenti dipendenze
- `govpay-bom` aggiornato a **2.0.1** (parent BOM) — porta Spring Boot **4.1.0**, Spring Framework **7.0.8** e Jackson **3.1.4**.
- `govpay-common` aggiornato a **2.0.0**.
- `hibernate-jpamodelgen`: aggiunta versione esplicita (`${hibernate.version}`) alla dipendenza `provided`, non più gestita dal BOM.

### Migrazione Spring Batch 6
- Riorganizzazione dei package `org.springframework.batch.core.*`: `Job`/`JobExecution`/`JobParameters*` → sottopackage `job`/`job.parameters`, `Step*`/`StepExecution` → `step`, i listener → `listener`, `ItemReader`/`ItemWriter`/`Chunk`/`ExecutionContext`/`RepeatStatus` → `org.springframework.batch.infrastructure.*`.
- `JobExplorer` rimosso: interrogazione dei job ora tramite `JobRepository` (`getJobInstances`/`getJobExecutions`).
- `JobLauncher` → `JobOperator` (`start(Job, JobParameters)`).
- `JobParametersInvalidException` → `InvalidJobParametersException`; eccezioni di restart/concorrenza spostate in `org.springframework.batch.core.launch`.
- `JobExecution.getJobId()` → `getJobInstanceId()`; `getId()` ora restituisce `long`.

### Migrazione Spring Boot 4
- `@EntityScan` spostato in `org.springframework.boot.persistence.autoconfigure`.
- Slice di test `@DataJpaTest` spostato in `org.springframework.boot.data.jpa.test.autoconfigure` (nuova dipendenza di test `spring-boot-data-jpa-test`).

### Migrazione Jackson 3 (`tools.jackson`)
- `ObjectMapper`, serializer/deserializer custom e feature migrati da `com.fasterxml.jackson` a `tools.jackson`.
- `RtApiClientConfig`: costruzione dell'`ObjectMapper` (ora immutabile) tramite `JsonMapper.builder()`; le feature enum sono state spostate in `EnumFeature`/`DateTimeFeature`.
- `RtApiService`: converter HTTP `MappingJackson2HttpMessageConverter` → `JacksonJsonHttpMessageConverter`.
- Client REST generato da OpenAPI: generato per Jackson 3 tramite le opzioni `useSpringBoot4=true` e `useJackson3=true` dell'`openapi-generator-maven-plugin`.

### Compatibilità
- **Breaking change**: aggiornamento major. Richiede runtime allineato a Spring Boot 4 / Jackson 3.
- Estensioni o integrazioni che dipendono dai vecchi package Spring Batch, da `JobLauncher`/`JobExplorer` o da `com.fasterxml.jackson.databind` lato applicazione devono essere aggiornate di conseguenza.

## 1.0.3 — 2026-05-12

Release di manutenzione: pulizia della configurazione di logging applicativa.

### Configurazione
- **Rimosse le direttive `logging.level.*` da `application.properties`**:
  - `logging.level.root=INFO`
  - `logging.level.it.govpay.rt.batch=DEBUG`
  - `logging.level.org.springframework.batch=DEBUG`
  - `logging.level.org.springframework.web.client=DEBUG`
  - `logging.level.com.fasterxml.jackson=DEBUG` (residuo di debug puntuale)

  La configurazione di logging è ora demandata al runtime (variabili d'ambiente, profili dedicati o configurazione esterna). Per ripristinare livelli verbosi in ambienti specifici, sovrascrivere via `LOGGING_LEVEL_*` o tramite profilo dedicato (`application-<profilo>.properties`). I file di test mantengono la propria configurazione di logging dedicata in `application-integration.properties`.

### Compatibilità
Nessuna breaking change a livello di codice. Possibile impatto operativo: chi si appoggiava ai livelli `DEBUG` hard-coded in produzione deve ora abilitarli esplicitamente via env var o configurazione esterna.

## 1.0.2 — 2026-05-06

Release di manutenzione: aggiornamento dipendenze GovPay e potenziamento della pipeline di build/release.

### Aggiornamenti dipendenze
- `govpay-bom` aggiornato a **1.1.3** (parent BOM).
- `govpay-common` aggiornato a **1.1.2**.

### Pipeline
- **SBOM CycloneDX**: aggiunto job `sbom` che genera l'SBOM aggregato (formati `json` + `xml`, schema 1.6) tramite `cyclonedx-maven-plugin`. Eseguito su push su `main`/tag o su richiesta esplicita (`vars.FORCE_SBOM_JOB`); disattivabile con `vars.DISABLE_SBOM_JOB`. L'SBOM viene incluso nel ZIP `release-reports` sotto `reports/sbom/`.
- **OSV Scanner**: aggiunto job `osv-scan` (Google OSV Scanner) eseguito su `main`/tag con fallimento bloccante. Il report SARIF è incluso nel ZIP `release-reports` sotto `reports/osv/`.
- **Cache OWASP Dependency-Check**: chiave basata sulla data e flag `NOUPDATE_FLAG` per saltare l'aggiornamento NVD quando la cache è della stessa giornata.
- **Workflow `refresh-owasp-db`**: aggiornamento notturno della cache NVD per ridurre la latenza dei job di build.
- **Reports ZIP unico**: tutti i report (OWASP, JaCoCo, OSV, licenze, SBOM) collezionati in `release-reports-<tag>.zip` allegato alla GitHub Release.
- **Bump action GitHub**: `actions/upload-artifact` e `actions/download-artifact` portati a v7.
- **Fix step "Zip SQL files"**: aggiunto `mkdir -p target` per creare la cartella di output prima dello zip (il job parte da un checkout pulito senza `target/`).

### Codice
- `GdeService`: aggiunto override di `getConfigurazioneComponente(ComponenteEvento, Giornale)` per allineamento al nuovo contratto di `AbstractGdeService` introdotto in `govpay-common` 1.1.2 (mapping `ComponenteEvento` → `GdeInterfaccia` tramite i getter del `Giornale`).
- Aggiunti script SQL di svecchiamento delle tabelle Spring Batch (`spring-batch-cleanup.sql`) per tutti i database supportati (PostgreSQL, MySQL, Oracle, SQL Server, HSQLDB).

### Compatibilità
Nessuna breaking change. Aggiornamento drop-in rispetto alla 1.0.1.
