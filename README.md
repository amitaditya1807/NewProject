# Microservices Learning Project

This project currently has:

- `auth-service`: Java Spring Boot service for registration, login, JWT, refresh tokens, Google login, and GitHub login.
- `user-service`: Go Gin service for authenticated user profile data.
- `frontend/simple-pages`: small HTML pages for OAuth testing.

## Local Requirements

- Docker Desktop
- Java 17
- Go
- PowerShell

## Run Everything With Docker

Start Docker Desktop first.

Create your local environment file:

```powershell
Copy-Item .env.example .env
```

Open `.env` and fill in your local values. The `.env` file is ignored by Git and must not be committed.

From the project root:

```powershell
docker compose up --build
```

This starts:

- PostgreSQL on `localhost:55432`
- Auth service on `http://localhost:8081`
- User service on `http://localhost:8082`
- API gateway on `http://localhost:8080`

To run in the background:

```powershell
docker compose up -d --build
```

Check containers:

```powershell
docker compose ps
```

See logs:

```powershell
docker compose logs -f
```

See logs for one service:

```powershell
docker compose logs -f auth-service
docker compose logs -f user-service
docker compose logs -f postgres
```

Stop everything:

```powershell
docker compose down
```

Stop everything and delete database data:

```powershell
docker compose down -v
```

## Start Only PostgreSQL

From the project root:

```powershell
Copy-Item .env.example .env
```

Open `.env` and fill in your local values.

```powershell
docker compose up -d postgres
```

This starts one PostgreSQL container on local port `55432` and creates:

- `auth_db`
- `user_db`

Local database values come from `.env`. With the default local development values:

```text
host: localhost
port: 55432
username: postgres
password: value of POSTGRES_PASSWORD
```

Check it is running:

```powershell
docker compose ps
```

Stop it:

```powershell
docker compose down
```

Delete database data and start fresh:

```powershell
docker compose down -v
```

## Run Auth Service

In a new PowerShell terminal:

```powershell
cd services/auth-service/java
$env:DB_PASSWORD="your-local-postgres-password"
$env:JWT_SECRET="your-long-random-jwt-secret"
$env:GOOGLE_CLIENT_ID=""
$env:GOOGLE_CLIENT_SECRET=""
$env:GITHUB_CLIENT_ID=""
$env:GITHUB_CLIENT_SECRET=""
$env:GITHUB_REDIRECT_URI="http://127.0.0.1:5500/frontend/simple-pages/github-callback.html"
.\mvnw.cmd spring-boot:run
```

Auth service runs on:

```text
http://localhost:8081
```

## Run User Service

In another PowerShell terminal:

```powershell
cd services/user-service/go
$env:USER_SERVICE_PORT="8082"
$env:USER_DB_URL="postgres://postgres:your-local-postgres-password@localhost:55432/user_db?sslmode=disable"
$env:JWT_SECRET="your-long-random-jwt-secret"
go run .
```

User service runs on:

```text
http://localhost:8082
```

## Test The Flow

Register a local user:

```powershell
$registerBody = @{
  email = "localtest@example.com"
  password = "Password123!"
  displayName = "Amit Kumar"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/auth/register" `
  -ContentType "application/json" `
  -Body $registerBody
```

Login:

```powershell
$loginBody = @{
  providerType = "LOCAL"
  email = "localtest@example.com"
  password = "Password123!"
} | ConvertTo-Json

$auth = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody
```

Save the profile:

```powershell
$profileBody = @{
  fullName = "Amit Kumar"
  phoneNumber = "9999999999"
  dateOfBirth = "2000-07-18"
  avatarUrl = "https://example.com/avatar.png"
  bio = "Learning microservices"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8082/api/users/profile" `
  -Headers @{ Authorization = "Bearer $($auth.accessToken)" } `
  -ContentType "application/json" `
  -Body $profileBody
```

Get the profile:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8082/api/users/profile" `
  -Headers @{ Authorization = "Bearer $($auth.accessToken)" }
```

To test through the API gateway, use the same paths on port `8080`:

```text
POST http://localhost:8080/api/auth/register
POST http://localhost:8080/api/auth/login
POST http://localhost:8080/api/users/profile
GET  http://localhost:8080/api/users/profile
```

## Useful Docs

- [Auth service theory](docs/auth-service-theory.md)
- [Auth service design](docs/auth-service-design.md)
- [User service theory](docs/user-service-theory.md)
