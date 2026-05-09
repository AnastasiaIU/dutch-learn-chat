# Dutch Learn Chat - Backend README

## Setup Instructions

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 12+ (or H2 for development)

### Installation

```bash
cd backend
mvn clean install
```

### Development with PostgreSQL

**Option A: Docker (recommended)**

From the repo root:

```bash
docker compose up -d postgres
```

Then run the backend:

```bash
cd backend
mvn spring-boot:run
```

**Option B: Manual install**

Create a PostgreSQL database:

```sql
CREATE DATABASE dutch_learn_chat;
```

Update `src/main/resources/application.yml` with your database credentials:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/dutch_learn_chat}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

### Running the Application

```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8080`

### Development with H2 (In-Memory Database)

```bash
mvn spring-boot:run -D"spring-boot.run.arguments=--spring.profiles.active=dev"
```

Access H2 Console: `http://localhost:8080/h2-console`

### API Endpoints

**Authentication:**

- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `GET /api/auth/health` - Health check

**Chat:**

- `POST /api/chat/session` - Create new chat session
- `POST /api/chat/message` - Send message and get AI response
- `GET /api/chat/history/{sessionId}` - Get chat history
- `GET /api/chat/sessions/{userId}` - Get user sessions

**Model Evaluation:**

- `POST /api/evaluation/run` - Run suite and store report (Admin only)
- `GET /api/evaluation/messages` - List stored assistant responses + evaluation metadata (Admin only)

### Project Structure

```
src/
├── main/
│   ├── java/com/dutchlearn/
│   │   ├── DutchLearChatApplication.java
│   │   ├── config/           # Spring configuration
│   │   ├── controller/       # REST endpoints
│   │   ├── dto/              # Data Transfer Objects
│   │   ├── entity/           # JPA entities
│   │   ├── repository/       # Data access layer
│   │   ├── security/         # JWT and security
│   │   └── service/          # Business logic
│   └── resources/
│       ├── application.yml
│       └── application-dev.yml
└── test/
```

### Database Schema

The application uses Spring Data JPA with automatic schema creation. Key entities:

- **User:** User accounts
- **ChatSession:** Conversation sessions
- **ChatMessage:** Individual messages in sessions
- **Feedback:** User feedback on AI responses

### Configuration

Key settings in `application.yml`:

```yaml
jwt:
  secret: your-secret-key
  expiration: 86400000  # 24 hours

automation:
  n8n:
    api-key: ${N8N_API_KEY}

ai:
  provider: github
  language-level: A2-B1
  api-key: ${GITHUB_TOKEN:}
  model: meta-llama-3.1-8b-instruct
  # Models:
  # deepseek-r1
  # deepseek-r1-0528
  # deepseek-v3-0324
  # llama-3.2-11b-vision-instruct
  # llama-3.2-90b-vision-instruct
  # llama-3.3-70b-instruct
  # llama-4-maverick-17b-128e-instruct-fp8
  # llama-4-scout-17b-16e-instruct
  # meta-llama-3.1-405b-instruct
  # meta-llama-3.1-8b-instruct
  # ministral-3b
  # mistral-small-2503
  model-tag: ${AI_MODEL_TAG:baseline}
  temperature: 0.4
  max-tokens: 320
  request-timeout-seconds: 45
  mock:
    enabled: false
  prompt:
    version: v1-a2b1-guardrails
  rag:
    enabled: true
    knowledge-base-path: rag/dutch-learning-kb.json
    max-context-items: 3
    max-snippet-chars: 220

spring:
  web:
    cors:
      allowed-origins: http://localhost:4200,http://localhost:3000
```

### Environment Variables

Create a `.env` file in `backend/` or set system environment variables:

```
GITHUB_TOKEN=your-github-personal-access-token
JWT_SECRET=your-secret-key
N8N_API_KEY=local-dev-n8n-key
DB_URL=jdbc:postgresql://localhost:5432/dutch_learn_chat
DB_USERNAME=postgres
DB_PASSWORD=postgres
AI_MODEL_TAG=baseline
SEED_DEV_USERS=true
```

`GITHUB_TOKEN` must include GitHub Models access. If this permission is missing, model calls return `401 Unauthorized` with an error like "models permission is required".

### Development Test Accounts (dev profile)

- Learner A2: `a2@test.com` / `a2`
- Learner B1: `b1@test.com` / `b1`
- Admin: `admin@test.com` / `admin`

### Building

```bash
mvn clean package
```

Generates executable JAR in `target/dutch-learn-chat-1.0.0.jar`

### Running JAR

```bash
java -jar target/dutch-learn-chat-1.0.0.jar
```

### Testing

```bash
mvn test
```

### Run Model Evaluation Loop

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/evaluation/run"
```

This returns per-case quality checks and a summary pass rate.

### RAG Knowledge Base

- `src/main/resources/rag/dutch-learning-kb.json`

The app retrieves matching snippets from this file and injects them into the system prompt.

### Common Issues

**Port 8080 already in use:**

Find and kill the process using port 8080:

```powershell
# Find the process ID (PID) using port 8080
netstat -ano | findstr :8080

# Kill the process (replace <PID> with the actual process ID)
taskkill /PID <PID> /F
```

Then restart the Spring Boot application:

```bash
mvn spring-boot:run -D"spring-boot.run.arguments=--spring.profiles.active=dev"
```

**Database connection errors:**

- Ensure PostgreSQL is running
- Check database credentials in `application.yml`
- Or use H2 (dev profile) to avoid database setup
