import type { AccessCodeVerification } from '@/domain/types';
import { apiRequest } from './apiClient';

/**
 * `GET /api/verify?code=`
 *
 * Event-day check-in. The code is trimmed and upper-cased before sending so a
 * student typing lower case is not refused for it — the server normalises too,
 * but doing it here means the request that goes out matches what was stored.
 */
export function verifyAccessCode(
  code: string,
  signal?: AbortSignal,
): Promise<AccessCodeVerification> {
  const search = new URLSearchParams({ code: code.trim().toUpperCase() });
  return apiRequest<AccessCodeVerification>(`/verify?${search.toString()}`, signal ? { signal } : {});
}
