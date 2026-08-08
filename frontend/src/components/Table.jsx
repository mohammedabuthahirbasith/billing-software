import { createContext, useContext } from 'react'

// Every table in the app shares one visual language: a light-bordered header row of small uppercase
// labels, hairline-separated body rows, and right-aligned tabular numerics. The only real variation
// is density — page-level list tables breathe (py-3), tables nested inside a card's detail panel are
// compact and tinted (py-2 + bg-slate-50 header) — so density is the single knob, passed down via
// context so callers set it once on <Table> instead of on every cell.
const DenseContext = createContext(false)

export function Table({ dense = false, className = '', children }) {
  return (
    <DenseContext.Provider value={dense}>
      <table className={`w-full text-sm ${className}`}>{children}</table>
    </DenseContext.Provider>
  )
}

export function TableHead({ children }) {
  const dense = useContext(DenseContext)
  return (
    <thead>
      <tr className={`border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 ${dense ? 'bg-slate-50' : ''}`}>
        {children}
      </tr>
    </thead>
  )
}

function cellPadding(dense) {
  return dense ? 'px-4 py-2' : 'px-4 py-3'
}

export function Th({ align = 'left', className = '', children }) {
  const dense = useContext(DenseContext)
  return (
    <th className={`${cellPadding(dense)} ${align === 'right' ? 'text-right' : ''} ${className}`}>{children}</th>
  )
}

export function Td({ align = 'left', className = '', children }) {
  const dense = useContext(DenseContext)
  return (
    <td className={`${cellPadding(dense)} ${align === 'right' ? 'text-right' : ''} ${className}`}>{children}</td>
  )
}

// A clickable row gets both the pointer cursor and the hover tint; a hoverable-but-static row (one
// with its own "View" link) opts into the tint alone.
export function Tr({ onClick, hover = false, children }) {
  const interactive = Boolean(onClick)
  return (
    <tr
      onClick={onClick}
      className={`border-b border-slate-100 last:border-0 ${interactive ? 'cursor-pointer' : ''} ${interactive || hover ? 'hover:bg-slate-50' : ''}`}
    >
      {children}
    </tr>
  )
}

export function EmptyRow({ colSpan, children }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-4 py-8 text-center text-slate-500">{children}</td>
    </tr>
  )
}
