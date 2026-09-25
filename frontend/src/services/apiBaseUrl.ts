/**
 * Where the backend lives. One decision, resolved once, shared by every client.
 *
 * Both HTTP clients import this: the public one in `apiClient.ts` and the admin one
 * in `admin/adminApi.ts`. That is the point of the module. When each client picked
 * its own base, the public client read `VITE_API_BASE_URL` and the admin client had
 * `/api/admin` written into it literally - so the deployed panel sent its requests to
 * the static host that served the page instead of to the API, and every admin call
 * 404'd in production while working perfectly through the dev proxy.
 *
 * In development this stays `/api`: Vite proxies that prefix to Spring Boot, the
 * browser sees a same-origin request, and CORS never enters the dev loop. In
 * production `VITE_API_BASE_URL` names the deployed service, including its `/api`
 * prefix - for example `https://<service-host>/api`.
 *
 * The host is never written into source. Vite inlines this at BUILD time, not at
 * runtime, so changing it on the hosting platform requires a redeploy to take effect.
 */

/** No trailing slash, so callers can always concatenate a rooted path. */
function withoutTrailingSlash(base: string): string {
  return base.endsWith('/') ? base.replace(/\/+$/, '') : base;
}

/**
 * The API root, e.g. `/api` in development or `https://<service-host>/api` in
 * production. Never ends in a slash; callers append paths that begin with one.
 */
export const API_BASE_URL: string = withoutTrailingSlash(
  import.meta.env['VITE_API_BASE_URL'] ?? '/api',
);
