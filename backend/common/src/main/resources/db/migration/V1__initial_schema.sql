create extension if not exists "uuid-ossp";
create extension if not exists vector;

create table workspaces (
    id uuid primary key,
    name text not null,
    plan text not null,
    created_at timestamptz not null
);

create table users (
    id uuid primary key,
    email text not null unique,
    password_hash text not null,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    role text not null,
    created_at timestamptz not null
);

create index idx_users_workspace on users (workspace_id);

create table documents (
    id uuid primary key,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    filename text not null,
    content_type text not null,
    size_bytes bigint not null,
    storage_path text not null,
    status text not null,
    uploaded_by uuid not null references users (id),
    created_at timestamptz not null
);

create index idx_documents_workspace_status on documents (workspace_id, status);

create table document_chunks (
    id uuid primary key,
    document_id uuid not null references documents (id) on delete cascade,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    chunk_text text not null,
    chunk_index integer not null,
    page_number integer,
    embedding_id text not null,
    created_at timestamptz not null
);

create index idx_document_chunks_document on document_chunks (document_id);
create index idx_document_chunks_embedding on document_chunks (workspace_id, embedding_id);

create table chat_sessions (
    id uuid primary key,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    document_id uuid references documents (id) on delete set null,
    title text not null,
    created_at timestamptz not null
);

create index idx_chat_sessions_workspace on chat_sessions (workspace_id, created_at desc);

create table chat_messages (
    id uuid primary key,
    session_id uuid not null references chat_sessions (id) on delete cascade,
    role text not null,
    content text not null,
    citations jsonb,
    created_at timestamptz not null
);

create index idx_chat_messages_session on chat_messages (session_id, created_at);

create table ingestion_jobs (
    id uuid primary key,
    document_id uuid not null references documents (id) on delete cascade,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    status text not null,
    error_message text,
    chunk_count integer not null default 0,
    started_at timestamptz not null,
    finished_at timestamptz
);

create index idx_ingestion_jobs_document on ingestion_jobs (document_id, started_at desc);

create table usage_logs (
    id uuid primary key,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    model_name text not null,
    prompt_tokens integer not null,
    completion_tokens integer not null,
    cost_estimate numeric(12, 6) not null,
    created_at timestamptz not null
);

create index idx_usage_logs_workspace on usage_logs (workspace_id, created_at desc);
