// The one way an inline error is rendered anywhere in the app: forms, list pages, and the barcode
// field all showed the same rose panel, only ever differing in the top margin the layout needed.
export default function ErrorText({ children, className = '' }) {
  if (!children) return null
  return <p className={`rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ${className}`}>{children}</p>
}
