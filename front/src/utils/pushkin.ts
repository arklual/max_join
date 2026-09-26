import { useSyncExternalStore } from 'react';
import apiClient from '../api/client';
import type { UserProfile } from '../types';

/** The Pushkin card is issued to ages 14–22 — everything about it is hidden for other users. */
export const PUSHKIN_MIN_AGE = 14;
export const PUSHKIN_MAX_AGE = 22;

export function isPushkinEligible(age: number | null | undefined): boolean {
  return age != null && age >= PUSHKIN_MIN_AGE && age <= PUSHKIN_MAX_AGE;
}

// undefined — not loaded yet, null — no profile / unknown age.
let knownAge: number | null | undefined;
let inflight: Promise<void> | null = null;
const listeners = new Set<() => void>();

/** Call whenever the own profile is loaded or saved, so every screen sees the current age. */
export function rememberProfileAge(age: number | null | undefined): void {
  knownAge = age ?? null;
  listeners.forEach((listener) => listener());
}

function loadAge(): Promise<void> {
  if (!inflight) {
    inflight = apiClient
      .get<UserProfile>('/users/me/profile')
      .then((res) => rememberProfileAge(res.data.age))
      .catch(() => rememberProfileAge(null))
      .finally(() => {
        inflight = null;
      });
  }
  return inflight;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  if (knownAge === undefined) loadAge();
  return () => {
    listeners.delete(listener);
  };
}

/** Whether the current user is of Pushkin card age; null while the profile is loading. */
export function usePushkinEligible(): boolean | null {
  const age = useSyncExternalStore(subscribe, () => knownAge);
  return age === undefined ? null : isPushkinEligible(age);
}
