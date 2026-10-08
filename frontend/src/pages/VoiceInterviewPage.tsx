import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Clock, PhoneOff, AlertCircle, Bot, Mic, ArrowLeft, SendHorizonal } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import AudioRecorder from '../components/AudioRecorder';
import InterviewPageHeader from '../components/InterviewPageHeader';
import RealtimeSubtitle from '../components/RealtimeSubtitle';
import { skillApi, type SkillDTO } from '../api/skill';
import { getTemplateName } from '../utils/voiceInterview';
import {
  voiceInterviewApi,
  connectWebSocket,
  VoiceInterviewWebSocket,
} from '../api/voiceInterview';

type VoiceConfig = {
  skillId: string;
  difficulty?: string;
  jobDirection?: string;
  techEnabled: boolean;
  projectEnabled: boolean;
  hrEnabled: boolean;
  plannedDuration: number;
  resumeId?: number;
  llmProvider?: string;
};

export default function VoiceInterviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const entryState = (location.state as {
    voiceConfig?: VoiceConfig;
    voiceSessionId?: number;
  } | null) || {};
  const resumeSessionId = entryState.voiceSessionId;
  const queryParams = new URLSearchParams(location.search);
  const urlSkillId = queryParams.get('skillId') || undefined;
  const urlDifficulty = queryParams.get('difficulty') || undefined;
  const urlDuration = Number(queryParams.get('duration') || queryParams.get('plannedDuration'));
  const queryVoiceConfig: VoiceConfig | undefined = urlSkillId
    ? {
        skillId: urlSkillId,
        difficulty: urlDifficulty,
        techEnabled: true,
        projectEnabled: true,
        hrEnabled: true,
        plannedDuration: Number.isFinite(urlDuration) && urlDuration > 0 ? urlDuration : 15,
      }
    : undefined;
  const presetVoiceConfig = entryState.voiceConfig ?? queryVoiceConfig;
  const effectiveSkillId = presetVoiceConfig?.skillId ?? urlSkillId ?? 'java-backend';

  const [isRecording, setIsRecording] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [currentPhase, setCurrentPhase] = useState('INTRO');
  const [connectionStatus, setConnectionStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');

  const [userText, setUserText] = useState('');
  const [aiText, setAiText] = useState('');
  const [messages, setMessages] = useState<{ role: 'user' | 'ai'; text: string; id: string }[]>([]);
  const [isAiSpeaking, setIsAiSpeaking] = useState(false);
  const [aiAudio, setAiAudio] = useState('');
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [templateName, setTemplateName] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isAsrReady, setIsAsrReady] = useState(false);

  const [skills, setSkills] = useState<SkillDTO[]>([]);

  // Refs
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const wsRef = useRef<VoiceInterviewWebSocket | null>(null);
  const audioPlayerRef = useRef<HTMLAudioElement>(null);
  const pauseTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const autoStartRef = useRef(false);
  const endedByUserRef = useRef(false);
  const isAiSpeakingRef = useRef(false);
  const isAsrReadyRef = useRef(false);
  const isSubmittingRef = useRef(false);
  const aiAudioPendingRef = useRef(false);
  const lastAiCommittedTextRef = useRef('');
  const pendingAiTextCommitRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const audioPlaybackWatchdogRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Chunked audio playback refs
  const audioContextRef = useRef<AudioContext | null>(null);
  const chunkQueueRef = useRef<AudioBuffer[]>([]);
  const isChunkPlayingRef = useRef(false);
  const chunkPlaybackSourceRef = useRef<AudioBufferSourceNode | null>(null);
  const drainCheckRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Ref to track latest aiText for async callbacks (avoids stale closure)
  const aiTextRef = useRef('');
  useEffect(() => { aiTextRef.current = aiText; }, [aiText]);
  useEffect(() => { isAsrReadyRef.current = isAsrReady; }, [isAsrReady]);

  const setAiSpeaking = useCallback((value: boolean) => {
    isAiSpeakingRef.current = value;
    setIsAiSpeaking(value);
  }, []);

  const setSubmitting = useCallback((value: boolean) => {
    isSubmittingRef.current = value;
    setIsSubmitting(value);
  }, []);

  const clearPendingAiTextCommit = useCallback(() => {
    if (pendingAiTextCommitRef.current) {
      clearTimeout(pendingAiTextCommitRef.current);
      pendingAiTextCommitRef.current = null;
    }
  }, []);

  const clearAudioPlaybackWatchdog = useCallback(() => {
    if (audioPlaybackWatchdogRef.current) {
      clearTimeout(audioPlaybackWatchdogRef.current);
      audioPlaybackWatchdogRef.current = null;
    }
  }, []);

  const commitAiMessage = useCallback((rawText: string) => {
    const normalized = (rawText || '').trim();
    if (!normalized || normalized === lastAiCommittedTextRef.current) {
      return;
    }
    setMessages(prev => {
      const last = prev[prev.length - 1];
      if (last?.role === 'ai' && last.text.trim() === normalized) {
        return prev;
      }
      return [
        ...prev,
        { role: 'ai', text: normalized, id: `ai-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }
      ];
    });
    lastAiCommittedTextRef.current = normalized;
    setAiText(prev => prev?.trim() === normalized ? '' : prev);
  }, []);

  const estimateWavDurationMs = useCallback((base64Wav: string) => {
    try {
      const binary = atob(base64Wav.slice(0, 128));
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      if (bytes.length < 44) {
        return 15_000;
      }
      const view = new DataView(bytes.buffer);
      const byteRate = view.getUint32(28, true);
      const dataSize = view.getUint32(40, true);
      if (byteRate <= 0 || dataSize <= 0) {
        return 15_000;
      }
      return Math.ceil((dataSize / byteRate) * 1000);
    } catch {
      return 15_000;
    }
  }, []);

  const finishAiPlayback = useCallback(() => {
    aiAudioPendingRef.current = false;
    clearAudioPlaybackWatchdog();
    setAiSpeaking(false);
    setSubmitting(false);
    clearPendingAiTextCommit();
    commitAiMessage(aiTextRef.current.trim());
    setAiText('');
    setAiAudio('');
  }, [
    clearAudioPlaybackWatchdog,
    clearPendingAiTextCommit,
    commitAiMessage,
    setAiSpeaking,
    setSubmitting,
  ]);

  // --- Chunked audio playback via AudioContext ---
  const getAudioContext = useCallback(() => {
    if (!audioContextRef.current || audioContextRef.current.state === 'closed') {
      audioContextRef.current = new AudioContext({ sampleRate: 24000 });
    }
    return audioContextRef.current;
  }, []);

  const playNextChunk = useCallback(() => {
    if (chunkQueueRef.current.length === 0) {
      isChunkPlayingRef.current = false;
      return;
    }
    isChunkPlayingRef.current = true;
    const ctx = getAudioContext();
    if (ctx.state === 'suspended') {
      ctx.resume();
    }
    const buffer = chunkQueueRef.current.shift()!;
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);
    chunkPlaybackSourceRef.current = source;
    source.onended = () => {
      chunkPlaybackSourceRef.current = null;
      playNextChunk();
    };
    source.start(0);
  }, [getAudioContext]);

  const scheduleChunkDrainCompletion = useCallback(() => {
    const startedAt = Date.now();
    const maxDrainWaitMs = 30_000;
    if (drainCheckRef.current) {
      clearInterval(drainCheckRef.current);
    }
    drainCheckRef.current = setInterval(() => {
      if (chunkQueueRef.current.length === 0 && !isChunkPlayingRef.current) {
        clearInterval(drainCheckRef.current!);
        drainCheckRef.current = null;
        setAiSpeaking(false);
        setSubmitting(false);
        clearPendingAiTextCommit();
        commitAiMessage(aiTextRef.current.trim());
        setAiText('');
      } else if (Date.now() - startedAt > maxDrainWaitMs) {
        clearInterval(drainCheckRef.current!);
        drainCheckRef.current = null;
        setAiSpeaking(false);
        setSubmitting(false);
      }
    }, 100);
  }, [clearPendingAiTextCommit, commitAiMessage, setAiSpeaking, setSubmitting]);

  const handleAudioChunk = useCallback((base64Wav: string, _index: number, isLast: boolean) => {
    try {
      aiAudioPendingRef.current = false;
      clearPendingAiTextCommit();
      const binaryStr = atob(base64Wav);
      const bytes = new Uint8Array(binaryStr.length);
      for (let i = 0; i < binaryStr.length; i++) {
        bytes[i] = binaryStr.charCodeAt(i);
      }
      const pcmOffset = 44;
      const pcmData = new Int16Array(bytes.buffer, pcmOffset, (bytes.length - pcmOffset) / 2);
      const float32 = new Float32Array(pcmData.length);
      for (let i = 0; i < pcmData.length; i++) {
        float32[i] = pcmData[i] / 32768.0;
      }

      const ctx = getAudioContext();
      const audioBuffer = ctx.createBuffer(1, float32.length, 24000);
      audioBuffer.getChannelData(0).set(float32);

      chunkQueueRef.current.push(audioBuffer);
      if (!isChunkPlayingRef.current) {
        playNextChunk();
      }

      setAiSpeaking(true);

      if (isLast) {
        scheduleChunkDrainCompletion();
      }
    } catch (e) {
      console.error('[ChunkAudio] Decode/play error:', e);
    }
  }, [getAudioContext, playNextChunk, scheduleChunkDrainCompletion, setAiSpeaking]);

  // Load skills for template name display
  useEffect(() => {
    skillApi.listSkills().then(setSkills).catch(console.error);
  }, []);

  // Derive template name from skills
  useEffect(() => {
    if (skills.length > 0 && effectiveSkillId) {
      setTemplateName(getTemplateName(effectiveSkillId, skills));
    }
  }, [skills, effectiveSkillId]);

  // Cleanup on unmount — 自动暂停未结束的 session
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
      if (wsRef.current) {
        wsRef.current.disconnect();
      }
      clearAudioPlaybackWatchdog();
      chunkPlaybackSourceRef.current?.stop();
      audioContextRef.current?.close();
      if (drainCheckRef.current) {
        clearInterval(drainCheckRef.current);
        drainCheckRef.current = null;
      }
      clearPendingAiTextCommit();
      // 用户没有主动结束/暂停时，自动暂停 session
      const currentSessionId = sessionId;
      if (currentSessionId && !endedByUserRef.current) {
        voiceInterviewApi.pauseSession(currentSessionId).catch(() => {});
      }
    };
  }, [clearAudioPlaybackWatchdog, clearPendingAiTextCommit, sessionId]);

  // Start interview timer
  useEffect(() => {
    if (sessionId && connectionStatus === 'connected') {
      startTimer();
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [sessionId, connectionStatus]);

  // Auto-play audio when aiAudio changes
  useEffect(() => {
    if (aiAudio && audioPlayerRef.current) {
      const playPromise = audioPlayerRef.current.play();
      if (playPromise !== undefined) {
        playPromise.catch(() => {
          setError('请点击页面任意位置以启用音频播放');
          finishAiPlayback();
        });
      }
    }
  }, [aiAudio, finishAiPlayback]);

  const startTimer = () => {
    timerRef.current = setInterval(() => {
      setCurrentTime((prev) => prev + 1);
    }, 1000);
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const getPhaseLabel = (phase: string) => {
    const phaseMap: Record<string, string> = {
      INTRO: '自我介绍',
      TECH: '技术问题',
      PROJECT: '项目深挖',
      HR: 'HR问题',
    };
    return phaseMap[phase] || phase;
  };

  // 手动提交回答：ASR 只负责生成可编辑文本，不自动触发 LLM
  const handleSubmitAnswer = useCallback(() => {
    if (!wsRef.current || !wsRef.current.isConnected()) {
      return;
    }
    if (!userText.trim() || isAiSpeakingRef.current || isSubmitting) {
      return;
    }
    setIsRecording(false);
    setSubmitting(true);
    const text = userText.trim();
    setMessages(prev => [
      ...prev,
      { role: 'user', text, id: `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }
    ]);
    setUserText('');
    wsRef.current.sendControl('submit', { text });
  }, [userText, isSubmitting, setSubmitting]);

  const createWebSocketHandlers = useCallback(() => ({
    onOpen: () => {
      setConnectionStatus('connected');
      setIsAsrReady(false);
    },
    onMessage: () => {},
    onSubtitle: (text: string, isFinal: boolean) => {
      // 上一轮提交后可能仍收到迟到的 ASR partial，不允许它覆盖下一轮输入区。
      if (!isFinal && (isSubmittingRef.current || isAiSpeakingRef.current)) {
        return;
      }
      // isFinal=true 由 triggerLlmResponse 触发，表示本轮用户回答已提交到 LLM
      if (isFinal && text.trim()) {
        setMessages(prev => {
          const last = prev[prev.length - 1];
          if (last?.role === 'user' && last.text.trim() === text.trim()) {
            return prev;
          }
          return [
            ...prev,
            { role: 'user', text: text.trim(), id: `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }
          ];
        });
        setUserText('');
      } else {
        setUserText(text);
      }
    },
    onAudioResponse: (audioData: string, text: string) => {
      const hasAudio = !!(audioData && audioData.length > 0);
      const normalized = (text || '').trim();
      if (hasAudio) {
        clearPendingAiTextCommit();
        clearAudioPlaybackWatchdog();
        aiAudioPendingRef.current = false;
        setAiAudio(audioData);
        setAiText(normalized);
        setAiSpeaking(true);
        const durationMs = estimateWavDurationMs(audioData);
        audioPlaybackWatchdogRef.current = setTimeout(
          finishAiPlayback,
          Math.min(Math.max(durationMs + 1500, 4000), 60_000)
        );
        return;
      }
      setAiAudio('');
      setAiText(normalized);
      setAiSpeaking(false);
      if (!normalized) {
        setSubmitting(false);
        return;
      }
      clearPendingAiTextCommit();
      pendingAiTextCommitRef.current = setTimeout(() => {
        commitAiMessage(normalized);
        setSubmitting(false);
        setAiSpeaking(false);
        pendingAiTextCommitRef.current = null;
      }, 2500);
    },
    onTextResponse: (text: string, isFinal: boolean) => {
      const normalized = (text || '').trim();
      if (!normalized) {
        return;
      }
      aiAudioPendingRef.current = isFinal;
      setAiText(normalized);
      setAiSpeaking(true);
      if (!isFinal) {
        return;
      }

      clearPendingAiTextCommit();
      pendingAiTextCommitRef.current = setTimeout(() => {
        if (aiAudioPendingRef.current) {
          aiAudioPendingRef.current = false;
        }
        commitAiMessage(normalized);
        setSubmitting(false);
        setAiSpeaking(false);
        pendingAiTextCommitRef.current = null;
      }, 15000);
    },
    onClose: (event: { code: number }) => {
      setConnectionStatus('disconnected');
      setIsAsrReady(false);
      clearPendingAiTextCommit();
      if (event.code !== 1000) {
        setError('连接已断开，请刷新页面重试');
      }
    },
    onError: () => {
      clearPendingAiTextCommit();
      clearAudioPlaybackWatchdog();
      setError('WebSocket 连接错误，请检查网络后重试');
      setConnectionStatus('disconnected');
      setIsAsrReady(false);
    },
    onAudioChunk: (data: string, index: number, isLast: boolean) => {
      handleAudioChunk(data, index, isLast);
    },
    onControl: (action: string, message?: string) => {
      if (action === 'asr_ready') {
        setIsAsrReady(true);
        setError(null);
        return;
      }
      if (action === 'asr_reconnecting') {
        setIsAsrReady(false);
        if (message) {
          setError(message);
        }
        return;
      }
      if (action === 'audio_complete') {
        scheduleChunkDrainCompletion();
        return;
      }
      if (action === 'pause_timeout_warning' && message) {
        setError(message);
        return;
      }
      if (action === 'pause_timeout' && message) {
        setError(message);
        setConnectionStatus('disconnected');
        setIsAsrReady(false);
      }
    },
    onErrorMessage: (message: string) => {
      setError(message || '语音面试服务异常，请稍后重试');
      if (message.includes('语音识别')) {
        setIsAsrReady(false);
      }
    },
  }), [
    clearAudioPlaybackWatchdog,
    clearPendingAiTextCommit,
    commitAiMessage,
    estimateWavDurationMs,
    finishAiPlayback,
    handleAudioChunk,
    scheduleChunkDrainCompletion,
    setAiSpeaking,
    setSubmitting,
  ]);

  const connectWithHandlers = useCallback((sessionId: number) => {
    setIsAsrReady(false);
    setTimeout(() => {
      try {
        wsRef.current = connectWebSocket(sessionId, createWebSocketHandlers());
      } catch (error) {
        setError('无法建立 WebSocket 连接: ' + (error instanceof Error ? error.message : '未知错误'));
        setConnectionStatus('disconnected');
        setIsAsrReady(false);
      }
    }, 500);
  }, [createWebSocketHandlers]);

  const handlePhaseConfig = useCallback(async (config: {
    skillId: string;
    difficulty?: string;
    jobDirection?: string;
    techEnabled: boolean;
    projectEnabled: boolean;
    hrEnabled: boolean;
    plannedDuration: number;
    resumeId?: number;
    llmProvider?: string;
  }) => {
    setError(null);
    setConnectionStatus('connecting');
    setIsAsrReady(false);

    try {
      const session = await voiceInterviewApi.createSession({
        skillId: config.skillId,
        difficulty: config.difficulty,
        jobDirection: config.jobDirection,
        introEnabled: false,
        techEnabled: config.techEnabled,
        projectEnabled: config.projectEnabled,
        hrEnabled: config.hrEnabled,
        plannedDuration: config.plannedDuration,
        resumeId: config.resumeId,
        llmProvider: config.llmProvider,
      });

      setSessionId(session.sessionId);
      setCurrentPhase(session.currentPhase);

      connectWithHandlers(session.sessionId);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '创建面试会话失败，请重试';
      setError(errorMessage);
      setConnectionStatus('disconnected');
      setIsAsrReady(false);
      alert('创建会话失败：' + errorMessage);
    }
  }, [connectWithHandlers]);

  const handleResumeSession = useCallback(async (id: number) => {
    setError(null);
    setConnectionStatus('connecting');
    setIsAsrReady(false);

    try {
      const [session, history] = await Promise.all([
        voiceInterviewApi.resumeSession(id),
        voiceInterviewApi.getMessages(id),
      ]);
      setSessionId(session.sessionId);
      setCurrentPhase(session.currentPhase);

      if (session.startTime) {
        const elapsedSec = Math.floor((Date.now() - new Date(session.startTime).getTime()) / 1000);
        setCurrentTime(elapsedSec > 0 ? elapsedSec : 0);
      }

      const restored: { role: 'user' | 'ai'; text: string; id: string }[] = [];
      let pendingAi: { text: string; id: string } | null = null;
      for (const msg of history) {
        const aiText = msg.aiGeneratedText?.trim();
        const userText = msg.userRecognizedText?.trim();

        if (pendingAi) {
          restored.push({ role: 'ai', text: pendingAi.text, id: pendingAi.id });
          pendingAi = null;
          if (userText) {
            restored.push({ role: 'user', text: userText, id: `user-${msg.id}` });
          }
          if (aiText) {
            pendingAi = { text: aiText, id: `ai-${msg.id}` };
          }
          continue;
        }

        if (aiText && userText) {
          restored.push({ role: 'ai', text: aiText, id: `ai-${msg.id}` });
          restored.push({ role: 'user', text: userText, id: `user-${msg.id}` });
        } else if (aiText) {
          pendingAi = { text: aiText, id: `ai-${msg.id}` };
        } else if (userText) {
          restored.push({ role: 'user', text: userText, id: `user-${msg.id}` });
        }
      }
      if (pendingAi) {
        restored.push({ role: 'ai', text: pendingAi.text, id: pendingAi.id });
      }
      setMessages(restored);

      connectWithHandlers(session.sessionId);
    } catch (error) {
      setError(error instanceof Error ? error.message : '恢复会话失败');
      setConnectionStatus('disconnected');
      setIsAsrReady(false);
    }
  }, [connectWithHandlers]);

  // Auto-start: 新建 or 恢复
  useEffect(() => {
    if (autoStartRef.current) return;

    if (presetVoiceConfig) {
      autoStartRef.current = true;
      handlePhaseConfig({
        skillId: presetVoiceConfig.skillId,
        difficulty: presetVoiceConfig.difficulty,
        jobDirection: presetVoiceConfig.jobDirection,
        techEnabled: presetVoiceConfig.techEnabled,
        projectEnabled: presetVoiceConfig.projectEnabled,
        hrEnabled: presetVoiceConfig.hrEnabled,
        plannedDuration: presetVoiceConfig.plannedDuration,
        resumeId: presetVoiceConfig.resumeId,
        llmProvider: presetVoiceConfig.llmProvider,
      });
    } else if (resumeSessionId) {
      autoStartRef.current = true;
      handleResumeSession(resumeSessionId);
    }
  }, [handlePhaseConfig, handleResumeSession, presetVoiceConfig, resumeSessionId]);

  // 麦克风音频持续发送给服务端做 ASR；仅 AI 播放时停发，避免回声进入识别
  const handleAudioData = (audioData: string) => {
    if (isAiSpeakingRef.current || isSubmittingRef.current) {
      return;
    }
    if (!isAsrReadyRef.current) {
      return;
    }
    if (wsRef.current && wsRef.current.isConnected()) {
      wsRef.current.sendAudio(audioData);
    } else {
      setError('未连接到服务器，请刷新页面重试');
    }
  };

  const handleSpeechStart = () => {};
  const handleSpeechEnd = () => {};

  const handlePause = async (type: 'short' | 'long') => {
    if (!sessionId) return;

    if (type === 'short') {
      setIsRecording(false);
      pauseTimeoutRef.current = setTimeout(() => {
        handleLongPause();
      }, 5 * 60 * 1000);
    } else {
      await handleLongPause();
    }
  };

  const handleLongPause = async () => {
    endedByUserRef.current = true;
    if (pauseTimeoutRef.current) {
      clearTimeout(pauseTimeoutRef.current);
      pauseTimeoutRef.current = null;
    }
    if (wsRef.current) {
      wsRef.current.disconnect();
    }
    if (isRecording) {
      setIsRecording(false);
    }
    if (!sessionId) return;
    try {
      await voiceInterviewApi.pauseSession(sessionId);
      navigate('/interviews');
    } catch (error) {
      alert('暂停失败，请重试');
    }
  };

  const handleEndInterview = async () => {
    endedByUserRef.current = true;
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
    if (wsRef.current) {
      wsRef.current.disconnect();
    }
    if (sessionId) {
      try {
        await voiceInterviewApi.endSession(sessionId);
      } catch (error) {
        console.error('Failed to end session:', error);
      }
    }
    navigate('/interviews');
  };

  const handleCloseModal = () => {
    navigate('/history');
  };

  // 提交按钮是否可用
  const canSubmit = !!userText.trim() && !isAiSpeaking && !isSubmitting && connectionStatus === 'connected';
  const canRecord = connectionStatus === 'connected' && isAsrReady && !isAiSpeaking && !isSubmitting;
  const recorderHint = connectionStatus !== 'connected'
    ? '正在连接服务器...'
    : !isAsrReady
      ? '语音识别准备中...'
      : isAiSpeaking
        ? '面试官正在回答...'
        : isSubmitting
          ? '正在思考...'
          : isRecording
            ? '正在聆听，说完后点击提交回答...'
            : '点击麦克风开始发言';
  const footerHint = isAiSpeaking
    ? '面试官正在回答...'
    : isSubmitting
      ? '正在思考...'
      : connectionStatus !== 'connected'
        ? '正在连接服务器'
        : !isAsrReady
          ? '语音识别准备中'
          : isRecording
            ? '说完后点击提交回答'
            : '点击麦克风发言';

  if (!autoStartRef.current && !presetVoiceConfig && !resumeSessionId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center p-6">
        <div className="bg-white dark:bg-slate-800 rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm p-8 text-center max-w-md w-full">
          <AlertCircle className="w-12 h-12 text-yellow-500 mx-auto mb-4" />
          <p className="text-slate-700 dark:text-slate-200 text-lg font-semibold mb-2">未检测到语音面试配置</p>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-6">请从面试记录或"语音面试"入口开始</p>
          <button
            onClick={handleCloseModal}
            className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
          >
            返回重新开始
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="pb-10">
      <div className="max-w-7xl mx-auto">
        <InterviewPageHeader
          title="语音模拟面试"
          subtitle="实时语音对话，面试官会根据你的回答持续追问"
          icon={<Mic className="w-6 h-6 text-white" />}
        />

        {error && (
          <div className="mb-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 px-4 py-3 rounded-xl flex items-center gap-2">
            <AlertCircle className="w-4 h-4" />
            <span className="text-sm">{error}</span>
          </div>
        )}

        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <div className="xl:col-span-2 space-y-6">
            <div className="bg-white dark:bg-slate-800 rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm p-6">
              <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => navigate('/interviews')}
                    className="w-9 h-9 rounded-lg bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors flex items-center justify-center"
                    title="返回面试记录"
                  >
                    <ArrowLeft className="w-4 h-4" />
                  </button>
                  <div>
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-white">{templateName || effectiveSkillId}</h2>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-xs px-2 py-0.5 bg-primary-100 dark:bg-primary-900/40 text-primary-600 dark:text-primary-300 rounded-full">
                        {getPhaseLabel(currentPhase)}
                      </span>
                      <span className="text-xs text-slate-500 dark:text-slate-400">
                        {connectionStatus === 'connected'
                          ? isAsrReady ? '语音识别就绪' : '语音识别准备中'
                          : connectionStatus === 'connecting' ? '连接中' : '连接断开'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-200">
                  <Clock className="w-4 h-4" />
                  <span className="font-mono text-sm tabular-nums">{formatTime(currentTime)}</span>
                </div>
              </div>

              <div className="flex flex-col items-center justify-center py-6">
                <motion.div
                  animate={isAiSpeaking ? { scale: [1, 1.05, 1] } : {}}
                  transition={{ repeat: Infinity, duration: 2 }}
                  className={`w-32 h-32 rounded-full border-4 flex items-center justify-center mb-6 transition-colors
                    ${isAiSpeaking
                      ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                      : 'border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900/60'
                    }`}
                >
                  <Bot className={`w-14 h-14 ${isAiSpeaking ? 'text-primary-500' : 'text-slate-400 dark:text-slate-500'}`} />
                </motion.div>

                <div className="w-full max-w-2xl min-h-[130px] rounded-2xl bg-slate-50 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-700 px-6 py-5 text-center flex items-center justify-center">
                  <AnimatePresence mode="wait">
                    {isAiSpeaking || aiText ? (
                      <motion.p
                        data-testid="voice-current-ai-text"
                        key="ai-active"
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0 }}
                        className="text-lg md:text-xl font-medium text-slate-800 dark:text-slate-100 leading-relaxed"
                      >
                        {aiText || '思考中...'}
                      </motion.p>
                    ) : userText ? (
                      <motion.p
                        data-testid="voice-current-user-text"
                        key="user-active"
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0 }}
                        className="text-lg md:text-xl font-medium text-primary-600 dark:text-primary-300 italic leading-relaxed"
                      >
                        {userText}
                      </motion.p>
                    ) : (
                      <motion.p
                        key="idle"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="text-slate-500 dark:text-slate-400"
                      >
                        {recorderHint}
                      </motion.p>
                    )}
                  </AnimatePresence>
                </div>
              </div>
            </div>

            <div className="bg-white dark:bg-slate-800 rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm p-5">
              <div className="flex items-center justify-center gap-6">
                <button
                  onClick={() => {
                    const choice = window.confirm('暂停面试？\n确定 = 短暂停（5分钟）\n取消 = 离开并保存');
                    handlePause(choice ? 'short' : 'long');
                  }}
                  disabled={connectionStatus !== 'connected'}
                  className="px-4 py-2 rounded-xl bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors disabled:opacity-50"
                  title="暂停"
                >
                  暂停
                </button>

                <AudioRecorder
                  isRecording={isRecording}
                  disabled={!isRecording && !canRecord}
                  onRecordingChange={setIsRecording}
                  onAudioData={handleAudioData}
                  onSpeechStart={handleSpeechStart}
                  onSpeechEnd={handleSpeechEnd}
                />

                <button
                  data-testid="voice-submit-answer"
                  onClick={handleSubmitAnswer}
                  disabled={!canSubmit}
                  className={`px-5 py-2.5 rounded-xl font-medium text-sm transition-all ${
                    canSubmit
                      ? 'bg-primary-500 text-white hover:bg-primary-600 shadow-md shadow-primary-500/30'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-400 dark:text-slate-500 cursor-not-allowed'
                  }`}
                  title="提交回答"
                >
                  <span className="inline-flex items-center gap-1.5">
                    <SendHorizonal className="w-4 h-4" />
                    提交回答
                  </span>
                </button>

                <button
                  data-testid="voice-end-interview"
                  onClick={handleEndInterview}
                  disabled={connectionStatus !== 'connected'}
                  className="px-4 py-2 rounded-xl bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900/50 transition-colors disabled:opacity-50"
                  title="结束面试"
                >
                  <span className="inline-flex items-center gap-1">
                    <PhoneOff className="w-4 h-4" />
                    结束
                  </span>
                </button>
              </div>
              <p className="text-center text-xs text-slate-500 dark:text-slate-400 mt-3">
                {footerHint}
              </p>
            </div>
          </div>

          <div className="h-[520px] md:h-[560px] xl:h-[calc(100vh-240px)] xl:max-h-[760px] bg-white dark:bg-slate-800 rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm overflow-hidden">
            <RealtimeSubtitle
              messages={messages}
              userText={userText}
              aiText={aiText}
              isAiSpeaking={isAiSpeaking}
            />
          </div>
        </div>
      </div>

      {aiAudio && (
        <audio
          ref={audioPlayerRef}
          src={`data:audio/wav;base64,${aiAudio}`}
          onEnded={() => {
            finishAiPlayback();
          }}
          onPlay={() => setAiSpeaking(true)}
          autoPlay
          style={{ display: 'none' }}
        />
      )}
    </div>
  );
}
