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

#[derive(Clone)]
struct AppState {
    http_client: Client,
    auth_service_url: String,
    user_service_url: String,
}

#[tokio::main]
async fn main() {
    let port = env_or_default("API_GATEWAY_PORT", "8080");
    let auth_service_url = env_or_default("AUTH_SERVICE_URL", "http://localhost:8081");
    let user_service_url = env_or_default("USER_SERVICE_URL", "http://localhost:8082");

    let state = AppState {
        http_client: Client::new(),
        auth_service_url,
        user_service_url,
    };

    let app = Router::new()
        .route("/health", get(health))
        .route("/api/auth/{*path}", any(proxy_auth))
        .route("/api/users/{*path}", any(proxy_users))
        .with_state(state.clone());

    let bind_address = format!("0.0.0.0:{port}");

    let listener = tokio::net::TcpListener::bind(&bind_address)
        .await
        .expect("failed to bind api gateway");

    println!("api-gateway running on http://localhost:{port}");
    println!("routing /api/auth/* to {}", state.auth_service_url);
    println!("routing /api/users/* to {}", state.user_service_url);

    axum::serve(listener, app)
        .await
        .expect("api gateway server failed");
}

fn env_or_default(key: &str, default_value: &str) -> String {
    env::var(key).unwrap_or_else(|_| default_value.to_string())
}

async fn health() -> Json<HashMap<&'static str, &'static str>> {
    Json(HashMap::from([
        ("service", "api-gateway"),
        ("status", "UP"),
    ]))
}

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

async fn proxy_request(
    http_client: Client,
    upstream_url: String,
    method: Method,
    uri: Uri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    let path_and_query = uri
        .path_and_query()
        .map(|value| value.as_str())
        .unwrap_or(uri.path());

    let target_url = format!("{}{}", upstream_url, path_and_query);

    let mut request = http_client.request(method, target_url).body(body);

    for (name, value) in headers.iter() {
        if name.as_str().eq_ignore_ascii_case("host") {
            continue;
        }

        request = request.header(name, value);
    }

    match request.send().await {
        Ok(response) => {
            let status = response.status();
            let body = response.bytes().await.unwrap_or_default();

            (status, body).into_response()
        }
        Err(error) => {
            eprintln!("proxy error: {error}");

            (StatusCode::BAD_GATEWAY, "upstream service unavailable").into_response()
        }
    }
}