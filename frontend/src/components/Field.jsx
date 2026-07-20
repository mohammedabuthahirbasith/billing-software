export default function Field({ label, error, as = 'input', children, className = '', ...props }) {
  const Tag = as
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <Tag
        className={`block w-full rounded-lg border px-3 py-2 text-sm text-slate-900
          shadow-sm transition-colors focus:border-brand-500 focus:outline-none
          focus:ring-2 focus:ring-brand-500/30
          ${error ? 'border-rose-400' : 'border-slate-300'} ${className}`}
        {...props}
      >
        {children}
      </Tag>
      {error && <span className="mt-1 block text-sm text-rose-600">{error}</span>}
    </label>
  )
}
