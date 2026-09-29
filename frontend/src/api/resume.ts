import { request } from './request';
import type { UploadResponse } from '../types/resume';

export const resumeApi = {
  /**
   * 上传简历并获取分析结果
   */
  async uploadAndAnalyze(file: File, jobDirection?: string): Promise<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (jobDirection) {
      formData.append('jobDirection', jobDirection);
    }
    return request.upload<UploadResponse>('/api/resumes/upload', formData);
  },

  /**
   * 健康检查
   */
  async healthCheck(): Promise<{ status: string; service: string }> {
    return request.get('/api/resumes/health');
  },
};
