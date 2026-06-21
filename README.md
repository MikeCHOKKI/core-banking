# Core Banking

API bancaire modulaire avec **Event Sourcing**, **CQRS**, **Kafka** et **Redis**.

## 🏗 Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  banking-api │────▶│ banking-     │────▶│  banking-   │
│  (REST API)  │     │ account      │     │  ledger     │
│  JWT/OAuth2  │     │ (commands)   │     │ (event store│
└─────────────┘     └──────────────┘     │  + Kafka)   │
                                          └──────┬──────┘
┌─────────────┐     ┌──────────────┐            │
│  banking-    │◀────│ banking-     │◀───────────┘
│  query       │     │ transfer     │
│  (read model)│     │ (ACID tx)    │
│  CQRS        │     │ idempotence  │
└─────────────┘     └──────────────┘
```

## ✨ Fonctionnalités

- **Comptes** : Création, dépôt, retrait, gel, clôture — avec verrou optimiste (@Version)
- **Virements ACID** : Débit/crédit atomique avec verrou pessimiste, idempotence (Redis+PostgreSQL), contrôle multi-devises
- **Event Sourcing** : Toute mutation persiste dans un event store append-only (PostgreSQL JSONB + Kafka)
- **CQRS** : Projections temps réel via Kafka → read models (solde, historique)
- **Idempotence** : Double couche Redis + PostgreSQL pour la déduplication des requêtes
- **Sécurité** : OAuth2 / JWT, RBAC (CLIENT, ADMIN, AUDITOR)

## 🛠 Stack technique

| Composant | Technologie |
|-----------|-------------|
| Langage | Java 21 |
| Framework | Spring Boot 3.4.4, Spring Security 6, Spring Cloud Stream |
| Base de données | PostgreSQL 16 (JSONB, Flyway migrations) |
| Cache / Idempotence | Redis 7 |
| Messagerie | Kafka 7.7 (KRaft mode, sans Zookeeper) |
| API | REST, OpenAPI/Swagger, Problem Detail (RFC 9457) |
| Tests | JUnit 5, Testcontainers |
| Build | Maven, modules multi-projets |

## 🚀 Démarrage rapide

### Prérequis
- Java 21+
- Docker & Docker Compose
- Maven 3.8+

### Lancer l'infrastructure
```bash
docker compose up -d
```

### Compiler et lancer l'API
```bash
mvn clean compile
mvn spring-boot:run -pl banking-api
```

L'API est accessible sur `http://localhost:8080`

### Documentation Swagger
`http://localhost:8080/swagger-ui/index.html`

## 📦 Structure des modules

### banking-common
DTOs partagés, enums (`AccountStatus`, `TransactionType`), interface `EventPublisher`, interface `IdempotencyService`, entité `IdempotencyRecord`.

### banking-account
Entité `Account` (aggregate root avec @Version pour l'optimistic locking), `AccountRepository` (JPA + pessimistic write), `AccountCommandService` (création, dépôt, retrait, gel, clôture).

### banking-transfer
`TransferService` (virement ACID avec débit/crédit atomique), `RedisIdempotencyService` (double couche Redis+PostgreSQL), DTOs de requête/réponse.

### banking-ledger
`KafkaEventPublisher` (implémentation concrète avec StreamBridge + event store), `DomainEvent` (event store append-only avec JSONB), `EventStoreService`, migration Flyway V1.

### banking-query
`ProjectionConsumer` (Kafka → projections), `AccountBalance` (materialized view), `TransactionHistory`, `AccountQueryService`.

### banking-api
REST controllers, `SecurityConfig` (OAuth2/JWT), `JwtTokenProvider`, `GlobalExceptionHandler` (RFC 9457 Problem Detail), `OpenApiConfig`.

## 🔐 API Endpoints

| Méthode | Path | Rôle | Description |
|---------|------|------|-------------|
| POST | `/api/v1/accounts` | ADMIN | Créer un compte |
| GET | `/api/v1/accounts` | ADMIN, AUDITOR | Lister les comptes |
| GET | `/api/v1/accounts/{id}` | CLIENT, ADMIN, AUDITOR | Détail d'un compte |
| GET | `/api/v1/accounts/by-number/{number}` | CLIENT, ADMIN, AUDITOR | Recherche par numéro |
| POST | `/api/v1/accounts/{id}/deposit` | CLIENT, ADMIN | Dépôt |
| POST | `/api/v1/accounts/{id}/withdraw` | CLIENT, ADMIN | Retrait |
| POST | `/api/v1/accounts/{id}/freeze` | ADMIN | Geler un compte |
| POST | `/api/v1/accounts/{id}/close` | ADMIN | Clôturer un compte |
| GET | `/api/v1/accounts/{id}/balance` | CLIENT, ADMIN, AUDITOR | Solde (CQRS) |
| GET | `/api/v1/accounts/{id}/transactions` | CLIENT, ADMIN, AUDITOR | Historique (CQRS) |
| POST | `/api/v1/transfers` | CLIENT, ADMIN | Virement |
| GET | `/api/v1/ledger/events/{aggregateId}` | AUDITOR | Events d'un agrégat |
| GET | `/api/v1/ledger/stats` | AUDITOR | Stats de l'event store |

## 🧪 Tests

```bash
# Avec Docker en cours d'exécution
mvn test -pl banking-api -am
```

Les tests utilisent Testcontainers pour PostgreSQL, Kafka et Redis.

## 📋 Variables d'environnement

| Variable | Défaut | Description |
|----------|--------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/banking_db` | URL PostgreSQL |
| `DB_USER` | `bank` | Utilisateur DB |
| `DB_PASS` | `bank_pass` | Mot de passe DB |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Serveur Kafka |
| `REDIS_HOST` | `localhost` | Hôte Redis |
| `REDIS_PORT` | `6379` | Port Redis |
| `JWT_SECRET` | `change-me-in-production-use-a-rsa-key` | Clé secrète JWT (min 256-bit) |
| `SERVER_PORT` | `8080` | Port du serveur |
| `SPRING_PROFILES_ACTIVE` | `dev` | Profil actif |

## 📄 License

Propriétaire — Core Banking
