export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type BatchUploadStatus = Exclude<ProcessingStatus, 'FAILED'>
  | 'READY' | 'QUEUED' | 'UPLOADING' | 'UPLOAD_FAILED' | 'PROCESS_FAILED';

export interface BatchUploadCategoryOption {
  value: string;
  label: string;
}

export interface BatchUploadItem {
  clientId: string;
  file: File;
  customName: string;
  /** 业务分类（如求职方向），空串表示尚未选择 */
  category: string;
  status: BatchUploadStatus;
  entityId?: number;
  duplicate?: boolean;
  error?: string;
  retryAvailableAt?: number;
  /** 上传响应未包含失败详情时，补查一次。 */
  needsStatusRefresh?: boolean;
}

export interface FileUploadPolicy {
  extensions: readonly string[];
  maxFileSize: number;
  maxSizeLabel: string;
  formatLabel: string;
}

export interface FileSelectionResult {
  accepted: BatchUploadItem[];
  rejected: string[];
}

export interface ProcessingResult {
  id: number;
  status: ProcessingStatus;
  error?: string | null;
}

export type BatchUploadResult = Pick<BatchUploadItem,
  'status' | 'duplicate' | 'error' | 'needsStatusRefresh'> & { entityId: number };

/** 业务适配器保持稳定引用，上传编排无需了解简历或知识库响应结构。 */
export interface BatchUploadAdapter {
  processLabel: string;
  upload: (file: File, customName?: string, category?: string) => Promise<BatchUploadResult>;
  getStatus: (id: number, signal: AbortSignal) => Promise<ProcessingResult>;
  retry: (id: number) => Promise<void>;
}
