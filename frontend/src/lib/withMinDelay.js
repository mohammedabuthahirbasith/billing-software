// Guarantees a promise takes at least `minMs` to settle. Without this, a loading spinner tied
// directly to a fetch's duration can flash for less than one browser paint frame on a fast local
// backend — not visually distinguishable from never appearing at all. A small artificial floor
// makes the "this is working" signal reliably perceivable regardless of how fast the real request is.
export function withMinDelay(promise, minMs = 350) {
  return Promise.allSettled([promise, new Promise((resolve) => setTimeout(resolve, minMs))])
    .then(([result]) => {
      if (result.status === 'fulfilled') return result.value
      throw result.reason
    })
}