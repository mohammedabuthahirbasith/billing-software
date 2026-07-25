import { useContext } from 'react'
import { ToastContext } from '../contexts/toastContext'

export function useToast() {
  const showToast = useContext(ToastContext)
  if (!showToast) throw new Error('useToast must be used within a ToastProvider')
  return showToast
}