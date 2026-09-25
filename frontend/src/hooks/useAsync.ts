/**
 * A tiny async-state hook.
 *
 * Deliberately not TanStack Query: this milestone makes exactly three GET calls and
 * one POST, and a cache layer would be a dependency carrying more concepts than the
 * app currently has. The shape below (`status` as a discriminated union rather than
 * loose `isLoading`/`error` booleans) is what matters, because it makes the
 * impossible states — loading *and* error, data *and* loading — unrepresentable.
 *
 * If server state grows past a handful of endpoints, replace this with TanStack
 * Query; every caller uses `status`, so the swap is local.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/services/apiError';

export type AsyncState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: ApiError };

/** Runs `loader` on mount and whenever `deps` change. Aborts in flight on unmount. */
export function useAsync<T>(
  loader: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'idle' });
  const [nonce, setNonce] = useState(0);

  // Keeping the loader in a ref means callers can pass an inline arrow function
  // without it re-triggering the effect on every render.
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });

    loaderRef
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: 'success', data });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setState({
          status: 'error',
          error: cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'Unexpected error.'),
        });
      });

    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  return { ...state, reload };
}
