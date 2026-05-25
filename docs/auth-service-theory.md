# Auth Service Theory Guide

This document explains the auth service built so far. Treat it as the learning notes behind the code: what each part does, why it exists, and how the full authentication flow works.

## 1. What The Auth Service Does

The auth service is responsible for identity and authentication.

It answers questions like:

- Who is this user?
- Can this user log in?
- Which login method did the user use?
- What role does this user have?
- Can this request be trusted?

It does not own profile, product, cart, order, or payment data. Those will belong to other services.

The auth service currently supports local email/password authentication. The design also prepares for future providers like Google, Facebook, and GitHub.

## 2. Authentication vs Authorization

Authentication means proving identity.

Example:

```text
This user is Amit because he entered the correct email and password.
```

Authorization means checking permission.

Example:

```text
Amit is logged in, but is he allowed to create products?
```

In code:

- Login is authentication.
- Role checks like `USER` or `ADMIN` are authorization.

## 3. Main Architecture

The service follows a simple layered structure:

```text
Controller -> Service -> Repository -> Database
```

Controller receives HTTP requests.

Service contains business logic.

Repository talks to the database.

Model represents database entities.

DTO represents API request and response data.

Security contains JWT and Spring Security-related code.

This is not full clean architecture yet, but it is clean enough for a first microservice version.

## 4. Important Packages

```text
controller
```

Contains REST APIs such as register, login, refresh, logout, validate, and me.

```text
service
```

Contains business logic such as registration, login, refresh token creation, and provider authentication.

```text
repository
```

Contains Spring Data JPA interfaces for database access.

```text
model
```

Contains database entities:

- `UserAccount`
- `UserAuthProvider`
- `RefreshToken`

```text
dto
```

Contains request and response objects:

- `RegisterRequest`
- `LoginRequest`
- `AuthResponse`
- `RefreshTokenRequest`
- `LogoutRequest`
- `CurrentUserResponse`
- `TokenValidationResponse`

```text
security
```

Contains JWT creation, JWT validation, JWT filter, and Spring Security configuration.

```text
factory`
```

Contains `AuthProviderFactory`, which selects the correct login provider.

```text
interfaces
```

Contains `AuthProvider`, the common contract for all login methods.

## 5. Database Design

The auth service currently uses three main tables.

### users

Backed by `UserAccount`.

Stores the core user identity:

```text
id
email
display_name
role
status
created_at
updated_at
```

This table says who the user is inside our system.

It does not store password.

### user_auth_providers

Backed by `UserAuthProvider`.

Stores how a user can log in:

```text
id
user_id
provider_type
provider_user_id
password_hash
created_at
updated_at
```

For local login:

```text
provider_type = LOCAL
provider_user_id = email
password_hash = BCrypt password hash
```

For Google login later:

```text
provider_type = GOOGLE
provider_user_id = Google sub
password_hash = null
```

This design lets one user have multiple login methods later.

### refresh_tokens

Backed by `RefreshToken`.

Stores long-lived refresh tokens as hashes:

```text
id
user_id
token_hash
expires_at
revoked
created_at
revoked_at
```

The raw refresh token is returned to the frontend. The database stores only its hash.

This is safer because if the database leaks, attackers do not directly get usable refresh tokens.

## 6. Registration Flow

Endpoint:

```text
POST /api/auth/register
```

Request:

```json
{
  "email": "test@example.com",
  "password": "secret123",
  "displayName": "Test User"
}
```

Flow:

```text
1. AuthController receives request.
2. @Valid checks DTO validation rules.
3. AuthService checks if email already exists.
4. AuthService creates UserAccount.
5. AuthService hashes password with BCrypt.
6. AuthService creates UserAuthProvider for LOCAL login.
7. JwtService creates access token.
8. RefreshTokenService creates refresh token and stores its hash.
9. AuthService returns AuthResponse.
```

Response:

```json
{
  "accessToken": "jwt_here",
  "refreshToken": "refresh_token_here",
  "tokenType": "Bearer"
}
```

## 7. Login Flow

Endpoint:

```text
POST /api/auth/login
```

Request:

```json
{
  "providerType": "LOCAL",
  "email": "test@example.com",
  "password": "secret123"
}
```

Flow:

```text
1. AuthController receives request.
2. AuthService asks AuthProviderFactory for provider matching providerType.
3. For LOCAL, factory returns LocalAuthProvider.
4. LocalAuthProvider finds UserAuthProvider by LOCAL + email.
5. LocalAuthProvider checks password using BCrypt.
6. LocalAuthProvider checks account status.
7. LocalAuthProvider returns AuthenticatedUser.
8. AuthService generates access token.
9. AuthService creates refresh token.
10. AuthService returns AuthResponse.
```

The important design idea:

```text
AuthService does not know the details of every login type.
Each provider handles its own login logic.
```

## 8. Strategy Pattern

The strategy pattern is used for login providers.

Interface:

```text
AuthProvider
```

Current implementation:

```text
LocalAuthProvider
```

Future implementations:

```text
GoogleAuthProvider
FacebookAuthProvider
GithubAuthProvider
```

Every provider must implement:

```java
AuthProviderType getProviderType();

AuthenticatedUser authenticate(LoginRequest request);
```

This avoids a large if-else block inside `AuthService`.

Bad design:

```java
if (provider == LOCAL) { ... }
else if (provider == GOOGLE) { ... }
else if (provider == FACEBOOK) { ... }
```

Better design:

```text
AuthProviderFactory selects the right provider.
The provider authenticates the user.
```

## 9. Factory Pattern

`AuthProviderFactory` stores all available providers in a map.

Example:

```text
LOCAL -> LocalAuthProvider
GOOGLE -> GoogleAuthProvider
```

When login request comes:

```java
AuthProvider provider = authProviderFactory.getProvider(request.getProviderType());
```

This keeps provider selection separate from authentication logic.

## 10. Password Hashing

Passwords are never stored directly.

During registration:

```text
plain password -> BCrypt hash -> database
```

During login:

```text
plain password + stored hash -> BCrypt comparison
```

The service uses `PasswordEncoder` with `BCryptPasswordEncoder`.

This is provided as a Spring bean in `PasswordConfig`.

## 11. JWT Access Tokens

JWT means JSON Web Token.

It has three parts:

```text
header.payload.signature
```

Example:

```text
xxxxx.yyyyy.zzzzz
```

The access token contains:

```text
sub = user id
email = user email
role = user role
iat = issued at
exp = expiration
```

The token is signed using the JWT secret.

Signing means:

```text
If someone changes the token payload, signature verification fails.
```

JWT is not encrypted. Anyone can decode and read the payload. Because of that, never put passwords, password hashes, or sensitive private data inside JWT.

Good JWT data:

```text
userId
email
role
expiry
```

Bad JWT data:

```text
password
password hash
private documents
payment details
```

## 12. Why Bearer Token Is Used

Protected requests send JWT like this:

```http
Authorization: Bearer <access-token>
```

`Bearer` tells the server that this authorization value is a bearer token.

The JWT filter checks:

```java
authHeader.startsWith("Bearer ")
```

Then it removes the `Bearer ` prefix and keeps only the token.

## 13. JWT Validation

`JwtService` can:

- generate access token
- extract user id
- extract email
- extract role
- check token expiry
- verify token signature

The parser code reads and verifies the JWT:

```java
Jwts.parser()
    .verifyWith(getSigningKey())
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

Conceptually:

```text
1. Use the signing key.
2. Verify token signature.
3. Read payload.
4. Return claims.
```

Claims are the values inside JWT payload.

## 14. JWT Authentication Filter

The JWT filter connects JWT to Spring Security.

Without the filter:

```text
JwtService can understand JWT,
but Spring Security does not know the request is authenticated.
```

With the filter:

```text
JWT is read from Authorization header.
JWT is validated.
User information is placed into Spring Security context.
Protected endpoints can work.
```

Flow:

```text
Request
  -> JwtAuthenticationFilter
  -> Spring Security
  -> Controller
```

The filter creates `AuthenticatedUserPrincipal`:

```text
userId
email
role
```

Then it creates a Spring Security authentication object and stores it:

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
```

This means:

```text
For this request, the user is authenticated.
```

## 15. Protected Current User Endpoint

Endpoint:

```text
GET /api/auth/me
```

This endpoint requires a valid access token.

Request:

```http
Authorization: Bearer <access-token>
```

Flow:

```text
1. JwtAuthenticationFilter validates token.
2. Filter stores AuthenticatedUserPrincipal in SecurityContext.
3. AuthController receives principal using @AuthenticationPrincipal.
4. Controller returns current user details.
```

Response:

```json
{
  "userId": "user-id",
  "email": "test@example.com",
  "role": "USER"
}
```

## 16. Refresh Tokens

Access tokens should be short-lived.

Example:

```text
15 minutes
```

If access token expires, the frontend should not ask the user to log in again every time. Instead, it sends a refresh token.

Refresh token:

```text
longer-lived token used to get a new access token
```

Current refresh token lifetime:

```text
7 days
```

Configured in:

```yaml
jwt:
  refresh-expiration-days: 7
```

## 17. Refresh Token Storage

The frontend receives the raw refresh token.

The database stores:

```text
SHA-256 hash of refresh token
```

During refresh:

```text
raw token from request -> hash -> find hash in DB
```

This is similar to password storage. We avoid storing the raw secret.

## 18. Refresh Token Flow

Endpoint:

```text
POST /api/auth/refresh
```

Request:

```json
{
  "refreshToken": "refresh_token_here"
}
```

Flow:

```text
1. Frontend sends refresh token.
2. RefreshTokenService hashes it.
3. Repository finds matching token hash.
4. Service checks token is not revoked.
5. Service checks token is not expired.
6. AuthService creates a new access token.
7. AuthService returns access token and same refresh token.
```

Response:

```json
{
  "accessToken": "new_jwt_here",
  "refreshToken": "same_refresh_token_here",
  "tokenType": "Bearer"
}
```

## 19. Logout Flow

Endpoint:

```text
POST /api/auth/logout
```

Request:

```json
{
  "refreshToken": "refresh_token_here"
}
```

Flow:

```text
1. Frontend sends refresh token.
2. RefreshTokenService validates it.
3. Service sets revoked = true.
4. Service sets revokedAt = current time.
5. Token can no longer be used for refresh.
```

Expected response:

```text
204 No Content
```

## 20. Expired vs Revoked

Expired and revoked are different.

Expired means:

```text
The token naturally became invalid because time passed.
```

Revoked means:

```text
The token was manually cancelled, usually by logout.
```

A refresh token can have:

```text
revoked = false
expires_at = past time
```

That token is still invalid because it is expired.

The code checks both:

```text
revoked?
expired?
```

## 21. Global Exception Handling

Instead of writing try-catch in every controller, the service uses:

```text
GlobalExceptionHandler
```

It catches exceptions and returns clean JSON.

Example duplicate email:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "Email already registered",
  "path": "/api/auth/register"
}
```

Validation errors are also handled globally.

If a request has invalid email or blank password, Spring throws `MethodArgumentNotValidException`, and the handler returns a clean error response.

## 22. Current Public Endpoints

These endpoints are public:

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/validate
POST /api/auth/refresh
POST /api/auth/logout
```

They are allowed in `SecurityConfig`.

## 23. Current Protected Endpoints

This endpoint requires a valid JWT:

```text
GET /api/auth/me
```

Any future endpoint not explicitly permitted will require authentication because of:

```java
.anyRequest().authenticated()
```

## 24. Security Configuration

`SecurityConfig` does these things:

- disables CSRF for stateless REST API
- disables server-side sessions
- marks public endpoints as public
- requires authentication for other endpoints
- registers `JwtAuthenticationFilter`

Stateless means:

```text
The backend does not remember login session.
Every request must bring its own token.
```

This is good for microservices because each service can validate tokens without relying on server memory.

## 25. Current Design Patterns Used

### Layered Architecture

```text
Controller -> Service -> Repository -> Database
```

### DTO Pattern

API request and response classes are separate from database entities.

### Repository Pattern

Spring Data JPA repositories abstract database operations.

### Strategy Pattern

Different login providers implement the same `AuthProvider` interface.

### Factory Pattern

`AuthProviderFactory` selects the correct provider based on `AuthProviderType`.

### Dependency Injection

Spring injects dependencies instead of manually creating objects with `new`.

## 26. How Google Login Will Fit Later

Google login will not replace the current system.

It will become another provider:

```text
GoogleAuthProvider implements AuthProvider
```

Flow:

```text
1. Frontend gets Google ID token.
2. Frontend sends providerType = GOOGLE and providerToken.
3. AuthProviderFactory returns GoogleAuthProvider.
4. GoogleAuthProvider verifies token with Google.
5. GoogleAuthProvider extracts Google sub, email, and name.
6. Auth service finds or creates local user.
7. Auth service returns our own access token and refresh token.
```

Other services will not care whether login came from local password or Google.

Other services only care about:

```text
our JWT
userId
role
```

## 27. Important Security Notes

Do not commit real secrets to GitHub:

```text
database password
jwt secret
google client secret
```

Use environment variables:

```yaml
password: ${DB_PASSWORD}
secret: ${JWT_SECRET}
```

Do not store raw passwords.

Do not store raw refresh tokens.

Do not put sensitive data inside JWT.

Use HTTPS in real deployments so tokens are not sent over plain HTTP.

## 28. Current Learning Milestone

At this point, the auth service can:

- register a user
- hash password
- store user and login provider
- log in with email/password
- generate JWT access token
- validate JWT access token
- protect endpoints using JWT filter
- return current authenticated user
- generate refresh token
- store refresh token hash
- refresh access token
- revoke refresh token on logout
- return clean API errors

This is a strong first version of an auth service for a microservices project.
