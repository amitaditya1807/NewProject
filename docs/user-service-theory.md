# User Service Theory

This document explains the user service we built in Go.

The goal of this service is simple:

- auth service handles identity and login
- user service handles profile data

This separation is one of the main ideas in microservices.

## What User Service Does

User service stores profile information for a user.

Examples:

- full name
- phone number
- date of birth
- avatar URL
- bio

User service does not store passwords.

User service does not handle Google login.

User service does not handle GitHub login.

Those things belong to auth service.

## Auth Service vs User Service

Auth service owns identity.

It stores:

- user email
- password hash for local login
- Google provider id
- GitHub provider id
- role
- account status
- refresh tokens

User service owns profile.

It stores:

- auth user id
- full name
- phone number
- date of birth
- avatar URL
- bio

The connection between both services is:

```text
auth-service user id = user-service user_id
```

In JWT, this auth user id is stored inside the `sub` claim.

Example:

```json
{
  "sub": "0f983cdc-8a2f-4f1c-a9f1-47c78549e162",
  "email": "localtest@example.com",
  "role": "USER"
}
```

Here, `sub` is the user id.

## Current Request Flow

The full flow is:

```text
Frontend
-> Auth service login
-> Auth service returns access token
-> Frontend calls user service with Authorization header
-> User service JWT middleware validates token
-> Middleware extracts user id from token
-> Handler reads user id
-> Service validates profile rules
-> Repository saves profile in PostgreSQL
-> Handler returns JSON response
```

## Authorization Header

User service receives token like this:

```http
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
```

The word `Bearer` means:

```text
I am carrying this token as proof of authentication.
```

The token comes from auth service.

User service trusts the token only after verifying it with the same JWT secret.

## JWT Middleware

Middleware is code that runs before the handler.

In our service:

```text
Request -> AuthMiddleware -> Handler
```

The middleware does these steps:

1. Reads the `Authorization` header.
2. Checks that it starts with `Bearer`.
3. Extracts the token string.
4. Parses the JWT.
5. Verifies the JWT signature using `JWT_SECRET`.
6. Reads the `sub` claim.
7. Stores `sub` as `userId` in Gin context.
8. Allows the request to continue.

This line stores user id:

```go
ctx.Set("userId", userID)
```

This line allows handler to run:

```go
ctx.Next()
```

If token is missing or invalid, middleware stops the request:

```go
ctx.Abort()
```

## Why ctx.GetString Worked

In middleware:

```go
ctx.Set("userId", userID)
```

This stores a value in Gin's request context.

In handler:

```go
userID := ctx.GetString("userId")
```

This reads that value back as a string.

So the handler no longer needs:

```go
ctx.GetHeader("UserId")
```

That was only for temporary manual testing.

The correct production-style flow is:

```text
Authorization token -> middleware -> ctx.Set("userId") -> handler -> ctx.GetString("userId")
```

## Architecture Used

The user service follows layered architecture:

```text
Handler -> Service -> Repository -> Database
```

Each layer has a clear job.

## Handler Layer

Handler is like a controller in Spring Boot.

It handles HTTP.

Responsibilities:

- read request body
- read current user id
- call service
- return JSON response

Example:

```go
func (handler *UserProfileHandler) SaveProfile(ctx *gin.Context)
```

This function handles:

```http
POST /api/users/profile
```

Important handler functions:

```go
ctx.ShouldBindJSON(&request)
```

Reads JSON body into a Go struct.

```go
ctx.JSON(http.StatusOK, response)
```

Sends JSON response.

```go
ctx.GetString("userId")
```

Reads user id placed by middleware.

## Service Layer

Service contains business logic.

Business logic means rules of the application.

In our user service:

- user id is required
- full name is required
- full name should be trimmed

Example:

```go
profile.FullName = strings.TrimSpace(profile.FullName)
```

This removes extra spaces before and after the name.

Example:

```go
if profile.FullName == "" {
    return nil, fmt.Errorf("full name is required")
}
```

This protects the database from bad profile data.

## Repository Layer

Repository talks to PostgreSQL.

It contains SQL.

The service does not know SQL.

The handler does not know SQL.

Only repository knows SQL.

This is called the Repository Pattern.

Example:

```go
repository.db.QueryRow(ctx, query, profile.UserID)
```

This sends a query to PostgreSQL and expects one row back.

Example:

```go
Scan(&profile.ID, &profile.UserID)
```

This copies database columns into Go struct fields.

## Model Layer

Model represents internal business data.

Our main model is:

```go
type UserProfile struct {
    ID          string
    UserID      string
    FullName    string
    PhoneNumber string
    DateOfBirth *time.Time
    AvatarURL   string
    Bio         string
    CreatedAt   time.Time
    UpdatedAt   time.Time
}
```

This is used inside service and repository.

## DTO Layer

DTO means Data Transfer Object.

DTO is the shape of request and response JSON.

Example:

```go
type SaveUserProfileRequest struct {
    FullName string `json:"fullName"`
}
```

Go field:

```go
FullName
```

JSON field:

```json
"fullName"
```

The backtick part is called a struct tag:

```go
`json:"fullName"`
```

Gin uses it while reading and writing JSON.

## Database Table

The service creates this table automatically:

```sql
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30),
    date_of_birth DATE,
    avatar_url TEXT,
    bio TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Important columns:

`id` is profile id.

`user_id` is auth-service user id.

`user_id UNIQUE` means one auth user can have only one profile.

## Upsert

The save query uses:

```sql
ON CONFLICT (user_id)
DO UPDATE SET ...
```

This means:

- if profile does not exist, insert it
- if profile already exists, update it

This behavior is called upsert.

## Important Go Concepts

### Pointer

`*` means pointer to a value.

Example:

```go
profile *model.UserProfile
```

This means `profile` stores the address of a `UserProfile`.

### Address

`&` means address of.

Example:

```go
&request
```

This gives the memory address of `request`.

Gin needs this address so it can fill request fields from JSON.

### Method Receiver

Example:

```go
func (handler *UserProfileHandler) SaveProfile(ctx *gin.Context)
```

This means `SaveProfile` belongs to `UserProfileHandler`.

It is similar to a method inside a Java class.

### Context

There are two important contexts:

Gin context:

```go
ctx *gin.Context
```

Used for HTTP request and response.

Request context:

```go
ctx.Request.Context()
```

Used for cancellation and database calls.

### Constructor Function

Example:

```go
func NewUserProfileService(repository *repository.UserProfileRepository) *UserProfileService
```

This creates a service object.

This is manual dependency injection.

## Manual Dependency Injection

In `main.go`, we connect layers manually:

```go
userProfileRepository := repository.NewUserProfileRepository(database)
userProfileService := service.NewUserProfileService(userProfileRepository)
userProfileHandler := handler.NewUserProfileHandler(userProfileService)
```

Flow:

```text
database -> repository -> service -> handler
```

This makes dependencies clear.

## Current APIs

Health check:

```http
GET /health
```

Get current user's profile:

```http
GET /api/users/profile
Authorization: Bearer accessToken
```

Create or update profile:

```http
POST /api/users/profile
Authorization: Bearer accessToken
Content-Type: application/json
```

Body:

```json
{
  "fullName": "Amit Kumar",
  "phoneNumber": "9999999999",
  "dateOfBirth": "2000-07-18",
  "avatarUrl": "https://example.com/avatar.png",
  "bio": "Learning microservices"
}
```

## Current System Design Patterns Used

Layered architecture:

```text
Handler -> Service -> Repository -> Database
```

Repository Pattern:

```text
Repository hides SQL from service and handler.
```

Service Layer Pattern:

```text
Service keeps business rules separate from HTTP and SQL.
```

Middleware Pattern:

```text
JWT middleware checks authentication before protected handlers.
```

DTO Pattern:

```text
Request and response JSON objects are separate from database model.
```

Manual Dependency Injection:

```text
main.go creates and connects dependencies clearly.
```

Context Pattern:

```text
Context carries request lifetime and user id through request flow.
```

## What We Should Improve Later

Possible next improvements:

- better error type for profile not found
- validation for phone number
- separate create and update endpoints if needed
- Docker Compose for auth DB and user DB
- API gateway
- user-service integration tests
- migration tool instead of simple startup migration

For learning, the current version is good because it is simple and clear.
