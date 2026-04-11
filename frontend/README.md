# Dutch Learn Chat - Frontend README

## Setup Instructions

### Prerequisites

- Node.js 20.19+
- npm 10+

### Installation

```bash
cd frontend
npm install
```

### Development Server

```bash
npm start
```

The app will be available at `http://localhost:4200`

### Build

```bash
npm run build
```

Production build output will be in `dist/dutch-learn-chat/`

### Project Structure

```
src/
├── app/
│   ├── auth/              # Authentication feature
│   │   └── services/
│   ├── chat/              # Chat feature
│   │   ├── components/
│   │   └── services/
│   └── shared/            # Shared utilities & components
├── environments/          # Environment configurations
├── assets/                # Static assets
├── index.html
├── main.ts                # Entry point
└── styles.scss            # Global styles
```

### Key Features

- **Standalone Components:** Uses Angular v21+ standalone API
- **Reactive Forms:** RxJS-based reactive patterns
- **HTTP Interceptors:** Easy to add authentication headers
- **Responsive Design:** Mobile-friendly UI

### Configuration

Update the API URL in `src/environments/environment.ts` to match your backend:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

### Testing

```bash
npm test
```
