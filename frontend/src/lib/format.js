const currencyFormatter = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })

export function formatCurrency(value) {
  return currencyFormatter.format(Number(value))
}

export function formatDateTime(value) {
  return new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
}
