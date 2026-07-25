// Five bars of varying width pulsing in a staggered wave, like a barcode passing under a scanner —
// a loading indicator unique to this app rather than a generic spinner. Uses currentColor so it
// always matches whatever text color the surrounding button already has (white on primary/danger,
// slate on secondary), no extra color prop needed.
const BAR_WIDTHS = [2, 3, 2, 4, 2] // px — mimics a barcode's mix of thin/thick lines

export default function BarcodeSpinner({ className = '' }) {
  return (
    <span className={`inline-flex h-4 items-center gap-[2px] ${className}`} role="status" aria-label="Loading">
      {BAR_WIDTHS.map((width, i) => (
        <span
          key={i}
          className="barcode-bar h-full bg-current"
          style={{ width: `${width}px`, animationDelay: `${i * 0.1}s` }}
        />
      ))}
    </span>
  )
}