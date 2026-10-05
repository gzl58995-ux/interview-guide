import { request } from './request';
import type { PageResult } from '../types/page';
import type { AdminFeedbackQuery, CreateFeedbackPayload, FeedbackItem, FeedbackStatus } from '../types/feedback';

export const feedbackApi = {
  async submit(payload: CreateFeedbackPayload): Promise<void> {
    return request.post<void>('/api/feedback', payload);
  },

  async query(query: AdminFeedbackQuery, signal?: AbortSignal): Promise<PageResult<FeedbackItem>> {
    return request.get<PageResult<FeedbackItem>>('/api/admin/feedback', { params: query, signal });
  },

  async updateStatus(id: number, status: FeedbackStatus): Promise<FeedbackItem> {
    return request.patch<FeedbackItem>(`/api/admin/feedback/${id}/status`, null, { params: { status } });
  },

  async remove(id: number): Promise<void> {
    return request.delete<void>(`/api/admin/feedback/${id}`);
  },
};
