# Dutch Learn Chat 🇳🇱

**AI-Powered Dutch Learning Chat for A2/B1 Learners**

An educational web application designed to help immigrants in the Netherlands practise Dutch conversation at A2/B1 language level in a safe, judgment-free environment.

## 📦 Deliverables Summary

### ✅ Implemented Features

- **User Authentication & Registration** - Email/password login with JWT tokens
- **Role-Based Access Control** - LEARNER and ADMIN roles with Spring Security
- **Chat Interface** - Real-time AI conversation at A2/B1 language level
- **Language Level Selection** - Users choose A2 or B1 during registration
- **AI Model Abstraction** - Multiple models supported, switchable via configuration
- **Admin Evaluation Dashboard** - Metrics screen for model comparison and evaluation
- **Database Layer** - PostgreSQL with JPA
- **Complete API** - All endpoints for auth, chat, and evaluation

### ⚠️ Partially Implemented / Placeholders

- **Vocabulary Tracking** - Infrastructure exists, highlighting not functional (time constraint)
- **Feedback System** - Data collection working, analysis/dashboard not implemented (time constraint)
- **RAG (Retrieval-Augmented Generation)** - Not implemented (complexity & time)
- **N8N Automation** - Configuration placeholder only, no active integration

### 📍 Key Implementation Locations

- **RBAC & Admin Access** - `backend/src/main/java/com/dutchlearn/config/SecurityConfig.java` (line 43: `.requestMatchers("/api/evaluation/**").hasRole("ADMIN")`)
- **Admin Evaluation Screen** - `frontend/src/app/admin/components/evaluation-admin.component.ts`
- **Model Switching** - `backend/src/main/resources/application.yml` (AI model configuration)
- **AI Abstraction** - `backend/src/main/java/com/dutchlearn/service/ai/` (provider pattern)

## 🏗️ Project Structure

```
dutch-learn-chat/
├── backend/                          # Spring Boot REST API (Java 21)
│   ├── src/main/java/com/dutchlearn/
│   │   ├── config/                  # Spring & security configuration
│   │   │   ├── SecurityConfig.java  # RBAC & JWT setup
│   │   │   └── AiConfig.java        # Model configuration
│   │   ├── controller/              # REST endpoints
│   │   │   ├── AuthController.java
│   │   │   ├── ChatController.java
│   │   │   └── ModelEvaluationController.java (admin)
│   │   ├── service/
│   │   │   ├── ai/                  # AI provider abstraction
│   │   │   ├── AuthService.java
│   │   │   └── ChatService.java
│   │   ├── entity/                  # JPA entities
│   │   └── security/                # JWT authentication
│   ├── pom.xml
│   └── README.md
├── frontend/                         # Angular v21+ web app (TypeScript)
│   ├── src/app/
│   │   ├── auth/                    # Authentication feature
│   │   │   ├── guards/              # auth.guard.ts, admin.guard.ts
│   │   │   └── services/
│   │   ├── chat/                    # Chat interface
│   │   │   ├── components/
│   │   │   └── services/
│   │   ├── admin/                   # Admin evaluation dashboard
│   │   │   └── components/
│   │   └── shared/                  # Shared utilities
│   ├── package.json
│   └── README.md
├── docs/
│   ├── DELIVERABLES.md              # Feature & implementation details
│   └── [additional documentation]
├── docker-compose.yml               # PostgreSQL setup
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

**PostgreSQL via Docker (recommended for local persistence):**

```bash
# From repo root
docker compose up -d postgres

# Run backend (defaults to DB_URL=jdbc:postgresql://localhost:5432/dutch_learn_chat)
cd backend
mvn spring-boot:run
```

**For PostgreSQL (manual install):**

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

## Data Attribution

This project uses the [NT2Lex](https://github.com/anaistack/NT2Lex) lexical resource for Dutch vocabulary data:

- **Citation:** Tack, Anaïs, François, Thomas, Desmet, Piet, and Fairon, Cédrick (2018). NT2Lex: A CEFR-Graded Lexical Resource for Dutch as a Foreign Language Linked to Open Dutch WordNet. *Proceedings of the Thirteenth Workshop on Innovative Use of NLP for Building Educational Applications*.
- **License:** [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)
- **Authors:** Anaïs Tack, Thomas François, Piet Desmet, Cédrick Fairon

## 🔧 Tech Stack

### Backend

- **Framework:** Spring Boot 4.0
- **Language:** Java 21
- **Database:** PostgreSQL
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


## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

**Copyright (c) 2026 Anastasiia Iurashchuk**

MIT License allows free use, modification, and distribution with attribution.

---

## ⚠️ Disclaimer

This is an educational prototype. Key notes:

- AI responses may contain errors
- Not certified for language assessment
- Designed for practice, not replacement for human tutors
