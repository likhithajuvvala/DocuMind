# Free cloud deployment (branch: `deploy/free-cloud`)

Same code as `main` — no application logic changed. This wires the existing config to free-tier
managed services instead of the local docker-compose infra:

| Local (docker-compose)     | Free cloud replacement          |
|-----------------------------|----------------------------------|
| postgres (pgvector image)   | [Neon](https://neon.tech) — Postgres with pgvector |
| kafka                       | [Upstash Kafka](https://upstash.com) |
| redis                       | [Upstash Redis](https://upstash.com) |
| minio                       | [Cloudflare R2](https://dash.cloudflare.com) |
| gateway/document/ingestion/query | [Render](https://render.com) — `render.yaml` blueprint at repo root |
| frontend                    | [Vercel](https://vercel.com) |

Qdrant is dropped rather than replaced — `VECTOR_STORE=pgvector` was already a supported config
toggle, so this is zero code change, just one fewer account to manage.

## 1. Neon (Postgres)

1. Create a project, note the connection string from the dashboard. It looks like:
   `postgresql://<user>:<password>@<host>/<db>?sslmode=require`
2. Nothing else to do — `spring.ai.vectorstore.pgvector.initialize-schema: true` means the app
   creates the `vector` extension and its table itself on first boot.

You'll split that connection string into three Render env vars: `DATABASE_URL`
(`jdbc:postgresql://<host>/<db>?sslmode=require`), `DATABASE_USER`, `DATABASE_PASSWORD`.

## 2. Cloudflare R2 (object storage)

1. R2 → create a bucket (e.g. `documind-documents`).
2. R2 → Manage API Tokens → create a token with read/write access to that bucket.
3. You'll need: Account ID, Access Key ID, Secret Access Key, and the endpoint
   `https://<account-id>.r2.cloudflarestorage.com`.

## 3. Upstash (Kafka + Redis)

1. Kafka → create a cluster → **Topics**: create `documind.document.uploaded`,
   `documind.document.indexed`, `documind.document.failed`, `documind.document.deleted`
   (auto-create is off by default on Upstash's free tier, unlike the local broker).
2. Kafka → **Clients** tab shows a ready-to-paste `sasl.jaas.config` string — copy it verbatim.
3. Redis → create a database → **Details** tab has host, port (usually 6379), and password.

## 4. Render (backend)

1. New → Blueprint → connect this GitHub repo, branch `deploy/free-cloud`. Render reads
   `render.yaml` and proposes 4 services (`documind-gateway`, `documind-document`,
   `documind-ingestion`, `documind-query`).
2. Every `sync: false` env var in the `documind-shared` group needs a value pasted in before the
   first deploy — Render's UI lists them all in one place when you apply the blueprint.
3. `JWT_SECRET` is generated for you (`generateValue: true`) and shared automatically across all
   four services — nothing to do there.
4. First deploys will be slow (free-tier CPU) and free instances spin down after 15 minutes idle,
   so the first request after a quiet period cold-starts (10-30s). Fine for 0-10 users.

## 5. Vercel (frontend)

1. New Project → import this repo → set **Root Directory** to `frontend`.
2. Environment variable: `NEXT_PUBLIC_API_BASE_URL` = `https://documind-gateway.onrender.com`.
3. Deploy. Vercel gives you the live link (`https://<project>.vercel.app`).
4. Go back to Render's `documind-shared` group and set `CORS_ALLOWED_ORIGINS` to that Vercel URL,
   then redeploy the 3 web services (gateway/document/query) so the browser is allowed through.

## Notes

- `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` are optional — leave blank and chat answers behave the
  same as local dev without a key ("The answer was not found in your documents" instead of a
  crash).
- `documind-ingestion` is a Render **worker**, not a web service — it has no public URL and won't
  spin down on idle the same way, since Kafka delivery (not HTTP traffic) is what wakes it.
- To stop all billing, delete the Render services and the Vercel project; Neon/Upstash/R2 free
  tiers don't bill at all at this scale, so nothing else to tear down.
