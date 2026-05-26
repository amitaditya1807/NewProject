# Auth Service Design

## Goal
Auth service handles user registration, login, token generation, and authentication provider support.

## Supported Login Methods - Version 1
- LOCAL email/password login
- GOOGLE login
- GITHUB login


## Future Login Methods
- Facebook

## Main Flow - Local Login
1. User sends email and password.
2. Auth service checks user credentials.
3. Auth service generates JWT.
4. Frontend uses JWT to access other services.

## Main Flow - Google Login
1. Frontend gets Google token.
2. Frontend sends Google token to auth service.
3. Auth service verifies token with Google.
4. Auth service finds or creates local user.
5. Auth service generates JWT.

## Main Classes
- AuthController
- AuthService
- AuthProvider
- LocalAuthProvider
- AuthProviderFactory
- JwtService
- UserAccount
- UserAuthProvider
- RefreshToken

## Database Tables
- users
- user_auth_providers
- refresh_tokens

User chooses login method
        |
        v
Auth Service uses correct login provider
        |
        v
Provider verifies identity
        |
        v
Auth Service creates/fetches local user
        |
        v
Auth Service issues JWT


User
 |
 | clicks Login with Google
 v
Frontend
 |
 | opens Google login
 v
Google
 |
 | returns Google token
 v
Frontend
 |
 | sends Google token
 v
Auth Service
 |
 | verifies token with Google
 v
Google
 |
 | user info
 v
Auth Service
 |
 | create/find user in DB
 | create app JWT
 v
Frontend
 |
 | uses app JWT
 v
Other Services


---
## Completed Features

- Local user registration
- Local email/password login
- BCrypt password hashing
- JWT generation
- JWT validation
- JWT authentication filter
- Protected `/api/auth/me` endpoint
- Global exception handling
- PostgreSQL persistence
- Refresh token generation
- Refresh token hashing
- Refresh token validation
- Logout with refresh token revocation
- Google login
- Google ID token verification
- Google account linking by email
- GitHub login
- GitHub OAuth code exchange
- GitHub account linking by email

## Current Auth Flow

1. User registers or logs in.
2. Auth service validates credentials.
3. Auth service generates JWT.
4. Frontend sends JWT using `Authorization: Bearer <token>`.
5. JWT filter validates token.
6. Protected endpoints can access current user.

## Current Endpoints

### Register
POST /api/auth/register

### Login
POST /api/auth/login

### Validate Token
GET /api/auth/validate

### Current User
GET /api/auth/me

---
## Refresh Token Flow

1. User logs in or registers.
2. Auth service returns an access token and refresh token.
3. Access token is short-lived.
4. Refresh token is stored in database as a hash.
5. Frontend sends refresh token to `/api/auth/refresh` when access token expires.
6. Auth service validates refresh token:
   - exists in database
   - not revoked
   - not expired
7. Auth service returns a new access token.
8. On logout, refresh token is marked as revoked.

### Refresh Token
POST /api/auth/refresh

### Logout
POST /api/auth/logout

---
---
## Next Feature: Google Login

### Goal
Allow users to log in using a Google account while still issuing our own access token and refresh token.

### Flow
1. Frontend gets Google ID token.
2. Frontend sends it to `/api/auth/login` with `providerType = GOOGLE`.
3. Auth service verifies the token with Google.
4. Auth service reads Google user id (`sub`), email, and name.
5. Auth service finds existing `UserAuthProvider` by `GOOGLE + sub`.
6. If not found, auth service creates `UserAccount` and `UserAuthProvider`.
7. Auth service returns access token and refresh token.

### New Classes
- GoogleAuthProvider
- GoogleTokenVerifier
- GoogleUserInfo