import type { BatchUploadCategoryOption } from '../types/batchUpload';
import type { JobDirection } from '../types/resume';

export const JOB_DIRECTION_OPTIONS: ReadonlyArray<BatchUploadCategoryOption & { value: JobDirection }> = [
  { value: 'PROCUREMENT_SUPPLY_CHAIN', label: '采购 / 供应链' },
  { value: 'SALES_BD', label: '销售 / BD' },
  { value: 'TECH', label: '技术类' },
];
