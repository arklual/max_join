import axios from 'axios';
import { API_BASE, getAuthHeaders, getAuthToken, isMessengerApp, setAuthToken } from './platform';

const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  for (const [name, value] of Object.entries(getAuthHeaders())) {
    config.headers[name] = value;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Outside MAX an expired / revoked JWT leaves the request anonymous: the
    // backend answers 401, or 403 for "who am I". Drop the token and go to sign-in.
    const status = error?.response?.status;
    const url: string = error?.config?.url ?? '';
    const sessionGone = status === 401 || (status === 403 && url.startsWith('/users/me'));
    if (sessionGone && !isMessengerApp() && getAuthToken() && !url.startsWith('/auth/')) {
      setAuthToken(null);
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login');
      }
    }
    return Promise.reject(error);
  },
);

export default apiClient;
