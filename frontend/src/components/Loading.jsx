export default function Loading({ label = 'Loading…', className = '' }) {
  return <p className={`text-slate-500 ${className}`}>{label}</p>
}
