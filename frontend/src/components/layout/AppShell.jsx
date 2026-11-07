import { LogOut } from 'lucide-react'
import { Outlet } from 'react-router-dom'
import RightNavigation from './RightNavigation.jsx'

function AppShell({ onSignOut }) {
  return (
    <div className="app-shell">
      <aside className="right-rail">
        <div className="rail-brand">
          <div className="brand-mark" aria-hidden="true">R</div>
          <div>
            <span className="brand-name">RelayFlow</span>
            <span className="brand-caption">Workflow control plane</span>
          </div>
        </div>
        <RightNavigation />
        <button className="sign-out-button" onClick={onSignOut} type="button">
          <LogOut aria-hidden="true" size={17} />
          <span>Change API token</span>
        </button>
        <div className="rail-footer">Local workspace</div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  )
}

export default AppShell
