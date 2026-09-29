import { AlertCircle, ArrowLeft, Upload } from 'lucide-react';

import BatchUploadDropzone from './BatchUploadDropzone';
import BatchUploadList from './BatchUploadList';
import { useBatchUpload } from '../../hooks/useBatchUpload';
import {
  countReadyItemsMissingCategory,
  MAX_BATCH_FILES,
  MAX_CONCURRENT_UPLOADS,
} from '../../utils/batchUpload';
import type { BatchUploadAdapter, BatchUploadCategoryOption, FileUploadPolicy } from '../../types/batchUpload';

interface BatchUploadPageProps {
  entityLabel: string;
  policy: FileUploadPolicy;
  adapter: BatchUploadAdapter;
  customNamePlaceholder?: string;
  categoryOptions?: readonly BatchUploadCategoryOption[];
  categoryLabel?: string;
  categoryRequired?: boolean;
  backLabel: string;
  onBack: () => void;
}

export default function BatchUploadPage({
  entityLabel, policy, adapter, customNamePlaceholder,
  categoryOptions, categoryLabel = '分类', categoryRequired = false,
  backLabel, onBack,
}: BatchUploadPageProps) {
  const batchUpload = useBatchUpload(adapter, policy);
  const processLabel = adapter.processLabel;
  const missingCategoryCount = categoryRequired && categoryOptions?.length
    ? countReadyItemsMissingCategory(batchUpload.items)
    : 0;
  const enqueueBlocked = missingCategoryCount > 0;

  return (
    <div className="mx-auto max-w-5xl pb-16 pt-10">
      <div className="mb-8 text-center">
        <h1 className="mb-3 text-4xl font-bold tracking-tight text-slate-900 dark:text-white">
          批量上传{entityLabel}
        </h1>
        <p className="text-slate-500 dark:text-slate-400">
          单批最多 {MAX_BATCH_FILES} 个文件，同时上传 {MAX_CONCURRENT_UPLOADS} 个，上传后自动异步{processLabel}
        </p>
      </div>

      <BatchUploadDropzone
        entityLabel={entityLabel}
        policy={policy}
        full={batchUpload.items.length >= MAX_BATCH_FILES}
        notice={batchUpload.selectionNotice}
        onFilesSelected={batchUpload.addFiles}
      />

      {batchUpload.pollError && (
        <div className="mt-4 flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
          <AlertCircle className="h-4 w-4" />
          部分{processLabel}状态暂时无法刷新，将自动重试
        </div>
      )}

      <BatchUploadList
        processLabel={processLabel}
        customNamePlaceholder={customNamePlaceholder}
        categoryOptions={categoryOptions}
        categoryLabel={categoryLabel}
        items={batchUpload.items}
        completedCount={batchUpload.completedCount}
        failedCount={batchUpload.failedCount}
        hasUploadActivity={batchUpload.hasUploadActivity}
        retryingProcessId={batchUpload.retryingProcessId}
        onClear={batchUpload.clearItems}
        onNameChange={batchUpload.updateCustomName}
        onCategoryChange={batchUpload.updateCategory}
        onRemove={batchUpload.removeItem}
        onRetryUpload={batchUpload.retryUpload}
        onRetryProcessing={clientId => void batchUpload.retryProcessing(clientId)}
      />

      {enqueueBlocked && (
        <p className="mt-6 text-center text-sm text-amber-600 dark:text-amber-400" aria-live="polite">
          还有 {missingCategoryCount} 个文件未选择{categoryLabel}，选择后才能加入上传队列
        </p>
      )}

      <div className="mt-8 flex flex-wrap justify-center gap-4">
        <button
          type="button"
          onClick={onBack}
          disabled={batchUpload.hasUploadActivity}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-6 py-3 font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
        >
          <ArrowLeft className="h-4 w-4" />
          {backLabel}
        </button>
        {batchUpload.readyCount > 0 && (
          <button
            type="button"
            onClick={batchUpload.enqueueReadyItems}
            disabled={enqueueBlocked}
            className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-emerald-600 px-8 py-3 font-semibold text-white shadow-lg shadow-emerald-500/30 transition hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Upload className="h-5 w-5" />
            加入上传队列 {batchUpload.readyCount} 个文件
          </button>
        )}
      </div>
    </div>
  );
}
