# Helm chart

Deploys the four backend services and the frontend. It does not deploy Postgres, Kafka, MinIO, or Qdrant — those are expected to be operated separately (managed services, or their own charts), and are pointed at through `config`.

## Install

```bash
helm install documind ./documind \
  --namespace documind --create-namespace \
  --set image.tag=1.4.0 \
  --set secrets.jwtSecret="$(openssl rand -base64 48)" \
  --set secrets.databasePassword="…" \
  --set secrets.openaiApiKey="…"
```

Self-hosted models instead of a paid API:

```bash
helm install documind ./documind -f documind/values-ollama.yaml \
  --set secrets.jwtSecret="…" --set secrets.databasePassword="…"
```

In a real pipeline, keep credentials out of the release entirely and point at a secret managed elsewhere:

```bash
--set secrets.existingSecret=documind-credentials
```

That secret must supply `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, and any provider API keys.

## What the values control

`services` is a map, so each service carries its own replicas, resources, and autoscaling. Ingestion is CPU and IO bound while querying is latency sensitive and spends its time waiting on the model provider, so they scale on separate curves rather than sharing one number.

The chart refuses to render without `secrets.jwtSecret` unless `existingSecret` is set. That is deliberate: the application falls back to a placeholder signing key, and an install that quietly succeeded with it would issue forgeable tokens.

The frontend deployment receives only `NEXT_PUBLIC_API_BASE_URL`. It never gets the database or provider credentials, because anything shipped to a browser build is public.

## Validating changes

```bash
helm lint documind --set secrets.jwtSecret=x --set secrets.databasePassword=x
helm template documind ./documind --set secrets.jwtSecret=x --set secrets.databasePassword=x \
  | kubeconform -strict -summary -kubernetes-version 1.31.0
```

The current chart renders 17 objects and passes both.

## Namespace

The chart does not template a Namespace. Helm owns that through `--namespace` and `--create-namespace`, and templating it makes uninstall behaviour surprising, since removing a release would take the namespace and anything else in it.

## History

This chart replaced a set of plain manifests that described the same workloads. They were deleted rather than kept alongside, because two descriptions of one deployment drift apart silently. The chart covers everything they did and adds probes, a service account, security contexts, a config checksum that rolls pods when configuration changes, and the credential guard. If you need the originals, they are in git history before this commit.
