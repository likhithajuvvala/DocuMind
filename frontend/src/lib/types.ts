export type DocumentStatus = "PENDING" | "PROCESSING" | "INDEXED" | "FAILED";

export type IngestionStatus =
  | "QUEUED"
  | "EXTRACTING"
  | "CHUNKING"
  | "EMBEDDING"
  | "COMPLETED"
  | "FAILED";

export interface AuthenticationResult {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  userId: string;
  workspaceId: string;
  email: string;
  role: string;
}

export interface DocumentSummary {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  uploadedBy: string;
  createdAt: string;
}

export interface DocumentStatusDetail {
  documentId: string;
  status: DocumentStatus;
  ingestionStatus: IngestionStatus | null;
  chunkCount: number;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface Citation {
  reference: number;
  documentId: string;
  documentName: string;
  pageNumber: number | null;
  relevance: number;
}

export interface ChatSession {
  id: string;
  documentId: string | null;
  title: string;
  createdAt: string;
}

export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  citations: Citation[];
  createdAt: string;
}

export interface UsagePerUser {
  userId: string;
  totalTokens: number;
  totalCost: number;
}

export interface WorkspaceUsage {
  workspaceId: string;
  since: string;
  totalTokens: number;
  totalCost: number;
  perUser: UsagePerUser[];
}

export interface IngestionHealth {
  pendingDocuments: number;
  processingDocuments: number;
  indexedDocuments: number;
  failedDocuments: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
