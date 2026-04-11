import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Activity, MapPin, AppWindow, Settings, BarChart2, LogOut, ShieldCheck } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

const navLinks = [
  { to: '/dashboard', label: 'dashboard', icon: LayoutDashboard },
  { to: '/activity', label: 'activity', icon: Activity },
  { to: '/location', label: 'location', icon: MapPin },
  { to: '/apps', label: 'apps', icon: AppWindow },
  { to: '/history', label: 'history', icon: BarChart2 },
  { to: '/settings', label: 'settings', icon: Settings },
]

export default function Layout() {
  const { signOut } = useAuth()
  const navigate = useNavigate()

  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: '#F8F7FF' }}>
      {/* Sidebar */}
      <nav style={{
        width: 220,
        background: '#3D2FA8',
        color: 'white',
        display: 'flex',
        flexDirection: 'column',
        padding: '24px 0',
        position: 'fixed',
        top: 0,
        left: 0,
        height: '100vh',
        zIndex: 100,
      }}>
        {/* Logo */}
        <div style={{ padding: '0 20px 28px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #3D2FA8, #6C5CE7)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <ShieldCheck size={20} color="white" />
            </div>
            <div>
              <div style={{ fontSize: 20, fontWeight: 500, letterSpacing: '-0.3px' }}>wew</div>
              <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.6)' }}>safe by design</div>
            </div>
          </div>
        </div>

        {/* Nav links */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, padding: '0 8px' }}>
          {navLinks.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              style={({ isActive }) => ({
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 12px',
                borderRadius: 8,
                color: isActive ? 'white' : 'rgba(255,255,255,0.65)',
                background: isActive ? 'rgba(255,255,255,0.15)' : 'transparent',
                textDecoration: 'none',
                fontSize: 14,
                transition: 'all 0.15s',
              })}
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </div>

        {/* Sign out */}
        <div style={{ padding: '0 8px' }}>
          <button
            onClick={handleSignOut}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '10px 12px',
              borderRadius: 8,
              color: 'rgba(255,255,255,0.65)',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              fontSize: 14,
              width: '100%',
            }}
          >
            <LogOut size={18} />
            sign out
          </button>
        </div>
      </nav>

      {/* Main content */}
      <main style={{ flex: 1, marginLeft: 220, padding: '32px 28px', maxWidth: 'calc(100vw - 220px)' }}>
        <Outlet />
      </main>
    </div>
  )
}
