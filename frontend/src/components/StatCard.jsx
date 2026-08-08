import Card from './Card'

// A single headline metric: small muted label above a large tabular-numeric value, so figures line
// up digit-for-digit across a row of cards. `tone="danger"` is for counts that are bad news (voids).
export default function StatCard({ label, value, tone = 'default' }) {
  return (
    <Card>
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold tabular-nums ${tone === 'danger' ? 'text-rose-600' : 'text-slate-900'}`}>
        {value}
      </p>
    </Card>
  )
}
