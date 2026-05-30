FROM rust:1.87-slim AS build

WORKDIR /app

COPY Cargo.toml Cargo.lock ./
COPY src ./src

RUN cargo build --release

FROM debian:bookworm-slim

WORKDIR /app

COPY --from=build /app/target/release/api-gateway ./api-gateway

EXPOSE 8080

ENTRYPOINT ["./api-gateway"]