import type { ReactNode } from 'react';
import { Moon, Sparkles, Sun } from 'lucide-react';
import { useTheme } from '../hooks/useTheme';

interface AuthPageShellProps {
  title: string;
  subtitle: string;
  children: ReactNode;
}

export default function AuthPageShell({ title, subtitle, children }: AuthPageShellProps) {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4 py-10 bg-gradient-to-br from-slate-50 via-white to-indigo-50 dark:from-slate-950 dark:via-slate-900 dark:to-slate-800">
      <button
        onClick={toggleTheme}
        title={theme === 'dark' ? '切换为浅色模式' : '切换为深色模式'}
        className="absolute top-6 right-6 p-2.5 rounded-xl bg-white/80 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white transition-colors"
      >
        {theme === 'dark' ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
      </button>

      <div className="w-full max-w-md">
        <div className="flex flex-col items-center mb-8">
          <div className="w-14 h-14 bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30 mb-4">
            <Sparkles className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">AI Interview</h1>
          <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">智能面试助手</p>
        </div>

        <div className="bg-white dark:bg-slate-800 rounded-2xl border border-slate-100 dark:border-slate-700 shadow-xl shadow-slate-200/60 dark:shadow-slate-900/50 p-8">
          <div className="mb-6">
            <h2 className="text-xl font-bold text-slate-800 dark:text-white">{title}</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">{subtitle}</p>
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}
