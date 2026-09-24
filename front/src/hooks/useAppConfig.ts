import { useEffect, useState } from 'react';
import { loadAppConfig, getCachedAppConfig, type AppConfig } from '../api/appConfig';

export function useAppConfig(): AppConfig | null {
  const [config, setConfig] = useState<AppConfig | null>(() => getCachedAppConfig());

  useEffect(() => {
    if (config) return;
    let cancelled = false;
    loadAppConfig().then((c) => {
      if (!cancelled) setConfig(c);
    });
    return () => {
      cancelled = true;
    };
  }, [config]);

  return config;
}
