import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearRole, clearToken, getRole } from '../api'

const BASE_NAV_LINKS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/products', label: 'Products' },
  { to: '/invoices', label: 'Invoices' },
]

function navLinkClass({ isActive }) {
  return `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
  }`
}

export default function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()
  const isOwner = getRole() === 'OWNER'
  const navLinks = isOwner ? [...BASE_NAV_LINKS, { to: '/staff/new', label: 'Add Staff' }] : BASE_NAV_LINKS

  function handleLogout() {
    clearToken()
    clearRole()
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-8">
            <span className="text-lg font-bold tracking-tight text-brand-700">Billing</span>
            <nav className="hidden gap-1 sm:flex">
              {navLinks.map((link) => (
                <NavLink key={link.to} to={link.to} end={link.end} className={navLinkClass}>
                  {link.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="hidden sm:block">
            <button
              onClick={handleLogout}
              className="rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            >
              Log out
            </button>
          </div>

          <button
            onClick={() => setMenuOpen((v) => !v)}
            aria-label="Toggle menu"
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100 sm:hidden"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              {menuOpen ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 6h16M4 12h16M4 18h16" />}
            </svg>
          </button>
        </div>

        {menuOpen && (
          <nav className="flex flex-col gap-1 border-t border-slate-200 px-4 py-2 sm:hidden">
            {navLinks.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end} className={navLinkClass} onClick={() => setMenuOpen(false)}>
                {link.label}
              </NavLink>
            ))}
            <button
              onClick={handleLogout}
              className="rounded-md px-3 py-2 text-left text-sm font-medium text-slate-600 hover:bg-slate-100"
            >
              Log out
            </button>
          </nav>
        )}
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
