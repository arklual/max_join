import apiClient from './client';
import type { MatchSuggestion } from '../types';

/** "Пойдём вместе?" — the chat opens when the companion accepts (accepts at once if they already invited). */
export async function inviteMatch(matchId: number): Promise<MatchSuggestion> {
  return (await apiClient.post<MatchSuggestion>(`/matches/${matchId}/request`)).data;
}

export async function acceptMatch(matchId: number): Promise<MatchSuggestion> {
  return (await apiClient.post<MatchSuggestion>(`/matches/${matchId}/accept`)).data;
}

export async function declineMatch(matchId: number): Promise<MatchSuggestion> {
  return (await apiClient.post<MatchSuggestion>(`/matches/${matchId}/decline`)).data;
}

export function errorMessage(err: unknown, fallback: string): string {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}
