const currencyFormatter = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })

export function formatCurrency(value) {
  return currencyFormatter.format(Number(value))
}

export function formatDateTime(value) {
  return new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
}

// yyyy-MM-dd, the wire format the report endpoints' `from`/`to` params expect and the value format
// a native <input type="date"> uses.
export function toISODate(date) {
  return date.toISOString().slice(0, 10)
}
