# Dutch Learn Chat 🇳🇱

**AI-Powered Dutch Learning Chat for A2/B1 Learners**

An educational web application designed to help immigrants in the Netherlands practise Dutch conversation at A2/B1 language level in a safe, judgment-free environment.

## 🎯 Project Goals

- Provide accessible, flexible Dutch conversation practice
- Support vocabulary learning with explanations and tracking
- Privacy-first design compliant with GDPR
- User-friendly interface with AI disclosure and feedback mechanisms

## 📋 Key Features

✅ **AI-Powered Web Chat** - Responds at A2/B1 Dutch level  
✅ **Vocabulary Support** - Highlighting, explanations, translations  
✅ **Personal Tracking** - Browser-side vocabulary tracking for privacy  
✅ **User Authentication** - Secure login and session management  
✅ **AI Disclosure** - Clear transparency about AI interaction  
✅ **Feedback Mechanism** - Users can flag confusing or incorrect responses  

## 🏗️ Project Structure

```
dutch-learn-chat/
├── backend/                 # Spring Boot REST API
│   ├── src/main/java/...
│   ├── pom.xml
│   └── README.md
├── frontend/                # Angular v21+ web app
│   ├── src/
│   ├── package.json
│   └── README.md
├── .gitignore
└── README.md
```

## 🚀 Quick Start

### Backend (Spring Boot)

```bash
cd backend

# Install dependencies (Maven handles this)
mvn clean install

# Run with H2 (in-memory database - no setup needed)
mvn spring-boot:run -D"spring-boot.run.arguments=--spring.profiles.active=dev"

# API will be available at http://localhost:8080
```

**For PostgreSQL:**

1. Create database: `CREATE DATABASE dutch_learn_chat;`
2. Update credentials in `src/main/resources/application.yml`
3. Run: `mvn spring-boot:run`

### Frontend (Angular)

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm start

# App will open at http://localhost:4200
```

## 🔧 Tech Stack

### Backend

- **Framework:** Spring Boot 4.0
- **Language:** Java 21
- **Database:** PostgreSQL / H2 (dev)
- **Security:** JWT Token Authentication
- **ORM:** Spring Data JPA
- **Build:** Maven

### Frontend

- **Framework:** Angular 21+
- **Language:** TypeScript
- **Styling:** SCSS
- **HTTP Client:** Angular HttpClient
- **State Management:** RxJS/BehaviorSubject
- **Build Tool:** Angular CLI

## 📡 API Endpoints

### Auth

- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `GET /api/auth/health` - Health check

### Chat

- `POST /api/chat/session` - Create new session
- `POST /api/chat/message` - Send message & get AI response
- `GET /api/chat/history/{sessionId}` - Get message history
- `GET /api/chat/sessions/{userId}` - Get user's sessions

## 🔐 Privacy & Ethics

This project prioritizes:

- **Data Minimisation** - Only collect what's necessary
- **Browser-side Storage** - Vocabulary tracking stored locally, not on server
- **AI Transparency** - Clear disclosure that users interact with AI
- **User Control** - Explicit feedback mechanism for responses
- **GDPR Compliance** - Netherlands-compliant data handling

## 📚 Documentation

- [Backend README](backend/README.md) - Setup, configuration, architecture
- [Frontend README](frontend/README.md) - Setup, building, folder structure

## 🛠️ Development Workflow

### Setting up both Backend and Frontend

1. **Terminal 1 - Backend (Port 8080):**

   ```bash
   cd backend
   mvn spring-boot:run -D"spring-boot.run.arguments=--spring.profiles.active=dev"
   ```

2. **Terminal 2 - Frontend (Port 4200):**

   ```bash
   cd frontend
   npm install
   npm start
   ```

3. Open browser to `http://localhost:4200`

### Testing the Integration

1. **Register:** Create a new account
2. **Login:** Authenticate with your credentials
3. **Start Chat:** Begin a conversation in Dutch or English
4. **Send Message:** The backend API responds with a mock Dutch response

## 🧪 Testing

### Backend

```bash
cd backend
mvn test
```

### Frontend

```bash
cd frontend
npm test
```

## 📝 Configuration Files

### Backend - `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dutch_learn_chat
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update

jwt:
  secret: ${JWT_SECRET:dev-secret-key}
  expiration: 86400000

ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

### Frontend - `src/environments/environment.ts`

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

**Copyright (c) 2026 Anastasiia Iurashchuk**

MIT License allows free use, modification, and distribution with attribution.

## ⚠️ Disclaimer

This is an educational prototype. Key notes:

- AI responses may contain errors
- Not certified for language assessment
- Vocabulary tracking is local (not synced across devices)
- Designed for practice, not replacement for human tutors

## 🔗 Resources

- [Angular Documentation](https://angular.io/docs)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [JWT Authentication](https://jwt.io/)
- [CEFR Language Levels](https://www.coe.int/en/web/common-european-framework-reference-languages)
- [Dutch Learning Resources](https://www.nt2.nl/)
