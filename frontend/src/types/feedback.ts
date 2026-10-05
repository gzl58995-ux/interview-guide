export type FeedbackCategory = 'BUG' | 'SUGGESTION' | 'EXPERIENCE' | 'OTHER';

export type FeedbackStatus = 'PENDING' | 'PROCESSED';

export interface FeedbackItem {
  id: number;
  userId: number;
  username: string;
  category: FeedbackCategory;
  content: string;
  pagePath: string | null;
  status: FeedbackStatus;
  handledAt: string | null;
  createdAt: string;
}

export interface CreateFeedbackPayload {
  category: FeedbackCategory;
  content: string;
  pagePath?: string;
}

export interface AdminFeedbackQuery {
  status?: FeedbackStatus;
  category?: FeedbackCategory;
  page?: number;
  size?: number;
}
