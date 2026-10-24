# RelayFlow
AI Workflow Orchestration Platform

## Local development

### Prerequisites

- Java 25
- Maven 3.10 or the included Maven Wrapper
- PostgreSQL 18.6, running locally
- Python 3 for the supplied mock world and verification scripts

### Create the local database

Connect to the local PostgreSQL cluster as an administrator and create a dedicated application role and database. In `psql`, run:

```sql
CREATE ROLE relayflow LOGIN;
\password relayflow
CREATE DATABASE relayflow OWNER relayflow;
```

The `\password` command prompts for the role password. Do not commit that password or put it in source control.

### Configure and start

In PowerShell, set the database values and demo configuration for the current session. Replace the password and token placeholders locally:

```powershell
$env:RELAY_DB_URL = 'jdbc:postgresql://localhost:5432/relayflow'
$env:RELAY_DB_USER = 'relayflow'
$env:RELAY_DB_PASSWORD = '<local role password>'
$env:RELAY_DEMO_TOKEN = '<local demo token>'
$env:MOCK_WORLD_URL = 'http://localhost:9210'
mvn spring-boot:run
```

The app applies `src/main/resources/db/migration` through Flyway and inserts the four canonical seed workflows as Published. Seed inserts are conflict-safe and do not overwrite an existing workflow on restart. Set `RELAY_SEED_ENABLED=false` to disable seed insertion. `SERVER_PORT` overrides the default API port `8080`.

Run the mock world in another terminal:

```powershell
python scripts/mock_world.py --port 9210
```

Spring Boot health is available at `http://localhost:8080/actuator/health`.

### Current implementation status

The project currently has the Phase 1 persistence/configuration foundation and application bootstrap. Workflow APIs, validators, execution engine, worker, and console are still planned work; the API smoke test will not pass until those endpoints and worker behavior are implemented.
