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

Create a PostgreSQL database:

```sql
CREATE DATABASE dutch_learn_chat;
```

Update `src/main/resources/application.yml` with your database credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dutch_learn_chat
    username: your_username
    password: your_password
```

### Running the Application

```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8080`

### Development with H2 (In-Memory Database)

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
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

ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4-turbo
    language-level: A2-B1

spring:
  web:
    cors:
      allowed-origins: http://localhost:4200,http://localhost:3000
```

### Environment Variables

Create a `.env` file or set system environment variables:

```
OPENAI_API_KEY=your-api-key
JWT_SECRET=your-secret-key
```

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
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

**Database connection errors:**

- Ensure PostgreSQL is running
- Check database credentials in `application.yml`
- Or use H2 (dev profile) to avoid database setup
