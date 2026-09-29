import { knowledgeBaseApi } from './knowledgebase';
import { resumeApi } from './resume';
import { historyApi } from './history';
import type { BatchUploadAdapter } from '../types/batchUpload';
import { getUploadOutcome, normalizeProcessingStatus } from '../utils/batchUpload';

export const knowledgeBaseUploadAdapter: BatchUploadAdapter = {
  processLabel: '向量化',
  async upload(file, customName) {
    const result = await knowledgeBaseApi.uploadKnowledgeBase(file, customName);
    if (!result.knowledgeBase?.id) throw new Error('上传结果缺少知识库 ID，请重试');
    return {
      entityId: result.knowledgeBase.id,
      duplicate: result.duplicate,
      ...getUploadOutcome(normalizeProcessingStatus(result.knowledgeBase.vectorStatus),
        result.enqueueAccepted, result.message, '向量化'),
    };
  },
  async getStatus(id, signal) {
    const result = await knowledgeBaseApi.getKnowledgeBase(id, signal);
    return { id: result.id, status: result.vectorStatus, error: result.vectorError };
  },
  retry: id => knowledgeBaseApi.revectorize(id),
};

export const resumeUploadAdapter: BatchUploadAdapter = {
  processLabel: '分析',
  async upload(file, _customName, jobDirection) {
    const result = await resumeApi.uploadAndAnalyze(file, jobDirection);
    const id = result.resume?.id ?? result.storage?.resumeId;
    if (!id) throw new Error('上传结果缺少简历 ID，请重试');
    // 历史重复简历可能只返回 analysis + storage；有显式状态时以显式状态为准。
    const status = normalizeProcessingStatus(result.resume?.analyzeStatus,
      result.analysis ? 'COMPLETED' : 'PENDING');
    return {
      entityId: id,
      duplicate: result.duplicate,
      ...getUploadOutcome(status, result.enqueueAccepted, result.message, '分析'),
    };
  },
  async getStatus(id, signal) {
    const result = await historyApi.getResumeDetail(id, signal);
    return {
      id: result.id,
      status: normalizeProcessingStatus(result.analyzeStatus,
        result.analyses?.length ? 'COMPLETED' : 'PENDING'),
      error: result.analyzeError,
    };
  },
  retry: id => historyApi.reanalyze(id),
};
