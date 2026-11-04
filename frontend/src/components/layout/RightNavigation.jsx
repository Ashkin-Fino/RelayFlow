import { Activity, Blocks, GitBranch, LayoutDashboard } from 'lucide-react'
import { NavLink, useLocation } from 'react-router-dom'

const navigationItems = [
  { label: 'Overview', path: '/', icon: LayoutDashboard, end: true },
  { label: 'Workflows', path: '/workflows', icon: GitBranch },
  { label: 'Runs', path: '/runs', icon: Activity },
  { label: 'Workflow Builder', path: '/workflows/new', icon: Blocks, end: true },
]

function RightNavigation() {
  const location = useLocation()

  return (
    <nav aria-label="Primary navigation" className="primary-navigation">
      <span className="navigation-label">Workspace</span>
      {navigationItems.map(({ label, path, icon: Icon, end }) => (
        <NavLink
          className={({ isActive }) => {
            const isBuilderRoute = path === '/workflows' && location.pathname === '/workflows/new'
            return `navigation-link${isActive && !isBuilderRoute ? ' active' : ''}`
          }}
          end={end}
          key={path}
          to={path}
        >
          <Icon aria-hidden="true" size={19} strokeWidth={1.8} />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export default RightNavigation
