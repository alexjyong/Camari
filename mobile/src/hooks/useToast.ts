import { useState, useCallback, useRef } from 'react';

export type ToastType = 'error' | 'info' | 'success';

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

interface UseToastReturn {
  toast: Toast | null;
  showToast: (message: string, type?: ToastType) => void;
  dismissToast: () => void;
}

const TOAST_DURATION_MS = 3500;

export function useToast(): UseToastReturn {
  const [toast, setToast] = useState<Toast | null>(null);
  const timerRef = useRef<number | null>(null);

  const dismissToast = useCallback(() => {
    setToast(null);
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const showToast = useCallback((message: string, type: ToastType = 'info') => {
    if (timerRef.current) clearTimeout(timerRef.current);

    setToast({ id: crypto.randomUUID(), message, type });

    timerRef.current = window.setTimeout(() => {
      setToast(null);
      timerRef.current = null;
    }, TOAST_DURATION_MS);
  }, []);

  return { toast, showToast, dismissToast };
}
