# Structured logging

Every backend service can emit newline-delimited JSON instead of human-readable text, so logs can be shipped straight into ELK, Loki, or a hosted equivalent without a parsing layer.

## Turning it on

```
LOG_FORMAT=ecs        # elastic common schema
LOG_FORMAT=logstash   # logstash json
LOG_FORMAT=gelf       # graylog
LOG_FORMAT=           # empty, the default: plain readable text
```

Docker Compose sets `ecs` by default, so containers are aggregation-ready; leaving the variable unset locally keeps the familiar console output. This uses Spring Boot's built-in structured logging, so no extra dependency or logback XML is involved.

## What each line carries

A request produces one event per service it touches:

```json
{
  "service": "gateway-service",
  "request_id": "d2a4868e-9fd7-4042-b398-9127b5092594",
  "workspace_id": "b4d33487-dd7e-4d2b-a89d-d09adb8110e3",
  "user_id": "7b7ef19c-6fc2-4c5f-bf2a-28cc361e20ad",
  "http_method": "GET",
  "http_path": "/api/documents",
  "http_status": "200",
  "duration_ms": "210"
}
```

`RequestCorrelationFilter` accepts an inbound `X-Request-Id` or mints one, and a request wrapper passes the generated id to the proxied call, so the **same id appears in the gateway and in the service behind it**. Filtering a log tool on one `request_id` reconstructs the whole hop chain.

Only the service that mints the id sets the response header. Setting it in every service returns `X-Request-Id` twice on proxied responses, which is the same duplication that made CORS fail in the browser.

Ingestion has no HTTP request, so `IngestionPipeline` puts `document_id` and `workspace_id` in the MDC for the duration of the work:

```json
{
  "service": "ingestion-worker",
  "document_id": "a57046b1-f81f-437f-beee-aa421de349a1",
  "workspace_id": "b4d33487-dd7e-4d2b-a89d-d09adb8110e3",
  "message": "Indexed document a57046b1-… into 3 chunks"
}
```

## Where the context comes from

`JwtAuthenticationFilter` publishes the workspace and user into both the MDC and request attributes. The attributes matter: the gateway finishes proxied requests on an async dispatch, where the MDC set on the original thread is gone, so the access log would otherwise report a null workspace on exactly the tenant-facing hop.

The outermost filter owns cleanup and calls `MDC.clear()` after logging. Earlier filters must not clear their own keys, or the fields disappear before the access log is written.

Actuator paths are excluded, otherwise Prometheus scraping every 15 seconds would dominate the log volume.
