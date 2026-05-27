FROM golang:1.24-alpine AS build

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o user-service .

FROM alpine:3.20

WORKDIR /app

COPY --from=build /app/user-service ./user-service

EXPOSE 8082

ENTRYPOINT ["./user-service"]
