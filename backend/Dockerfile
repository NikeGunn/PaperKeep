# syntax=docker/dockerfile:1

# Stage 1: Build
FROM --platform=$BUILDPLATFORM golang:1.25-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH \
    go build -ldflags="-s -w" -o /app/server ./cmd/api

# Stage 2: Runtime
FROM public.ecr.aws/lambda/provided:al2023-arm64
COPY --from=builder /app/server /var/task/server
ENTRYPOINT ["/var/task/server"]
