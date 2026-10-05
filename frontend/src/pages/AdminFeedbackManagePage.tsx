import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, CheckCircle, RotateCw, Trash2, XCircle } from 'lucide-react';
import { feedbackApi } from '../api/feedback';
import { getErrorMessage } from '../api/request';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import { FEEDBACK_CATEGORIES, categoryLabel, statusLabel } from '../components/feedbackForm';
import type { FeedbackCategory, FeedbackItem, FeedbackStatus } from '../types/feedback';
import { formatDateTime } from '../utils/date';

const PAGE_SIZE = 20;

type StatusFilter = FeedbackStatus | 'ALL';
type CategoryFilter = FeedbackCategory | 'ALL';

const STATUS_FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: '全部状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSED', label: '已处理' },
];

interface ToastState {
  message: string;
  type: 'success' | 'error';
}

function categoryBadgeClass(category: string): string {
  return FEEDBACK_CATEGORIES.find(item => item.value === category)?.className
    ?? 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300';
}

function statusBadgeClass(status: FeedbackStatus): string {
  return status === 'PROCESSED'
    ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400'
    : 'bg-amber-50 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400';
}

export default function AdminFeedbackManagePage() {
  const [items, setItems] = useState<FeedbackItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<CategoryFilter>('ALL');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [actioningId, setActioningId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<FeedbackItem | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const showToast = useCallback((message: string, type: 'success' | 'error' = 'success') => {
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
    setToast({ message, type });
    toastTimerRef.current = window.setTimeout(() => setToast(null), 3000);
  }, []);

  useEffect(() => () => {
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
  }, []);

  const loadData = useCallback(async (silent = false) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    if (!silent) setLoading(true);

    try {
      const pageData = await feedbackApi.query({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        category: categoryFilter === 'ALL' ? undefined : categoryFilter,
        page,
        size: PAGE_SIZE,
      }, controller.signal);
      if (controller.signal.aborted) return;

      setLoadError('');
      setTotal(pageData.total);

      const serverTotalPages = Math.max(1, pageData.totalPages);
      setTotalPages(serverTotalPages);
      if (page > serverTotalPages) {
        setPage(serverTotalPages);
        return;
      }
      setItems(pageData.items);
    } catch (err) {
      if (controller.signal.aborted) return;
      if (!silent) setLoadError(getErrorMessage(err));
    } finally {
      if (!controller.signal.aborted && !silent) setLoading(false);
    }
  }, [statusFilter, categoryFilter, page]);

  useEffect(() => {
    loadData();
    return () => abortRef.current?.abort();
  }, [loadData]);

  const handleStatusChange = (value: StatusFilter) => {
    setStatusFilter(value);
    setPage(1);
  };

  const handleCategoryChange = (value: CategoryFilter) => {
    setCategoryFilter(value);
    setPage(1);
  };

  const handleToggleStatus = async (item: FeedbackItem) => {
    if (actioningId !== null) return;
    const target: FeedbackStatus = item.status === 'PENDING' ? 'PROCESSED' : 'PENDING';
    setActioningId(item.id);
    try {
      await feedbackApi.updateStatus(item.id, target);
      showToast(target === 'PROCESSED' ? '已标记为已处理' : '已标记为待处理');
      await loadData(true);
    } catch (err) {
      showToast(getErrorMessage(err), 'error');
    } finally {
      setActioningId(null);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    try {
      await feedbackApi.remove(deleteTarget.id);
      setDeleteTarget(null);
      showToast('已删除');
      await loadData(true);
    } catch (err) {
      showToast(getErrorMessage(err), 'error');
    } finally {
      setDeleting(false);
    }
  };

  const truncatedContent = deleteTarget && deleteTarget.content.length > 50
    ? `${deleteTarget.content.slice(0, 50)}...`
    : deleteTarget?.content ?? '';

  return (
    <motion.div
      className="w-full"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="flex flex-wrap items-center gap-3 mb-6">
        <select
          value={statusFilter}
          onChange={(event) => handleStatusChange(event.target.value as StatusFilter)}
          className="px-3 py-3 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
          title="按处理状态筛选"
        >
          {STATUS_FILTER_OPTIONS.map(option => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select
          value={categoryFilter}
          onChange={(event) => handleCategoryChange(event.target.value as CategoryFilter)}
          className="px-3 py-3 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
          title="按分类筛选"
        >
          <option value="ALL">全部分类</option>
          {FEEDBACK_CATEGORIES.map(item => (
            <option key={item.value} value={item.value}>{item.label}</option>
          ))}
        </select>
        <button
          onClick={() => loadData()}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-3 bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-200 rounded-xl hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors disabled:opacity-50"
          title="刷新列表"
        >
          <RotateCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`}/>
          刷新
        </button>
      </div>

      {loading && (
        <div className="text-center py-20">
          <div className="w-10 h-10 border-3 border-slate-200 dark:text-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin"/>
          <p className="text-slate-500 dark:text-slate-400">加载中...</p>
        </div>
      )}

      {!loading && loadError && (
        <div className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl">
          <AlertCircle className="w-12 h-12 text-red-400 mx-auto mb-4"/>
          <h3 className="text-lg font-semibold text-slate-700 dark:text-slate-300 mb-2">加载失败</h3>
          <p className="text-slate-500 dark:text-slate-400 mb-6">{loadError}</p>
          <button
            onClick={() => loadData()}
            className="px-5 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
          >
            重新加载
          </button>
        </div>
      )}

      {!loading && !loadError && items.length === 0 && (
        <motion.div
          className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <div className="text-6xl mb-6">💬</div>
          <h3 className="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">
            暂无留言
          </h3>
          <p className="text-slate-500 dark:text-slate-400">
            当前筛选条件下还没有用户反馈
          </p>
        </motion.div>
      )}

      {!loading && !loadError && items.length > 0 && (
        <>
          <div className="space-y-4">
            <AnimatePresence>
              {items.map((item, index) => (
                <motion.div
                  key={item.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.03 }}
                  className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm p-5"
                >
                  <div className="flex flex-wrap items-center gap-2 mb-3">
                    <span className={`inline-flex px-2.5 py-1 rounded-full text-xs font-medium ${categoryBadgeClass(item.category)}`}>
                      {categoryLabel(item.category)}
                    </span>
                    <span className={`inline-flex px-2.5 py-1 rounded-full text-xs font-medium ${statusBadgeClass(item.status)}`}>
                      {statusLabel(item.status)}
                    </span>
                    <span className="text-sm text-slate-600 dark:text-slate-300">
                      {item.username}
                    </span>
                    <span className="text-xs text-slate-400 dark:text-slate-500">
                      {formatDateTime(item.createdAt)}
                    </span>
                    {item.handledAt && (
                      <span className="text-xs text-slate-400 dark:text-slate-500">
                        处理于 {formatDateTime(item.handledAt)}
                      </span>
                    )}
                  </div>

                  <p className="whitespace-pre-wrap break-words text-slate-700 dark:text-slate-200">
                    {item.content}
                  </p>

                  <div className="flex flex-wrap items-center justify-between gap-3 mt-4">
                    <span className="text-xs text-slate-400 dark:text-slate-500 font-mono break-all">
                      {item.pagePath ?? '-'}
                    </span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleToggleStatus(item)}
                        disabled={actioningId !== null}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-primary-500 dark:text-primary-400 bg-primary-50 dark:bg-primary-900/30 hover:bg-primary-100 dark:hover:bg-primary-900/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {actioningId === item.id
                          ? <RotateCw className="w-4 h-4 animate-spin"/>
                          : <CheckCircle className="w-4 h-4"/>}
                        {item.status === 'PENDING' ? '标记已处理' : '标记待处理'}
                      </button>
                      <button
                        onClick={() => setDeleteTarget(item)}
                        disabled={deleting}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-red-500 dark:text-red-400 bg-red-50 dark:bg-red-900/30 hover:bg-red-100 dark:hover:bg-red-900/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        <Trash2 className="w-4 h-4"/>
                        删除
                      </button>
                    </div>
                  </div>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>

          {total > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-3 mt-4 px-1 text-sm text-slate-500 dark:text-slate-400">
              <span>
                共 <span className="font-semibold text-slate-700 dark:text-slate-200">{total}</span> 条
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage(page - 1)}
                  disabled={page <= 1}
                  className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  上一页
                </button>
                <span className="text-slate-700 dark:text-slate-200">{page} / {totalPages}</span>
                <button
                  onClick={() => setPage(page + 1)}
                  disabled={page >= totalPages}
                  className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  下一页
                </button>
              </div>
            </div>
          )}
        </>
      )}

      <DeleteConfirmDialog
        open={deleteTarget !== null}
        item={deleteTarget ? { id: deleteTarget.id, name: `#${deleteTarget.id}` } : null}
        itemType="留言"
        loading={deleting}
        customMessage={deleteTarget ? (
          <div>
            <p>确定要删除这条留言吗？删除后无法恢复。</p>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 break-words">
              “{truncatedContent}”
            </p>
          </div>
        ) : null}
        onConfirm={handleDelete}
        onCancel={() => {
          if (!deleting) setDeleteTarget(null);
        }}
      />

      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: 50, x: '-50%' }}
            animate={{ opacity: 1, y: 0, x: '-50%' }}
            exit={{ opacity: 0, y: 50, x: '-50%' }}
            className={`fixed bottom-6 left-1/2 px-5 py-3 rounded-xl shadow-lg text-sm font-medium
              flex items-center gap-2 z-[60] ${
                toast.type === 'success'
                  ? 'bg-emerald-600 text-white'
                  : 'bg-red-600 text-white'
              }`}
          >
            {toast.type === 'success'
              ? <CheckCircle className="w-4 h-4" />
              : <XCircle className="w-4 h-4" />
            }
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
