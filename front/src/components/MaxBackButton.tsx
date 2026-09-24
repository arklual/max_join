import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { showMaxBackButton } from '../api/maxBridge';

/** Screens where "back" means leaving the mini app, so MAX shows its own close button. */
const ROOT_PATHS = new Set(['/', '/afisha', '/likes', '/chats', '/groups', '/profile', '/register', '/login']);

/** Where to go when a screen was opened directly by a deep link and has no history. */
function parentOf(pathname: string): string {
  if (pathname.startsWith('/chats/')) return '/chats';
  if (pathname.startsWith('/group-chats/') || pathname.startsWith('/groups/')) return '/groups';
  if (pathname.startsWith('/friend-groups')) return '/groups';
  if (pathname.startsWith('/profile/') || pathname === '/search-preferences' || pathname === '/support') {
    return '/profile';
  }
  return '/afisha';
}

/** Wires the MAX system back button to in-app navigation on nested screens. */
export default function MaxBackButton() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    if (ROOT_PATHS.has(location.pathname)) return;
    return showMaxBackButton(() => {
      const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0;
      if (idx > 0) navigate(-1);
      else navigate(parentOf(location.pathname), { replace: true });
    });
  }, [location.pathname, navigate]);

  return null;
}
