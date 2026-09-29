import type { BatchUploadItem, BatchUploadStatus, FileSelectionResult, FileUploadPolicy, ProcessingStatus } from '../types/batchUpload';

export const MAX_BATCH_FILES = 10;
export const MAX_CONCURRENT_UPLOADS = 2;
export const UPLOAD_START_INTERVAL_MS = 500;
export const UPLOAD_RETRY_COOLDOWN_MS = 2000;
export const STATUS_POLL_INTERVAL_MS = 5000;

export const KNOWLEDGE_BASE_FILE_POLICY: FileUploadPolicy = {
  extensions: ['pdf', 'doc', 'docx', 'txt', 'md'],
  maxFileSize: 50 * 1024 * 1024,
  maxSizeLabel: '50MB',
  formatLabel: 'PDF、DOCX、DOC、TXT、MD',
};
export const RESUME_FILE_POLICY: FileUploadPolicy = {
  extensions: ['pdf', 'doc', 'docx', 'txt'],
  maxFileSize: 10 * 1024 * 1024,
  maxSizeLabel: '10MB',
  formatLabel: 'PDF、DOCX、DOC、TXT',
};

interface QueuedUploadTask {
  clientId: string;
  run: () => Promise<void>;
}

export function getFileIdentity(file: Pick<File, 'name' | 'size' | 'lastModified'>): string {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

export function validateUploadFile(file: Pick<File, 'name' | 'size'>, policy: FileUploadPolicy): string | null {
  if (file.size <= 0) {
    return '文件为空';
  }
  if (file.size > policy.maxFileSize) {
    return `文件超过 ${policy.maxSizeLabel}`;
  }

  const extension = file.name.includes('.')
    ? file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
    : '';
  if (!policy.extensions.includes(extension)) {
    return `仅支持 ${policy.formatLabel}`;
  }
  return null;
}

export function selectUploadFiles(
  currentItems: readonly BatchUploadItem[],
  files: FileList | File[],
  policy: FileUploadPolicy,
  now = Date.now(),
): FileSelectionResult {
  const identities = new Set(currentItems.map(item => getFileIdentity(item.file)));
  const accepted: BatchUploadItem[] = [];
  const rejected: string[] = [];
  let remainingSlots = Math.max(0, MAX_BATCH_FILES - currentItems.length);

  Array.from(files).forEach((file, index) => {
    const identity = getFileIdentity(file);
    const validationError = validateUploadFile(file, policy);

    if (identities.has(identity)) {
      rejected.push(`${file.name}（已在列表中）`);
    } else if (validationError) {
      rejected.push(`${file.name}（${validationError}）`);
    } else if (remainingSlots === 0) {
      rejected.push(`${file.name}（单批最多 ${MAX_BATCH_FILES} 个文件）`);
    } else {
      identities.add(identity);
      remainingSlots -= 1;
      accepted.push({
        clientId: `${now}-${index}-${identity}`,
        file,
        customName: '',
        category: '',
        status: 'READY',
      });
    }
  });

  return { accepted, rejected };
}

export function countReadyItemsMissingCategory(items: readonly BatchUploadItem[]): number {
  return items.filter(item => item.status === 'READY' && !item.category).length;
}

export function normalizeProcessingStatus(status?: string, fallback: ProcessingStatus = 'PENDING'): ProcessingStatus {
  return status === 'PENDING' || status === 'PROCESSING' || status === 'COMPLETED' || status === 'FAILED'
    ? status : fallback;
}

export function toBatchUploadStatus(status: ProcessingStatus): BatchUploadStatus {
  return status === 'FAILED' ? 'PROCESS_FAILED' : status;
}

export function getUploadOutcome(
  processStatus: ProcessingStatus,
  enqueueAccepted: boolean | undefined,
  message: string | undefined,
  processLabel: string,
): Pick<BatchUploadItem, 'status' | 'error' | 'needsStatusRefresh'> {
  const enqueueFailed = enqueueAccepted === false;
  const status = enqueueFailed ? 'FAILED' : processStatus;
  return {
    status: toBatchUploadStatus(status),
    error: status === 'FAILED'
      ? (enqueueFailed && message) || `${processLabel}失败，请重试`
      : undefined,
    needsStatusRefresh: status === 'FAILED' && !enqueueFailed,
  };
}

export function isProcessingActive(status: BatchUploadStatus): boolean {
  return status === 'PENDING' || status === 'PROCESSING';
}

export function canRetryUpload(
  item: Pick<BatchUploadItem, 'status' | 'retryAvailableAt'>,
  now = Date.now(),
): boolean {
  return item.status === 'UPLOAD_FAILED'
    && (!item.retryAvailableAt || item.retryAvailableAt <= now);
}

export class RateLimitedUploadQueue {
  private readonly concurrency: number;
  private readonly startIntervalMs: number;
  private readonly pending: QueuedUploadTask[] = [];
  private readonly scheduledIds = new Set<string>();
  private readonly idleResolvers: Array<() => void> = [];
  private activeCount = 0;
  private nextStartAt = 0;
  private startTimer: ReturnType<typeof setTimeout> | null = null;
  private disposed = false;

  constructor(concurrency: number, startIntervalMs: number) {
    if (concurrency < 1) {
      throw new Error('并发数必须大于 0');
    }
    if (startIntervalMs < 0) {
      throw new Error('请求间隔不能小于 0');
    }
    this.concurrency = concurrency;
    this.startIntervalMs = startIntervalMs;
  }

  enqueue(clientId: string, run: () => Promise<void>): boolean {
    if (this.disposed || this.scheduledIds.has(clientId)) {
      return false;
    }

    this.scheduledIds.add(clientId);
    this.pending.push({ clientId, run });
    this.pump();
    return true;
  }

  isScheduled(clientId: string): boolean {
    return this.scheduledIds.has(clientId);
  }

  whenIdle(): Promise<void> {
    if (this.isIdle()) {
      return Promise.resolve();
    }
    return new Promise(resolve => this.idleResolvers.push(resolve));
  }

  dispose(): void {
    this.disposed = true;
    if (this.startTimer) {
      clearTimeout(this.startTimer);
      this.startTimer = null;
    }
    this.pending.forEach(task => this.scheduledIds.delete(task.clientId));
    this.pending.length = 0;
    this.resolveIdleIfNeeded();
  }

  private pump(): void {
    if (this.disposed || this.startTimer || this.activeCount >= this.concurrency) {
      return;
    }

    const task = this.pending[0];
    if (!task) {
      this.resolveIdleIfNeeded();
      return;
    }

    const waitTime = Math.max(0, this.nextStartAt - Date.now());
    if (waitTime > 0) {
      this.startTimer = setTimeout(() => {
        this.startTimer = null;
        this.pump();
      }, waitTime);
      return;
    }

    this.pending.shift();
    this.activeCount += 1;
    this.nextStartAt = Date.now() + this.startIntervalMs;

    void Promise.resolve()
      .then(task.run)
      .catch(() => undefined)
      .finally(() => {
        this.activeCount -= 1;
        this.scheduledIds.delete(task.clientId);
        this.pump();
        this.resolveIdleIfNeeded();
      });

    this.pump();
  }

  private isIdle(): boolean {
    return this.activeCount === 0 && this.pending.length === 0 && this.startTimer === null;
  }

  private resolveIdleIfNeeded(): void {
    if (!this.isIdle()) return;
    this.idleResolvers.splice(0).forEach(resolve => resolve());
  }
}
