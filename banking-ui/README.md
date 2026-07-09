# Harbor Bank UI

React (Vite) front end for the banking platform.

## Local development

```bash
cd banking-ui
npm install
npm run dev
```

Opens on http://localhost:3001

Environment (optional):

```bash
export VITE_API_BASE=http://localhost:8080
export VITE_KEYCLOAK_BASE=http://localhost:8180
```

## Demo users

| User | Password | Roles |
|---|---|---|
| `demo.customer` | `password` | CUSTOMER |
| `demo.admin` | `password` | ADMIN, SUPPORT |
| `demo.support` | `password` | SUPPORT |

After login, the customer UI resolves the profile by email / external user id from `customer-service`.
