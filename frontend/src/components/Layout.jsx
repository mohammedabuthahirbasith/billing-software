import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearRole, clearToken, getRole } from '../api'

const BASE_NAV_LINKS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/products', label: 'Products' },
  { to: '/invoices', label: 'Invoices' },
]

function navLinkClass({ isActive }) {
  return `flex items-center rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-600 text-white' : 'text-slate-300 hover:bg-navy-800 hover:text-white'
  }`
}

function NavLinks({ links, onNavigate }) {
  return (
    <nav className="flex-1 space-y-1 px-3">
      {links.map((link) => (
        <NavLink key={link.to} to={link.to} end={link.end} className={navLinkClass} onClick={onNavigate}>
          {link.label}
        </NavLink>
      ))}
    </nav>
  )
}

export default function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()
  const isOwner = getRole() === 'OWNER'
  const navLinks = isOwner
    ? [...BASE_NAV_LINKS, { to: '/reports', label: 'Reports' }, { to: '/staff/new', label: 'Add Staff' }]
    : BASE_NAV_LINKS

  function handleLogout() {
    clearToken()
    clearRole()
    navigate('/login')
  }

  useEffect(() => {
    if (!menuOpen) return
    function onKeyDown(e) {
      if (e.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [menuOpen])

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Desktop sidebar — persistent, fixed width, dark background so the shell reads as a real
          admin product rather than just a wider version of a marketing-site top bar. */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col bg-navy-900 lg:flex">
        <div className="flex h-16 items-center px-6">
          <span className="text-lg font-bold tracking-tight text-white">Billing</span>
        </div>
        <NavLinks links={navLinks} />
        <div className="px-3 pb-6">
          <button
            onClick={handleLogout}
            className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-300 hover:bg-navy-800 hover:text-white"
          >
            Log out
          </button>
        </div>
      </aside>

      {/* Mobile top bar */}
      <div className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-4 lg:hidden">
        <span className="text-lg font-bold tracking-tight text-brand-700">Billing</span>
        <button
          onClick={() => setMenuOpen(true)}
          aria-label="Open menu"
          className="rounded-md p-2 text-slate-600 hover:bg-slate-100"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
      </div>

      {/* Mobile drawer — slides in from the left, closes on link click, backdrop click, or Escape
          (same interaction pattern as ConfirmDialog). */}
      <div
        className={`fixed inset-0 z-40 lg:hidden ${menuOpen ? '' : 'pointer-events-none'}`}
        aria-hidden={!menuOpen}
      >
        <div
          className={`fixed inset-0 bg-navy-950/60 transition-opacity ${menuOpen ? 'opacity-100' : 'opacity-0'}`}
          onClick={() => setMenuOpen(false)}
        />
        <div
          className={`fixed inset-y-0 left-0 flex w-64 flex-col bg-navy-900 shadow-xl transition-transform duration-200 ${
            menuOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <div className="flex h-16 items-center justify-between px-6">
            <span className="text-lg font-bold tracking-tight text-white">Billing</span>
            <button
              onClick={() => setMenuOpen(false)}
              aria-label="Close menu"
              className="rounded-md p-1 text-slate-300 hover:bg-navy-800 hover:text-white"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>
          <NavLinks links={navLinks} onNavigate={() => setMenuOpen(false)} />
          <div className="px-3 pb-6">
            <button
              onClick={handleLogout}
              className="flex w-full items-center rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-300 hover:bg-navy-800 hover:text-white"
            >
              Log out
            </button>
          </div>
        </div>
      </div>

      {/* Main content — offset by the sidebar on desktop, capped at max-w-screen-2xl so it reads
          as full-width on any normal display without stretching absurdly on an ultra-wide monitor. */}
      <div className="lg:pl-64">
        <main className="mx-auto max-w-screen-2xl px-4 py-8 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}