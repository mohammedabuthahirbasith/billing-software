import { useCallback, useEffect, useState } from 'react'
import { ConfirmContext } from '../contexts/confirmContext'
import Button from './Button'

// Imperative, promise-based replacement for window.confirm() — const ok = await confirm({...}) —
// styled to match the rest of the app instead of the browser's native, unstyleable dialog.
export function ConfirmProvider({ children }) {
  const [request, setRequest] = useState(null)

  const confirm = useCallback((options) => {
    return new Promise((resolve) => {
      setRequest({
        title: options.title ?? 'Are you sure?',
        message: options.message ?? '',
        confirmLabel: options.confirmLabel ?? 'Confirm',
        cancelLabel: options.cancelLabel ?? 'Cancel',
        danger: options.danger ?? false,
        resolve,
      })
    })
  }, [])

  function respond(result) {
    request.resolve(result)
    setRequest(null)
  }

  useEffect(() => {
    if (!request) return
    function onKeyDown(e) {
      if (e.key === 'Escape') respond(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [request])

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {request && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 px-4"
          onClick={() => respond(false)}
        >
          <div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-dialog-title"
            className="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="confirm-dialog-title" className="text-lg font-semibold text-slate-900">
              {request.title}
            </h2>
            {request.message && <p className="mt-2 text-sm text-slate-600">{request.message}</p>}
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="secondary" onClick={() => respond(false)}>{request.cancelLabel}</Button>
              <Button variant={request.danger ? 'danger' : 'primary'} onClick={() => respond(true)}>
                {request.confirmLabel}
              </Button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  )
}