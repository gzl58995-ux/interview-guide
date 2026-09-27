import { request } from './request';
import type { AnalyzeStatus, InterviewDetail, ResumeDetail } from './history';
import type { PageResult } from '../types/page';

export interface AdminResumeItem {
  id: number;
  userId: number;
  username: string | null;
  filename: string;
  fileSize: number;
  uploadedAt: string;
  accessCount: number;
  latestScore?: number;
  lastAnalyzedAt?: string;
  interviewCount: number;
  analyzeStatus?: AnalyzeStatus;
  analyzeError?: string;
}

export type AdminResumeSort =
  | 'UPLOADED_AT_DESC'
  | 'UPLOADED_AT_ASC'
  | 'ACCESS_COUNT_DESC'
  | 'FILE_SIZE_DESC';

export interface AdminResumeQuery {
  keyword?: string;
  analyzeStatus?: AnalyzeStatus;
  userId?: number;
  sort?: AdminResumeSort;
  page?: number;
  size?: number;
}

export interface AdminResumeStats {
  totalResumes: number;
  userCount: number;
  analyzingCount: number;
  interviewCount: number;
}

export interface AdminResumeOwner {
  userId: number;
  username: string | null;
  resumeCount: number;
}

export const adminApi = {
  /**
   * 获取全平台所有用户的简历列表（仅管理员）
   */
  async getAllResumes(): Promise<AdminResumeItem[]> {
    return request.get<AdminResumeItem[]>('/api/admin/resumes');
  },

  /**
   * 按查询表单分页搜索全平台简历（仅管理员）
   */
  async queryResumes(
    query: AdminResumeQuery,
    signal?: AbortSignal
  ): Promise<PageResult<AdminResumeItem>> {
    return request.get<PageResult<AdminResumeItem>>('/api/admin/resumes/page', {
      params: query,
      signal,
    });
  },

  /**
   * 获取全平台简历统计信息（仅管理员）
   */
  async getStatistics(): Promise<AdminResumeStats> {
    return request.get<AdminResumeStats>('/api/admin/resumes/statistics');
  },

  /**
   * 获取拥有简历的归属用户列表（仅管理员，用于筛选下拉）
   */
  async getOwners(): Promise<AdminResumeOwner[]> {
    return request.get<AdminResumeOwner[]>('/api/admin/resumes/owners');
  },

  /**
   * 获取任意用户的简历详情（仅管理员）
   */
  async getResumeDetail(id: number, signal?: AbortSignal): Promise<ResumeDetail> {
    return request.get<ResumeDetail>(`/api/admin/resumes/${id}/detail`, { signal });
  },

  /**
   * 获取任意用户的面试详情（仅管理员）
   */
  async getInterviewDetail(sessionId: string): Promise<InterviewDetail> {
    return request.get<InterviewDetail>(`/api/admin/interview/sessions/${sessionId}/details`);
  },

  /**
   * 导出任意用户的简历分析报告PDF（仅管理员）
   */
  async exportAnalysisPdf(resumeId: number): Promise<Blob> {
    return request.download(`/api/admin/resumes/${resumeId}/export`);
  },

  /**
   * 导出任意用户的面试报告PDF（仅管理员）
   */
  async exportInterviewPdf(sessionId: string): Promise<Blob> {
    return request.download(`/api/admin/interview/sessions/${sessionId}/export`);
  },
};
