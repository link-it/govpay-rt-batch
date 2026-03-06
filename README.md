<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - RT Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-rt-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-rt-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-rt-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-rt-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-rt-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Sommario

Batch Spring Boot per il recupero automatico delle Ricevute Telematiche (RT) da pagoPA tramite API REST.
Il sistema acquisisce le RT mancanti interrogando il Nodo dei Pagamenti e le riconcilia con i pagamenti esistenti nel database GovPay.

## Funzionalita' principali

- **Acquisizione RT**: Recupero automatico delle ricevute telematiche dal Nodo pagoPA
- **Riconciliazione**: Matching automatico con i pagamenti presenti nel database GovPay
- **Multi-database**: Supporto per PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, HSQLDB
- **Schedulazione**: Esecuzione periodica configurabile o trigger manuale
- **Retry automatico**: Gestione errori con retry e backoff esponenziale
- **Containerizzazione**: Immagine Docker pronta per il deploy

## Requisiti

- Java 21+
- Maven 3.6.3+
- Database supportato (PostgreSQL, MySQL, Oracle, SQL Server, H2)

## Compilazione

```bash
mvn clean install
```

## Esecuzione

I driver JDBC non sono inclusi nel fat JAR e devono essere forniti esternamente tramite la proprieta' `loader.path` (PropertiesLauncher).

```bash
# Creare la directory per i driver JDBC e copiarvi il driver del database utilizzato
mkdir -p jdbc-drivers/
# Esempio per PostgreSQL:
cp /path/to/postgresql-42.x.x.jar jdbc-drivers/

# Avvio applicazione
java -Dloader.path=./jdbc-drivers -jar target/govpay-rt-batch.jar

# Con profilo specifico
java -Dloader.path=./jdbc-drivers -jar target/govpay-rt-batch.jar --spring.profiles.active=prod
```

## Configurazione Docker

I driver JDBC devono essere montati nella directory `/opt/jdbc-drivers` del container (configurabile tramite `GOVPAY_DS_JDBC_LIBS`).

```bash
docker run -d \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=db-host:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=secret \
  -v /path/to/jdbc-drivers:/opt/jdbc-drivers \
  linkitaly/govpay-rt-batch:latest
```

## Database supportati

| Database | Versione minima |
|----------|-----------------|
| PostgreSQL | 9.6+ |
| MySQL | 5.7+ |
| MariaDB | 10.3+ |
| Oracle | 11g+ |
| SQL Server | 2016+ |
| HSQLDB/H2 | (sviluppo) |

## Documentazione

- **[ChangeLog](ChangeLog)** - Storia delle modifiche e release
- **[Wiki](https://github.com/link-it/govpay-rt-batch/wiki)** - Documentazione completa

## License

Questo progetto e' distribuito sotto licenza GPL v3. Vedere il file [LICENSE](LICENSE) per i dettagli.

## Contatti

- **Progetto**: [GovPay RT Batch](https://github.com/link-it/govpay-rt-batch)
- **Organizzazione**: [Link.it](https://www.link.it)

---

Questo progetto e' parte dell'ecosistema [GovPay](https://www.govpay.it) per la gestione dei pagamenti della Pubblica Amministrazione italiana tramite pagoPA.
