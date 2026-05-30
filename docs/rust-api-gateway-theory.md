# Rust API Gateway Theory

This document explains the Rust API gateway in this project: what problem it solves, how the code works, how Docker networking changes the URLs, and what should be improved before calling it production-grade.

## 1. What An API Gateway Is

In a microservices system, the frontend should not need to know every backend service address.

Without a gateway:

```text
Angular frontend -> auth-service :8081
Angular frontend -> user-service :8082
Angular frontend -> future-service :8083
```

That becomes messy as the system grows. The frontend has to know too many ports, too many service names, and too many backend details.

With an API gateway:

```text
Angular frontend -> api-gateway :8080
api-gateway -> auth-service :8081
api-gateway -> user-service :8082
```

So the frontend has one backend entry point:

```text
http://localhost:8080
```

The gateway decides where each request goes.

## 2. What Our Gateway Does Today

Our Rust gateway currently has three responsibilities:

```text
GET /health
  -> handled directly by gateway

/api/auth/*
  -> forwarded to auth-service

/api/users/*
  -> forwarded to user-service
```

Example:

```text
POST http://localhost:8080/api/auth/login
```

is forwarded to:

```text
POST http://auth-service:8081/api/auth/login
```

inside Docker.

Another example:

```text
GET http://localhost:8080/api/users/profile
```

is forwarded to:

```text
GET http://user-service:8082/api/users/profile
```

inside Docker.

The gateway is not doing login itself. It is a reverse proxy. It forwards the request and returns the upstream response.

## 3. Project Files

The gateway lives here:

```text
api-gateway/rust
```

Important files:

```text
api-gateway/rust/src/main.rs
api-gateway/rust/Cargo.toml
api-gateway/rust/Dockerfile
```

Docker Compose wires it here:

```text
docker-compose.yml
```

Relevant Compose service:

```yaml
api-gateway:
  build:
    context: ./api-gateway/rust
  container_name: api-gateway
  restart: unless-stopped
  environment:
    API_GATEWAY_PORT: 8080
    AUTH_SERVICE_URL: http://auth-service:8081
    USER_SERVICE_URL: http://user-service:8082
  ports:
    - "8080:8080"
  depends_on:
    - auth-service
    - user-service
```

## 4. Why Rust

Rust is a good fit for an API gateway because it gives:

- Fast runtime performance.
- Low memory usage.
- Strong compile-time safety.
- Good async networking through Tokio.
- Modern web libraries like Axum.

For this project, Rust is mainly useful as a learning gateway:

```text
Angular frontend
  -> Rust gateway
  -> Java auth-service
  -> Go user-service
  -> Postgres
```

This gives the project a realistic polyglot microservices shape.

## 5. Dependencies

`Cargo.toml`:

```toml
[dependencies]
axum = "0.8"
tokio = { version = "1", features = ["full"] }
tower-http = { version = "0.6", features = ["cors", "trace"] }
reqwest = { version = "0.12", default-features = false, features = ["json", "rustls-tls"] }
```

### axum

Axum is the HTTP server framework.

It gives us:

```rust
Router
route()
get()
any()
State
```

In simple terms, Axum listens for HTTP requests and calls the correct Rust function.

### tokio

Tokio is the async runtime.

HTTP servers must handle many requests at the same time. Instead of blocking one thread per request, Tokio lets Rust handle async network work efficiently.

This line starts the app inside the Tokio runtime:

```rust
#[tokio::main]
async fn main() {
```

### tower-http

We use `tower-http` for CORS:

```rust
use tower_http::cors::CorsLayer;
```

CORS is required because Angular runs at:

```text
http://127.0.0.1:4200
```

and the gateway runs at:

```text
http://localhost:8080
```

Browsers treat those as different origins. Without CORS, browser requests can be blocked before they reach our services.

### reqwest

Reqwest is the HTTP client used by the gateway to call backend services.

Example:

```rust
http_client.request(method, target_url).body(body)
```

Important detail:

```toml
default-features = false
features = ["json", "rustls-tls"]
```

We disabled default features so Reqwest does not require OpenSSL in Docker. We use `rustls-tls`, which avoids the OpenSSL build issue we hit earlier.

## 6. Main Code Structure

The gateway code starts with imports:

```rust
use axum::{
    body::Bytes,
    extract::State,
    http::{HeaderMap, Method, StatusCode, Uri},
    response::{IntoResponse, Response},
    routing::{any, get},
    Json, Router,
};
use reqwest::Client;
use std::{collections::HashMap, env};
use tower_http::cors::CorsLayer;
```

These imports tell us what the gateway needs:

- `Bytes`: raw request body.
- `State`: shared app state.
- `HeaderMap`: incoming HTTP headers.
- `Method`: GET, POST, PUT, DELETE, etc.
- `Uri`: request path and query string.
- `Response`: HTTP response sent back to client.
- `Router`: Axum route registry.
- `Client`: Reqwest HTTP client.
- `env`: environment variable access.
- `CorsLayer`: browser CORS support.

## 7. AppState

The shared state is:

```rust
#[derive(Clone)]
struct AppState {
    http_client: Client,
    auth_service_url: String,
    user_service_url: String,
}
```

This state is attached to the Axum router.

It stores:

```text
Reqwest client
Auth service URL
User service URL
```

Why keep this in state?

Because every request handler needs access to the same upstream URLs and the same HTTP client.

The struct derives `Clone` because Axum may clone state internally when routing requests.

## 8. Reading Environment Variables

In `main()`:

```rust
let port = env_or_default("API_GATEWAY_PORT", "8080");
let auth_service_url = env_or_default("AUTH_SERVICE_URL", "http://localhost:8081");
let user_service_url = env_or_default("USER_SERVICE_URL", "http://localhost:8082");
```

This means:

```text
If env var exists, use it.
Otherwise use local development default.
```

The helper:

```rust
fn env_or_default(key: &str, default_value: &str) -> String {
    env::var(key).unwrap_or_else(|_| default_value.to_string())
}
```

This is why the gateway can run in two places.

### Running locally from PowerShell

Defaults are used:

```text
AUTH_SERVICE_URL=http://localhost:8081
USER_SERVICE_URL=http://localhost:8082
API_GATEWAY_PORT=8080
```

### Running in Docker

Compose provides:

```text
AUTH_SERVICE_URL=http://auth-service:8081
USER_SERVICE_URL=http://user-service:8082
API_GATEWAY_PORT=8080
```

## 9. Docker Networking

This is very important.

From your Windows machine:

```text
auth-service is reached at http://localhost:8081
user-service is reached at http://localhost:8082
api-gateway is reached at http://localhost:8080
postgres is reached at localhost:55432
```

But from one Docker container to another:

```text
auth-service is reached at http://auth-service:8081
user-service is reached at http://user-service:8082
postgres is reached at postgres:5432
```

Inside Docker, service names become DNS names.

So this works inside the gateway container:

```text
http://auth-service:8081
http://user-service:8082
```

But this would be wrong inside Docker:

```text
http://localhost:8081
```

Because inside the gateway container, `localhost` means the gateway container itself, not the auth-service container.

## 10. Router Setup

The router is built here:

```rust
let app = Router::new()
    .route("/health", get(health))
    .route("/api/auth/{*path}", any(proxy_auth))
    .route("/api/users/{*path}", any(proxy_users))
    .layer(CorsLayer::permissive())
    .with_state(state.clone());
```

Route by route:

```rust
.route("/health", get(health))
```

This maps:

```text
GET /health -> health()
```

Then:

```rust
.route("/api/auth/{*path}", any(proxy_auth))
```

This maps all auth paths:

```text
GET    /api/auth/...
POST   /api/auth/...
PUT    /api/auth/...
DELETE /api/auth/...
```

to:

```rust
proxy_auth()
```

The `{*path}` part means wildcard route. It matches everything after `/api/auth/`.

Then:

```rust
.route("/api/users/{*path}", any(proxy_users))
```

This maps all user-service paths to:

```rust
proxy_users()
```

Then:

```rust
.layer(CorsLayer::permissive())
```

This allows browser requests from Angular.

Then:

```rust
.with_state(state.clone())
```

This attaches `AppState` to all handlers.

## 11. Starting The Server

The gateway binds to:

```rust
let bind_address = format!("0.0.0.0:{port}");
```

Then:

```rust
let listener = tokio::net::TcpListener::bind(&bind_address)
    .await
    .expect("failed to bind api gateway");
```

`0.0.0.0` means:

```text
Listen on all network interfaces.
```

That matters in Docker. If the app listened only on `127.0.0.1`, it might be reachable only inside the container. `0.0.0.0` lets Docker expose it to the host machine.

Then:

```rust
axum::serve(listener, app)
    .await
    .expect("api gateway server failed");
```

This starts serving requests.

## 12. Health Endpoint

Health function:

```rust
async fn health() -> Json<HashMap<&'static str, &'static str>> {
    Json(HashMap::from([
        ("service", "api-gateway"),
        ("status", "UP"),
    ]))
}
```

Request:

```text
GET http://localhost:8080/health
```

Response:

```json
{
  "service": "api-gateway",
  "status": "UP"
}
```

This endpoint does not call any backend service. It only proves the gateway process is alive.

## 13. Auth Proxy Handler

```rust
async fn proxy_auth(
    State(state): State<AppState>,
    method: Method,
    uri: Uri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    proxy_request(
        state.http_client,
        state.auth_service_url,
        method,
        uri,
        headers,
        body,
    )
    .await
}
```

Axum injects these automatically:

```text
State(state) -> shared gateway config
method       -> original HTTP method
uri          -> original path and query string
headers      -> original request headers
body         -> original request body
```

If the browser sends:

```text
POST /api/auth/login
```

then `proxy_auth()` passes that request to `proxy_request()` with:

```text
upstream_url = auth_service_url
```

In Docker:

```text
auth_service_url = http://auth-service:8081
```

## 14. User Proxy Handler

```rust
async fn proxy_users(
    State(state): State<AppState>,
    method: Method,
    uri: Uri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    proxy_request(
        state.http_client,
        state.user_service_url,
        method,
        uri,
        headers,
        body,
    )
    .await
}
```

This is almost the same as auth proxy.

Difference:

```text
auth proxy -> auth_service_url
user proxy -> user_service_url
```

## 15. Generic Proxy Logic

Both proxy handlers call:

```rust
proxy_request(...)
```

This function contains the common reverse-proxy behavior.

### Step 1: Preserve path and query

```rust
let path_and_query = uri
    .path_and_query()
    .map(|value| value.as_str())
    .unwrap_or(uri.path());
```

If the incoming request is:

```text
/api/users/profile?includeAudit=true
```

then `path_and_query` becomes:

```text
/api/users/profile?includeAudit=true
```

This preserves query parameters.

### Step 2: Build target URL

```rust
let target_url = format!("{}{}", upstream_url, path_and_query);
```

Example:

```text
upstream_url   = http://user-service:8082
path_and_query = /api/users/profile
target_url     = http://user-service:8082/api/users/profile
```

### Step 3: Create outgoing request

```rust
let mut request = http_client.request(method, target_url).body(body);
```

This forwards:

```text
Original method
Original body
Target URL
```

So:

```text
POST /api/users/profile
```

stays POST.

And JSON body stays the same.

### Step 4: Copy headers

```rust
for (name, value) in headers.iter() {
    if name.as_str().eq_ignore_ascii_case("host") {
        continue;
    }

    request = request.header(name, value);
}
```

This copies incoming headers to the upstream request.

Important for user-service:

```text
Authorization: Bearer <jwt>
```

Without forwarding the `Authorization` header, user-service would always return:

```json
{
  "message": "authorization header is required"
}
```

Why skip `Host`?

Because the original host is:

```text
localhost:8080
```

But the upstream host should be:

```text
auth-service:8081
user-service:8082
```

Forwarding the original `Host` header can confuse upstream services or proxies.

### Step 5: Send request

```rust
match request.send().await {
```

This performs the upstream HTTP call.

If it succeeds:

```rust
Ok(response) => {
    let status = response.status();
    let body = response.bytes().await.unwrap_or_default();

    (status, body).into_response()
}
```

The gateway takes:

```text
Upstream status code
Upstream response body
```

and sends them back to the frontend.

Example:

If auth-service returns:

```text
400 Bad Request
```

then gateway returns:

```text
400 Bad Request
```

If user-service returns:

```text
200 OK
```

then gateway returns:

```text
200 OK
```

If upstream fails:

```rust
Err(error) => {
    eprintln!("proxy error: {error}");

    (StatusCode::BAD_GATEWAY, "upstream service unavailable").into_response()
}
```

The gateway returns:

```text
502 Bad Gateway
```

That means:

```text
Gateway is alive, but it could not reach the backend service.
```

## 16. Request Flow Examples

### Local account register

Frontend sends:

```text
POST http://localhost:8080/api/auth/register
```

Body:

```json
{
  "email": "amit@example.com",
  "password": "Password123!",
  "displayName": "Amit Kumar"
}
```

Gateway forwards to:

```text
POST http://auth-service:8081/api/auth/register
```

Auth-service writes user data to:

```text
auth_db
```

Auth-service returns:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer"
}
```

Gateway returns the same response to Angular.

### Login

Frontend sends:

```text
POST http://localhost:8080/api/auth/login
```

Gateway forwards to:

```text
POST http://auth-service:8081/api/auth/login
```

Auth-service validates credentials and returns JWT tokens.

### Save profile

Frontend sends:

```text
POST http://localhost:8080/api/users/profile
Authorization: Bearer <accessToken>
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

Gateway forwards to:

```text
POST http://user-service:8082/api/users/profile
Authorization: Bearer <accessToken>
```

User-service validates the JWT and extracts `userId`.

User-service writes profile data to:

```text
user_db
```

Then it returns the saved profile.

## 17. Why Gateway Does Not Validate JWT Yet

Right now user-service validates JWT itself.

Current flow:

```text
Angular -> gateway -> user-service validates JWT
```

Future production-style flow could be:

```text
Angular -> gateway validates JWT -> user-service trusts forwarded user context
```

But do not rush that.

For learning and safety, it is fine that user-service still validates its own token. It keeps authorization close to the service that owns the protected resource.

Later, the gateway can add:

```text
JWT pre-validation
rate limiting
request IDs
structured logs
timeout control
central auth error formatting
```

## 18. CORS In This Gateway

Current code:

```rust
.layer(CorsLayer::permissive())
```

This is convenient for local development.

It allows browser calls from Angular during development.

For production, do not keep it fully permissive. Instead, restrict allowed origins.

Example future direction:

```text
Allowed origins:
https://your-real-frontend-domain.com
```

Do not allow every origin in production unless there is a deliberate reason.

## 19. Current Limitations

This gateway is intentionally simple.

Current limitations:

```text
No request timeout configuration
No retry policy
No rate limiting
No structured JSON logging
No request ID propagation
No JWT validation at gateway
No service discovery
No load balancing
No circuit breaker
No path rewriting
No response header forwarding
```

The biggest code limitation today:

```rust
(status, body).into_response()
```

This returns status and body, but does not preserve all response headers from the upstream service.

For many JSON APIs this is okay during learning, but production gateways should carefully forward important headers like:

```text
content-type
cache-control
set-cookie, if cookies are used
trace IDs
```

## 20. Why We Do Not Forward Host Header

The gateway intentionally skips:

```text
Host
```

Because incoming host is:

```text
localhost:8080
```

But backend service host is:

```text
auth-service:8081
```

If we forward `Host: localhost:8080`, backend services may think the request was meant for the gateway host.

Skipping host lets Reqwest set the correct host for the upstream URL.

## 21. Testing Commands

Start all services:

```powershell
docker compose up -d --build
```

Check containers:

```powershell
docker compose ps
```

Check gateway health:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Expected:

```text
status service
------ -------
UP     api-gateway
```

Probe auth through gateway:

```powershell
Invoke-WebRequest `
  -Uri http://localhost:8080/api/auth/login `
  -Method POST `
  -ContentType "application/json" `
  -Body "{}" `
  -UseBasicParsing
```

Expected:

```text
400 Bad Request
```

This is good because it proves the request reached auth-service. It fails only because `{}` is not a valid login body.

Probe user-service through gateway:

```powershell
Invoke-WebRequest `
  -Uri http://localhost:8080/api/users/profile `
  -Method GET `
  -UseBasicParsing
```

Expected:

```json
{
  "message": "authorization header is required"
}
```

This is good because it proves the request reached user-service. It fails only because no JWT was sent.

## 22. Common Errors

### 502 Bad Gateway

Means:

```text
Gateway is running, but upstream service is unreachable.
```

Check:

```powershell
docker compose ps
docker compose logs --tail=80 auth-service
docker compose logs --tail=80 user-service
docker compose logs --tail=80 api-gateway
```

### Connection refused on localhost:8080

Means:

```text
Gateway is not running or port is not mapped.
```

Fix:

```powershell
docker compose up -d api-gateway
```

### User-service says authorization header required

Means:

```text
Request reached user-service, but no JWT was sent.
```

Fix:

```text
Login first, then send Authorization: Bearer <accessToken>
```

### GitHub OAuth redirect mismatch

GitHub redirect URI must match in all three places:

```text
GitHub OAuth App callback URL
.env GITHUB_REDIRECT_URI
Angular GitHub redirect URI
```

Current Angular/backend target:

```text
http://127.0.0.1:4200
```

## 23. Production Improvement Plan

Recommended next steps:

1. Add request timeout.

   The gateway should not wait forever for upstream services.

2. Add structured logging.

   Log method, path, status, duration, and request ID.

3. Add request ID propagation.

   Every request should get an `X-Request-Id`.

4. Restrict CORS.

   Replace permissive CORS with explicit frontend origins.

5. Forward response headers carefully.

   Preserve content type and other important headers.

6. Add central error format.

   Make gateway errors consistent.

7. Add rate limiting.

   Protect login and public endpoints.

8. Consider JWT validation at gateway.

   Useful later, but user-service should still protect sensitive operations.

## 24. Mental Model

Think of the gateway as the front desk of your backend.

It does not own users.

It does not own profiles.

It does not own the database.

It only receives requests, decides which service should handle them, forwards the request, and returns the response.

Current routing table:

```text
/health       -> gateway itself
/api/auth/*   -> auth-service
/api/users/*  -> user-service
```

That is the core idea.

