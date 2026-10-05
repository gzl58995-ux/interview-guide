import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { CheckCircle2, Loader2, MessageSquarePlus } from 'lucide-react';
import { feedbackApi } from '../api/feedback';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';
import { useAuth } from '../hooks/useAuth';
import type { FeedbackCategory } from '../types/feedback';
import {
  FEEDBACK_CATEGORIES,
  normalizePagePath,
  validateFeedbackContent,
} from './feedbackForm';

export interface FeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const CLOSE_DELAY_MS = 1500;

export default function FeedbackModal({ isOpen, onClose }: FeedbackModalProps) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [category, setCategory] = useState<FeedbackCategory>('SUGGESTION');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState('');
  const closeTimerRef = useRef<number | null>(null);

  const clearCloseTimer = useCallback(() => {
    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
  }, []);

  const resetForm = useCallback(() => {
    setCategory('SUGGESTION');
    setContent('');
    setSubmitting(false);
    setSucceeded(false);
    setError('');
  }, []);

  useEffect(() => clearCloseTimer, [clearCloseTimer]);

  const handleClose = useCallback(() => {
    clearCloseTimer();
    resetForm();
    onClose();
  }, [clearCloseTimer, onClose, resetForm]);

  const validationError = validateFeedbackContent(content);
  const canSubmit = !validationError && !submitting;

  const handleSubmit = async () => {
    if (!canSubmit) {
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await feedbackApi.submit({
        category,
        content: content.trim(),
        pagePath: normalizePagePath(window.location.pathname),
      });
      setSucceeded(true);
      setSubmitting(false);
      closeTimerRef.current = window.setTimeout(() => {
        resetForm();
        onClose();
      }, CLOSE_DELAY_MS);
    } catch (err) {
      setSubmitting(false);
      setError(getErrorMessage(err));
    }
  };

  if (!isOpen) {
    return null;
  }

  const pagePath = normalizePagePath(window.location.pathname) ?? '/';

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
          />

          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-lg w-full p-6"
            >
              <div className="flex items-center gap-3 mb-5">
                <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
                  <MessageSquarePlus className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-slate-900 dark:text-white">意见反馈</h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400">你的建议会直接传达给开发者</p>
                </div>
              </div>

              {!user ? (
                <div className="text-center py-8">
                  <p className="text-slate-600 dark:text-slate-300 mb-6">登录后才能提交反馈</p>
                  <button
                    onClick={() => {
                      handleClose();
                      navigate(ROUTES.login);
                    }}
                    className="px-5 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:from-primary-600 hover:to-primary-700 transition-all"
                  >
                    去登录
                  </button>
                </div>
              ) : succeeded ? (
                <div className="text-center py-8">
                  <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-3" />
                  <p className="text-lg font-semibold text-slate-800 dark:text-white">感谢反馈</p>
                  <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">我们会尽快查看并改进</p>
                </div>
              ) : (
                <>
                  <div className="mb-4">
                    <span className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
                      反馈类型
                    </span>
                    <div className="flex flex-wrap gap-2">
                      {FEEDBACK_CATEGORIES.map((item) => {
                        const active = item.value === category;
                        return (
                          <button
                            key={item.value}
                            type="button"
                            onClick={() => setCategory(item.value)}
                            className={`px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors ${
                              active
                                ? `${item.className} border-transparent ring-2 ring-primary-500/40`
                                : 'border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                            }`}
                          >
                            {item.label}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="mb-4">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium text-slate-700 dark:text-slate-300">详细描述</span>
                      <span className="text-xs text-slate-400 dark:text-slate-500">{content.length}/1000</span>
                    </div>
                    <textarea
                      value={content}
                      onChange={(e) => setContent(e.target.value)}
                      maxLength={1000}
                      rows={5}
                      placeholder="描述你遇到的问题或建议，5-1000 字"
                      className="w-full px-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500/40 focus:border-primary-500 resize-none"
                    />
                    <p className="text-xs text-slate-400 dark:text-slate-500 mt-1.5">
                      将附带当前页面：{pagePath}
                    </p>
                  </div>

                  {error && (
                    <p className="text-sm text-red-500 dark:text-red-400 mb-4">{error}</p>
                  )}

                  <div className="flex gap-3 justify-end">
                    <motion.button
                      type="button"
                      onClick={handleClose}
                      disabled={submitting}
                      className="px-5 py-2.5 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 rounded-xl font-medium hover:bg-slate-50 dark:hover:bg-slate-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                      whileHover={{ scale: 1.02 }}
                      whileTap={{ scale: 0.98 }}
                    >
                      取消
                    </motion.button>
                    <motion.button
                      type="button"
                      onClick={handleSubmit}
                      disabled={!canSubmit}
                      className="px-5 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:from-primary-600 hover:to-primary-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                      whileHover={{ scale: canSubmit ? 1.02 : 1 }}
                      whileTap={{ scale: canSubmit ? 0.98 : 1 }}
                    >
                      {submitting ? (
                        <span className="flex items-center gap-2">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          提交中...
                        </span>
                      ) : (
                        '提交反馈'
                      )}
                    </motion.button>
                  </div>
                </>
              )}
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
