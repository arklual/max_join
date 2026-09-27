import apiClient from './client';

export interface AppConfig {
  maxBotUsername: string;
  telegramBotUsername: string;
  /** Cities JOIN serves (events are loaded only for them). */
  cities: string[];
}

let cached: AppConfig | null = null;
let inflight: Promise<AppConfig> | null = null;

export async function loadAppConfig(): Promise<AppConfig> {
  if (cached) return cached;
  if (inflight) return inflight;
  inflight = apiClient
    .get<AppConfig>('/config')
    .then((res) => {
      cached = {
        maxBotUsername: res.data.maxBotUsername ?? '',
        telegramBotUsername: res.data.telegramBotUsername ?? '',
        cities: res.data.cities ?? [],
      };
      return cached;
    })
    .catch(() => {
      cached = { maxBotUsername: '', telegramBotUsername: '', cities: [] };
      return cached;
    })
    .finally(() => {
      inflight = null;
    });
  return inflight;
}

export function getCachedAppConfig(): AppConfig | null {
  return cached;
}
