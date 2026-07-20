const TONES = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  neutral: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  danger: 'bg-rose-50 text-rose-700 ring-rose-600/20',
}

export default function Badge({ tone = 'neutral', children }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${TONES[tone]}`}>
      {children}
    </span>
  )
}
