import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { BatchUploadAdapter, BatchUploadItem, FileUploadPolicy, ProcessingResult } from '../types/batchUpload';
import { getErrorMessage } from '../api/request';
import { useBatchStatusPolling } from './useBatchStatusPolling';
import {
  canRetryUpload,
  isProcessingActive,
  MAX_CONCURRENT_UPLOADS,
  RateLimitedUploadQueue,
  selectUploadFiles,
  toBatchUploadStatus,
  UPLOAD_RETRY_COOLDOWN_MS,
  UPLOAD_START_INTERVAL_MS,
} from '../utils/batchUpload';

export function useBatchUpload(adapter: BatchUploadAdapter, policy: FileUploadPolicy) {
  const mountedRef = useRef(true);
  const itemsRef = useRef<BatchUploadItem[]>([]);
  const queueRef = useRef<RateLimitedUploadQueue | null>(null);
  const retryTimersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const retryingProcessRef = useRef<number | null>(null);
  const [items, setItems] = useState<BatchUploadItem[]>([]);
  const [selectionNotice, setSelectionNotice] = useState('');
  const [retryingProcessId, setRetryingProcessId] = useState<number | null>(null);

  const getQueue = useCallback(() => {
    if (!queueRef.current) {
      queueRef.current = new RateLimitedUploadQueue(
        MAX_CONCURRENT_UPLOADS,
        UPLOAD_START_INTERVAL_MS,
      );
    }
    return queueRef.current;
  }, []);

  const updateItems = useCallback(
    (updater: (current: BatchUploadItem[]) => BatchUploadItem[]) => {
      const next = updater(itemsRef.current);
      itemsRef.current = next;
      if (mountedRef.current) setItems(next);
      return next;
    },
    [],
  );

  const updateItem = useCallback(
    (clientId: string, updater: (item: BatchUploadItem) => BatchUploadItem) => {
      updateItems(current => current.map(item => (
        item.clientId === clientId ? updater(item) : item
      )));
    },
    [updateItems],
  );

  const scheduleRetryUnlock = useCallback((clientId: string) => {
    const currentTimer = retryTimersRef.current.get(clientId);
    if (currentTimer) clearTimeout(currentTimer);

    const timer = setTimeout(() => {
      retryTimersRef.current.delete(clientId);
      updateItem(clientId, item => ({ ...item, retryAvailableAt: undefined }));
    }, UPLOAD_RETRY_COOLDOWN_MS);
    retryTimersRef.current.set(clientId, timer);
  }, [updateItem]);

  const uploadItem = useCallback(async (
    item: Pick<BatchUploadItem, 'clientId' | 'file' | 'customName'>,
  ) => {
    updateItem(item.clientId, current => ({
      ...current,
      status: 'UPLOADING',
      error: undefined,
      retryAvailableAt: undefined,
    }));

    try {
      const result = await adapter.upload(
        item.file,
        item.customName.trim() || undefined,
      );
      if (!mountedRef.current) return;
      updateItem(item.clientId, current => ({
        ...current,
        ...result,
      }));
    } catch (error: unknown) {
      if (!mountedRef.current) return;
      updateItem(item.clientId, current => ({
        ...current,
        status: 'UPLOAD_FAILED',
        error: getErrorMessage(error) || '上传失败，请重试',
        retryAvailableAt: Date.now() + UPLOAD_RETRY_COOLDOWN_MS,
      }));
      scheduleRetryUnlock(item.clientId);
    }
  }, [adapter, scheduleRetryUnlock, updateItem]);

  const enqueueItem = useCallback((clientId: string): boolean => {
    const item = itemsRef.current.find(current => current.clientId === clientId);
    if (!item || (item.status !== 'READY' && !canRetryUpload(item))) return false;

    const accepted = getQueue().enqueue(clientId, () => uploadItem({
      clientId,
      file: item.file,
      customName: item.customName,
    }));
    if (accepted) {
      updateItem(clientId, current => ({
        ...current,
        status: 'QUEUED',
        error: undefined,
        retryAvailableAt: undefined,
      }));
    }
    return accepted;
  }, [getQueue, updateItem, uploadItem]);

  const addFiles = useCallback((fileList: FileList | File[]) => {
    const selection = selectUploadFiles(itemsRef.current, fileList, policy);
    if (selection.accepted.length > 0) {
      updateItems(current => [...current, ...selection.accepted]);
    }
    setSelectionNotice(selection.rejected.join('；'));
  }, [policy, updateItems]);

  const enqueueReadyItems = useCallback(() => {
    setSelectionNotice('');
    itemsRef.current
      .filter(item => item.status === 'READY')
      .forEach(item => enqueueItem(item.clientId));
  }, [enqueueItem]);

  const retryUpload = useCallback((clientId: string) => {
    enqueueItem(clientId);
  }, [enqueueItem]);

  const trackedIdsKey = useMemo(() => [...new Set(items
    .filter(item => item.entityId && (isProcessingActive(item.status) || item.needsStatusRefresh))
    .map(item => item.entityId!))]
    .sort((a, b) => a - b)
    .join(','), [items]);

  const applyProcessingStatus = useCallback((result: ProcessingResult) => {
    updateItems(current => current.map(item => {
      if (item.entityId !== result.id
        || (!isProcessingActive(item.status) && !item.needsStatusRefresh)) return item;
      return {
        ...item,
        status: toBatchUploadStatus(result.status),
        needsStatusRefresh: false,
        error: result.status === 'FAILED'
          ? result.error || `${adapter.processLabel}失败，请重试`
          : undefined,
      };
    }));
  }, [adapter.processLabel, updateItems]);
  const { error: pollError, pause: pausePolling, resume: resumePolling } =
    useBatchStatusPolling(trackedIdsKey, adapter.getStatus, applyProcessingStatus);

  const retryProcessing = useCallback(async (clientId: string) => {
    const item = itemsRef.current.find(current => current.clientId === clientId);
    if (!item?.entityId || retryingProcessRef.current !== null) return;

    const entityId = item.entityId;
    pausePolling(entityId);
    retryingProcessRef.current = entityId;
    setRetryingProcessId(entityId);
    try {
      await adapter.retry(entityId);
      if (!mountedRef.current) return;
      updateItems(current => current.map(currentItem => (
        currentItem.entityId === entityId
          ? { ...currentItem, status: 'PENDING', error: undefined, needsStatusRefresh: false }
          : currentItem
      )));
    } catch (error: unknown) {
      if (!mountedRef.current) return;
      updateItem(clientId, current => ({
        ...current,
        error: getErrorMessage(error) || `重新${adapter.processLabel}失败，请重试`,
      }));
    } finally {
      retryingProcessRef.current = null;
      if (mountedRef.current) {
        resumePolling(entityId);
        setRetryingProcessId(null);
      }
    }
  }, [adapter, pausePolling, resumePolling, updateItem, updateItems]);

  useEffect(() => {
    mountedRef.current = true;
    getQueue();
    return () => {
      mountedRef.current = false;
      queueRef.current?.dispose();
      queueRef.current = null;
      retryTimersRef.current.forEach(timer => clearTimeout(timer));
      retryTimersRef.current.clear();
    };
  }, [getQueue]);

  const hasUploadActivity = items.some(item => ['QUEUED', 'UPLOADING'].includes(item.status));
  const clearItems = () => {
    if (hasUploadActivity) return;
    retryTimersRef.current.forEach(timer => clearTimeout(timer));
    retryTimersRef.current.clear();
    updateItems(() => []);
    setSelectionNotice('');
  };

  return {
    items,
    hasUploadActivity,
    selectionNotice,
    pollError,
    retryingProcessId,
    readyCount: items.filter(item => item.status === 'READY').length,
    completedCount: items.filter(item => item.status === 'COMPLETED').length,
    failedCount: items.filter(item => ['UPLOAD_FAILED', 'PROCESS_FAILED'].includes(item.status)).length,
    addFiles,
    clearItems,
    enqueueReadyItems,
    retryUpload,
    retryProcessing,
    removeItem: (clientId: string) => {
      const item = itemsRef.current.find(current => current.clientId === clientId);
      if (!item || !['READY', 'UPLOAD_FAILED'].includes(item.status)) return;
      const timer = retryTimersRef.current.get(clientId);
      if (timer) clearTimeout(timer);
      retryTimersRef.current.delete(clientId);
      updateItems(current => current.filter(currentItem => currentItem.clientId !== clientId));
    },
    updateCustomName: (clientId: string, customName: string) => updateItem(
      clientId,
      item => ['READY', 'UPLOAD_FAILED'].includes(item.status)
        ? { ...item, customName }
        : item,
    ),
    updateCategory: (clientId: string, category: string) => updateItem(
      clientId,
      item => ['READY', 'UPLOAD_FAILED'].includes(item.status)
        ? { ...item, category }
        : item,
    ),
  };
}
