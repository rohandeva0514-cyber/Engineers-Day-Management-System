import { Outlet, ScrollRestoration } from 'react-router-dom';
import { PracticalShell } from './PracticalShell';

export function RootLayout() {
  return (
    <>
      <PracticalShell>
        <Outlet />
      </PracticalShell>
      <ScrollRestoration />
    </>
  );
}
