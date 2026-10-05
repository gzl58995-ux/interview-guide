import type { FeedbackCategory, FeedbackStatus } from '../types/feedback';

export interface FeedbackCategoryOption {
  value: FeedbackCategory;
  label: string;
  className: string;
}

export const FEEDBACK_CATEGORIES: FeedbackCategoryOption[] = [
  {
    value: 'BUG',
    label: '功能异常',
    className: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  },
  {
    value: 'SUGGESTION',
    label: '改进建议',
    className: 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400',
  },
  {
    value: 'EXPERIENCE',
    label: '使用体验',
    className: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  },
  {
    value: 'OTHER',
    label: '其他',
    className: 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300',
  },
];

export const FEEDBACK_STATUS_LABELS: Record<FeedbackStatus, string> = {
  PENDING: '待处理',
  PROCESSED: '已处理',
};

export function categoryLabel(value: string): string {
  return FEEDBACK_CATEGORIES.find(item => item.value === value)?.label ?? value;
}

export function statusLabel(value: string): string {
  return FEEDBACK_STATUS_LABELS[value as FeedbackStatus] ?? value;
}

export function validateFeedbackContent(content: string): string | null {
  const trimmed = content.trim();
  if (trimmed.length === 0) {
    return '反馈内容不能为空';
  }
  if (trimmed.length < 5 || trimmed.length > 1000) {
    return '反馈内容需为 5-1000 字';
  }
  return null;
}

export function normalizePagePath(path: string): string | undefined {
  const trimmed = path.trim();
  if (trimmed.length === 0) {
    return undefined;
  }
  return trimmed.slice(0, 512);
}
