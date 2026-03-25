import type { Toast as ToastData } from '../hooks/useToast';
import './Toast.css';

interface ToastProps {
  toast: ToastData | null;
  onDismiss: () => void;
}

export function Toast({ toast, onDismiss }: ToastProps) {
  if (!toast) return null;

  return (
    <div className={`toast toast--${toast.type}`} role="alert" onClick={onDismiss}>
      <span className="toast-message">{toast.message}</span>
    </div>
  );
}
