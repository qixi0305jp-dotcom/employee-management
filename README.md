# Employee Management API

Java / Spring Bootによる従業員・部署・ファイル管理のバックエンド学習プロジェクトです。認証・認可、キャッシュ、入力検証、テストを通じて、保守しやすいAPI実装を学んでいます。

## Project Overview

A backend portfolio project built around three business modules: **Employee**, **Department**, and **File**. It demonstrates layered API development, permission checks, Redis caching, and coordinated database/file operations.

**Setup limitation:** this repository does not include a complete database schema or migration history. A compatible schema and user/permission data must already be prepared; cloning the repository alone is not enough to run it from scratch. SQL files under `src/test/resources/sql` are test fixtures and cleanup scripts, not database bootstrap scripts.

## Features

- Login with BCrypt password verification and signed JWTs.
- Database-backed authorities and self-or-admin permission queries.
- Employee CRUD, partial updates, batch deletion, pagination, search, and department-related views.
- Department CRUD with operation-specific authorities.
- Redis-backed employee lookup with negative caching, lock ownership checks, and cache eviction after writes.
- File upload, paginated search, metadata lookup, download, and deletion with ownership checks.
- File path validation and disk cleanup when upload persistence fails.
- Input validation and centralized HTTP error handling.
- JSON responses using `Result<T>` (`code`, `message`, `data`); successful file downloads return a resource response instead.

## Tech Stack

| Component | Version / usage |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.0.7 |
| MyBatis Spring Boot Starter | 4.0.1 |
| MySQL | JDBC persistence through Connector/J; server version not documented |
| Redis | Spring Data Redis; server version not documented |
| Spring Security | Dependency version managed by Spring Boot |
| JWT | JJWT 0.12.6 |
| OpenAPI / Swagger UI | springdoc 3.0.0 |
| Maven | Wrapper distribution 3.9.16; wrapper 3.3.4 |
| Testing | JUnit, Mockito, Spring testing, MockMvc |

Other dependencies include Bean Validation and Lombok. Starter versions above should not be interpreted as the versions of all underlying libraries.

## Architecture & Project Structure

```text
HTTP request -> Security filter chain -> Controller -> Service -> Mapper -> MySQL
                                                       |
                                                       +-> Redis (Employee cache)
                                                       +-> Disk  (File storage)
```

Packages use technical layers rather than separate business-module packages:

```text
src/main/java/com/example/employeemanagement/
  common/       Result response wrapper
  config/       Security and OpenAPI configuration
  controller/   HTTP endpoints
  dto/          Input models and validation
  entity/       Persistence models
  exception/    Exceptions and HTTP error handling
  mapper/       MyBatis mapper interfaces
  security/     JWT filter and authentication entry point
  service/      Business logic, caching, file operations
  util/         JWT utility
  vo/           Response models
src/main/resources/
  application*.properties
  mapper/       MyBatis XML mappings
src/test/java/com/example/employeemanagement/
  config/ controller/ service/ mapper/ util/ integration/
src/test/resources/
  application-test.properties
  sql/          Test fixtures and cleanup scripts
test.http       Manual API request examples
```

## Security

- `POST /users/login` is public. Business endpoints require authentication.
- Requests use `Authorization: Bearer <token>`. The filter verifies the JWT, checks the user identity, and loads the current role and permissions from the database.
- Employee reads require `employee:view`; writes require `employee:add`, `employee:update`, or `employee:delete`. The ADMIN role does **not** bypass these authority checks.
- `/employees/my-scope` applies department scope to ordinary users and allows ADMIN to query all employees. Other employee read endpoints should not be described as automatically department-scoped.
- Department operations require the corresponding `department:view/add/update/delete` authority.
- `/users/{username}/permissions` allows the authenticated user to query themselves, or ADMIN to query another user.
- Ordinary users can list, view, download, and delete their own files. ADMIN can access all files.
- Authentication failures use HTTP 401; access denial uses HTTP 403. Business validation failures use HTTP 400 with the existing Result error envelope.

There is no registration, refresh-token, or logout API. Do not commit credentials, signing keys, or real tokens.

## Configuration & Profiles

No profile is activated in the repository. Select one environment explicitly; do not combine local, test, and prod profiles.

| Profile | Database / Redis | Storage and logs | Swagger |
| --- | --- | --- | --- |
| `local` | localhost MySQL `employee_management`; localhost Redis DB0 | `uploads`; `logs/employee-management.log` | Enabled |
| `test` | localhost MySQL `employee_management_test`; localhost Redis DB1 | `target/test-uploads`; `target/test-logs/employee-management.log` | Disabled |
| `prod` | External environment variables | External absolute paths | Disabled |

Common settings are in [application.properties](src/main/resources/application.properties). Environment settings are in [local](src/main/resources/application-local.properties), [prod](src/main/resources/application-prod.properties), and [test](src/test/resources/application-test.properties). The test profile file is a test resource, not a deployment profile packaged with the application.

### Environment variables

| Environment | Required | Optional |
| --- | --- | --- |
| local | `DB_PASSWORD`, `JWT_SECRET` | `DB_USERNAME` (default `root`), `JWT_EXPIRATION_MS` (default `3600000`) |
| test (full suite) | `TEST_DB_PASSWORD`, `TEST_REDIS_PASSWORD` | `TEST_DB_USERNAME` (default `root`) |
| prod | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_DATABASE`, `UPLOAD_DIR`, `LOG_FILE`, `JWT_SECRET` | `JWT_EXPIRATION_MS` (default `3600000`) |

Passwords have no fallback. `TEST_DB_PASSWORD` never falls back to `DB_PASSWORD`. Tests use an explicitly marked public signing-key fixture and a fixed one-hour JWT duration.

JWT expiration is measured in **milliseconds** and must be greater than zero. Provide a strong signing key suitable for JJWT HMAC signing (at least 32 UTF-8 bytes); never reuse the public test fixture outside tests.

Local/test JDBC URLs contain local-development options. Prod instead requires `sslMode=VERIFY_IDENTITY`, including a trusted server certificate and matching hostname. Redis password configuration alone does not establish encrypted transport.

`UPLOAD_DIR` and `LOG_FILE` must be absolute paths when deploying. Local relative paths resolve from the working directory. Logs use INFO for the application package, 10 MB rolling files, 30-day history, and a 1 GB history size cap.

`.env` and private HTTP environment files are ignored, but the application does not automatically load `.env` files. Supply variables through your shell, IDE, or runtime environment.

## Getting Started

### Prerequisites

1. Install JDK 25. The Maven Wrapper can obtain its configured Maven distribution when network access is available.
2. Prepare local MySQL at port 3306 with a compatible `employee_management` schema. Complete schema creation instructions are currently unavailable in this repository.
3. Prepare a login user with a BCrypt password and the necessary role/permission relationships. No ready-to-use public login credentials are provided.
4. Start local Redis at port 6379, reserving DB0 for development and DB1 for integration tests. Local configuration currently assumes no Redis password.
5. Supply `DB_PASSWORD` and `JWT_SECRET` securely to the process; optionally supply `DB_USERNAME`. Prefer a database account limited to the required application operations.

### Start from the repository root

The following commands assume the required secrets are already present in the process environment. They contain no credential values.

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Unix shell:

```sh
sh mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The Unix example uses `sh mvnw` because the current Git executable bit is not set. No permission change is required for this command.

With the default port, local API documentation is available at:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Use login to obtain your own token, then authorize requests in Swagger. [test.http](test.http) provides manual request examples using private variables. Its private HTTP variables do not configure Spring environment variables. Adjust example resource IDs before executing write requests.

## API Overview

| Module | Method and path | Purpose |
| --- | --- | --- |
| User | `POST /users/login` | Login |
| User | `GET /users/{username}/permissions` | Self-or-ADMIN permission lookup |
| Employee | `GET /employees`, `GET /employees/{id}` | List / lookup |
| Employee | `POST /employees` | Create |
| Employee | `PUT /employees/{id}`, `PATCH /employees/{id}` | Full / partial update |
| Employee | `DELETE /employees/{id}`, `DELETE /employees/batch` | Single / batch deletion |
| Employee | `GET /employees/page`, `/employees/search`, `/employees/search-page` | Pagination / filtering |
| Employee | `GET /employees/with-department`, `/employees/search-with-department`, `/employees/{id}/detail` | Department-related views |
| Employee | `GET /employees/my-scope` | Department-scoped lookup |
| Department | `GET/POST /departments`, `GET/PUT/DELETE /departments/{id}` | Department CRUD |
| File | `POST /files/upload` | Multipart upload using the `file` part |
| File | `GET /files` | Paginated list with optional keyword |
| File | `GET /files/{id}`, `GET /files/{id}/download` | Metadata / download |
| File | `DELETE /files/{id}` | Delete metadata and disk file |

See Security above for access rules, and use local Swagger and [test.http](test.http) for request details. File pagination defaults to page 1 and size 10, allows sizes 1–100, and rejects offsets exceeding the supported integer range. Employee and File pagination retain their own response models.

## Testing

- `controller`: MockMvc request validation, authorization, and HTTP contracts.
- `service`: direct business tests with mocked dependencies, including cache, unlock, upload compensation, and ownership behavior.
- `mapper`: MyBatis parameter binding and dynamic XML SQL tests without a database.
- `config` and `util`: configuration contracts and JWT behavior.
- `integration`: real MySQL and Redis tests. The application-context smoke test remains in the root test package.

The current version's complete test suite was run in IntelliJ IDEA and confirmed to pass in the local test environment: **326 tests passed, 0 failed**. This is a full-suite result, not a test-coverage percentage or production-environment validation.

The run includes passing MySQL integration tests, Redis integration tests, and `EmployeeManagementApplicationTests`.

For a small non-external configuration/JWT check:

```powershell
.\mvnw.cmd "-Dtest=DatabasePasswordConfigurationTest,ProfileConfigurationTest,JwtUtilTest" test
```

```sh
sh mvnw -Dtest=DatabasePasswordConfigurationTest,ProfileConfigurationTest,JwtUtilTest test
```

MySQL and Redis integration tests have been **verified passing in the local test environment**. They require a compatible `employee_management_test` database and `TEST_DB_PASSWORD`; Redis integration additionally requires isolated Redis DB1 and `TEST_REDIS_PASSWORD`. Preserve the existing database guards, transactional rollback, and owned-key cleanup. Redis DB1 is logical isolation within an instance, not a separate server.

Do not run the full suite against an unverified environment. Missing credentials are not a reason to restore hardcoded passwords or point tests at development data.

## Notes & Limitations

- A complete schema/migration and first-user bootstrap process are not included. Test fixture SQL assumes existing tables.
- No production deployment, CI/CD pipeline, or Docker deployment is demonstrated by this repository. No measured test-coverage percentage is provided.
- Production MySQL TLS, Redis authentication, filesystem permissions, and runtime environment behavior remain unverified here.
- Stable File ordering uses `upload_time DESC, id DESC`; OFFSET pagination does not prevent drift during concurrent inserts/deletes or solve deep-page performance costs.
- Redis cache operations and database writes are not one distributed transaction. Failure handling and TTLs define an accepted consistency boundary.
- Upload extension/MIME checks and path validation are not malware scanning. Database rollback does not automatically roll back filesystem or Redis operations.
- Keep credentials, tokens, local private configuration, uploads, and logs out of version control. Do not publish the original private learning history without a separate history/security review.
