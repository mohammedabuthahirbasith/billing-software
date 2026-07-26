// Full-page background image for the auth pages, with the form floating on top as a card — one
// unified image-backed page, not a segregated split-screen. The background is a real illustrated
// image (public/auth-background.jpg — resized/compressed from the user-supplied 4K source down to
// ~42KB so it never becomes a load-time cost on top of everything else this app already does to
// feel fast). A soft dark overlay sits between the image and the card so the form stays legible
// regardless of which part of the image lands behind it.
export default function AuthLayout({ children }) {
  return (
    <div
      className="relative flex min-h-screen flex-col items-center justify-center bg-slate-950 bg-cover bg-center px-4 py-12"
      style={{ backgroundImage: "url('/auth-background.jpg')" }}
    >
      <div className="absolute inset-0 bg-navy-950/40" />

      <div className="relative z-10 mb-8 text-xl font-bold tracking-tight text-white">Billing</div>

      <div className="relative z-10 w-full max-w-sm">{children}</div>

      <p className="relative z-10 mt-8 text-xs text-brand-100/70">
        © {new Date().getFullYear()} Billing. All rights reserved.
      </p>
    </div>
  )
}