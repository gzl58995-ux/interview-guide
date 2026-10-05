import type { ComponentType } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { motion } from 'framer-motion';
import { FileStack, MessageSquare, ShieldCheck } from 'lucide-react';
import { ROUTES } from '../constants/routes';

interface AdminTab {
  id: string;
  path: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
}

const adminTabs: AdminTab[] = [
  { id: 'resumes', path: ROUTES.adminResumes, label: '简历管理', icon: FileStack },
  { id: 'feedback', path: ROUTES.adminFeedback, label: '用户反馈', icon: MessageSquare },
];

export default function AdminPage() {
  return (
    <motion.div
      className="w-full"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      {/* 头部 */}
      <div className="flex items-center gap-4 mb-8">
        <div
          className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
          <ShieldCheck className="w-6 h-6"/>
        </div>
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">管理员页面</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">平台级数据管理，仅管理员可见</p>
        </div>
      </div>

      {/* 子页面导航 */}
      <nav className="flex flex-wrap gap-2 mb-6">
        {adminTabs.map(tab => (
          <NavLink
            key={tab.id}
            to={tab.path}
            className={({ isActive }) =>
              `flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary-500 text-white shadow-sm shadow-primary-500/30'
                  : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-700'
              }`
            }
          >
            <tab.icon className="w-4 h-4"/>
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <Outlet/>
    </motion.div>
  );
}
