import { FormEvent, useEffect, useState } from 'react';

type EntityKey = 'events' | 'users' | 'event-likes' | 'matches' | 'chats' | 'chat-messages';
type FieldType = 'text' | 'textarea' | 'number' | 'decimal' | 'date' | 'time' | 'datetime-local' | 'select' | 'multiselect' | 'checkbox';
type FormState = Record<string, string | number | boolean | string[]>;
type RecordData = Record<string, unknown>;
type MetadataKey = 'eventTypes' | 'genders';

interface AuthState {
  username: string;
  password: string;
}

interface ApiErrorShape {
  status: number;
  message: string;
}

interface MetadataState {
  eventTypes: string[];
  genders: string[];
}

interface FieldConfig {
  name: string;
  label: string;
  type: FieldType;
  required?: boolean;
  options?: string[];
  optionsKey?: MetadataKey;
  step?: string;
  placeholder?: string;
}

interface EntityConfig {
  key: EntityKey;
  label: string;
  endpoint: string;
  listColumns: string[];
  fields: FieldConfig[];
}

const STORAGE_KEY = 'join-admin-auth';
const API_BASE = import.meta.env.VITE_ADMIN_API_URL || '/api/admin';

const ENTITY_CONFIGS: EntityConfig[] = [
  {
    key: 'events',
    label: 'Events',
    endpoint: 'events',
    listColumns: ['id', 'title', 'type', 'city', 'eventDate', 'price'],
    fields: [
      { name: 'title', label: 'Title', type: 'text', required: true },
      { name: 'description', label: 'Description', type: 'textarea' },
      { name: 'type', label: 'Type', type: 'select', required: true, optionsKey: 'eventTypes' },
      { name: 'imageUrl', label: 'Image URL', type: 'text' },
      { name: 'price', label: 'Price', type: 'decimal', step: '0.01' },
      { name: 'eventDate', label: 'Event Date', type: 'date', required: true },
      { name: 'eventTime', label: 'Event Time', type: 'time' },
      { name: 'ticketUrl', label: 'Ticket URL', type: 'text' },
      { name: 'city', label: 'City', type: 'text', required: true },
      { name: 'createdAt', label: 'Created At', type: 'datetime-local' },
    ],
  },
  {
    key: 'users',
    label: 'Users',
    endpoint: 'users',
    listColumns: ['id', 'maxId', 'firstName', 'city', 'gender', 'age'],
    fields: [
      { name: 'maxId', label: 'MAX ID', type: 'number', required: true },
      { name: 'email', label: 'Email', type: 'text' },
      { name: 'city', label: 'City', type: 'text' },
      { name: 'firstName', label: 'First Name', type: 'text' },
      { name: 'lastName', label: 'Last Name', type: 'text' },
      { name: 'gender', label: 'Gender', type: 'select', optionsKey: 'genders' },
      { name: 'age', label: 'Age', type: 'number' },
      { name: 'photo', label: 'Photo Path', type: 'text' },
      { name: 'telegramChannel', label: 'Telegram Channel', type: 'text' },
      { name: 'status', label: 'Status', type: 'text' },
      { name: 'bio', label: 'Bio', type: 'textarea' },
      { name: 'preferredAgeMin', label: 'Preferred Age Min', type: 'number' },
      { name: 'preferredAgeMax', label: 'Preferred Age Max', type: 'number' },
      { name: 'preferredGender', label: 'Preferred Gender', type: 'select', optionsKey: 'genders' },
      { name: 'interests', label: 'Interests', type: 'multiselect', optionsKey: 'eventTypes' },
      { name: 'createdAt', label: 'Created At', type: 'datetime-local' },
    ],
  },
  {
    key: 'event-likes',
    label: 'Event Likes',
    endpoint: 'event-likes',
    listColumns: ['id', 'userId', 'eventId', 'createdAt'],
    fields: [
      { name: 'userId', label: 'User ID', type: 'number', required: true },
      { name: 'eventId', label: 'Event ID', type: 'number', required: true },
      { name: 'createdAt', label: 'Created At', type: 'datetime-local' },
    ],
  },
  {
    key: 'matches',
    label: 'Matches',
    endpoint: 'matches',
    listColumns: ['id', 'user1Id', 'user2Id', 'eventId', 'createdAt'],
    fields: [
      { name: 'user1Id', label: 'User 1 ID', type: 'number', required: true },
      { name: 'user2Id', label: 'User 2 ID', type: 'number', required: true },
      { name: 'eventId', label: 'Event ID', type: 'number', required: true },
      { name: 'createdAt', label: 'Created At', type: 'datetime-local' },
    ],
  },
  {
    key: 'chats',
    label: 'Chats',
    endpoint: 'chats',
    listColumns: ['id', 'user1Id', 'user2Id', 'eventId', 'matchId'],
    fields: [
      { name: 'user1Id', label: 'User 1 ID', type: 'number', required: true },
      { name: 'user2Id', label: 'User 2 ID', type: 'number', required: true },
      { name: 'eventId', label: 'Event ID', type: 'number', required: true },
      { name: 'matchId', label: 'Match ID', type: 'number', required: true },
      { name: 'createdAt', label: 'Created At', type: 'datetime-local' },
    ],
  },
  {
    key: 'chat-messages',
    label: 'Chat Messages',
    endpoint: 'chat-messages',
    listColumns: ['id', 'chatId', 'senderId', 'text', 'isRead'],
    fields: [
      { name: 'chatId', label: 'Chat ID', type: 'number', required: true },
      { name: 'senderId', label: 'Sender ID', type: 'number', required: true },
      { name: 'text', label: 'Text', type: 'textarea', required: true },
      { name: 'createdAt', label: 'Created At', type: 'datetime-local' },
      { name: 'isRead', label: 'Is Read', type: 'checkbox' },
    ],
  },
];

export default function App() {
  const [auth, setAuth] = useState<AuthState | null>(loadStoredAuth());
  const [pendingAuth, setPendingAuth] = useState<AuthState>(loadStoredAuth() ?? { username: 'admin', password: 'change_me' });
  const [metadata, setMetadata] = useState<MetadataState>({ eventTypes: [], genders: [] });
  const [activeEntity, setActiveEntity] = useState<EntityKey>('events');
  const [records, setRecords] = useState<RecordData[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [formState, setFormState] = useState<FormState>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const config = getConfig(activeEntity);

  useEffect(() => {
    if (!auth) {
      return;
    }

    void loadMetadata(auth);
  }, [auth]);

  useEffect(() => {
    if (!auth) {
      return;
    }

    void loadRecords(config.key, auth);
  }, [auth, config.key]);

  async function loadMetadata(credentials: AuthState) {
    try {
      const data = await apiRequest<MetadataState>('/metadata', credentials);
      setMetadata(data);
      setError('');
    } catch (requestError) {
      handleAuthError(requestError);
    }
  }

  async function loadRecords(entityKey: EntityKey, credentials: AuthState) {
    const entityConfig = getConfig(entityKey);
    setLoading(true);
    setError('');
    setInfo('');

    try {
      const data = await apiRequest<RecordData[]>(`/${entityConfig.endpoint}`, credentials);
      const sorted = [...data].sort((left, right) => Number(right.id ?? 0) - Number(left.id ?? 0));
      setRecords(sorted);
      if (sorted.length > 0) {
        selectRecord(entityConfig, sorted[0]!);
      } else {
        startCreate(entityConfig);
      }
    } catch (requestError) {
      handleAuthError(requestError);
    } finally {
      setLoading(false);
    }
  }

  function startCreate(entityConfig: EntityConfig) {
    setSelectedId(null);
    setFormState(buildEmptyForm(entityConfig));
    setInfo(`Creating new ${entityConfig.label.slice(0, -1) || entityConfig.label}.`);
  }

  function selectRecord(entityConfig: EntityConfig, record: RecordData) {
    setSelectedId(Number(record.id));
    setFormState(buildFormState(entityConfig, record));
    setInfo('');
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');

    try {
      await apiRequest<MetadataState>('/metadata', pendingAuth);
      persistAuth(pendingAuth);
      setAuth(pendingAuth);
    } catch (requestError) {
      setError(extractApiError(requestError).message);
    }
  }

  function handleLogout() {
    clearStoredAuth();
    setAuth(null);
    setRecords([]);
    setSelectedId(null);
    setFormState({});
    setError('');
    setInfo('');
  }

  function handleFieldChange(field: FieldConfig, rawValue: string | boolean) {
    setFormState((current) => {
      if (field.type === 'checkbox') {
        return { ...current, [field.name]: Boolean(rawValue) };
      }

      if (field.type === 'multiselect') {
        const currentValues = Array.isArray(current[field.name]) ? [...(current[field.name] as string[])] : [];
        const value = String(rawValue);
        const nextValues = currentValues.includes(value)
          ? currentValues.filter((item) => item !== value)
          : [...currentValues, value];
        return { ...current, [field.name]: nextValues };
      }

      return { ...current, [field.name]: String(rawValue) };
    });
  }

  async function handleSave() {
    if (!auth) {
      return;
    }

    setSaving(true);
    setError('');

    try {
      const payload = serializeForm(config, formState);
      const method = selectedId === null ? 'POST' : 'PUT';
      const suffix = selectedId === null ? '' : `/${selectedId}`;
      const savedRecord = await apiRequest<RecordData>(`/${config.endpoint}${suffix}`, auth, {
        method,
        body: payload,
      });

      await loadRecords(config.key, auth);
      selectRecord(config, savedRecord);
      setInfo(selectedId === null ? 'Record created.' : 'Record updated.');
    } catch (requestError) {
      setError(extractApiError(requestError).message);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!auth || selectedId === null) {
      return;
    }

    const confirmed = window.confirm(`Delete ${config.label.slice(0, -1)} #${selectedId}?`);
    if (!confirmed) {
      return;
    }

    setSaving(true);
    setError('');

    try {
      await apiRequest<void>(`/${config.endpoint}/${selectedId}`, auth, { method: 'DELETE' });
      await loadRecords(config.key, auth);
      setInfo('Record deleted.');
    } catch (requestError) {
      setError(extractApiError(requestError).message);
    } finally {
      setSaving(false);
    }
  }

  function handleAuthError(requestError: unknown) {
    const apiError = extractApiError(requestError);
    if (apiError.status === 401 || apiError.status === 403) {
      clearStoredAuth();
      setAuth(null);
      setError('Admin credentials are invalid.');
      return;
    }

    setError(apiError.message);
  }

  if (!auth) {
    return (
      <div className="login-shell">
        <div className="login-card">
          <p className="eyebrow">JOIN Admin</p>
          <h1>Control panel</h1>
          <p className="login-copy">Separate website for CRUD access to events, users, likes, matches, chats, and messages.</p>
          <form className="login-form" onSubmit={handleLogin}>
            <label className="field-stack">
              <span>Username</span>
              <input
                value={pendingAuth.username}
                onChange={(event) => setPendingAuth((current) => ({ ...current, username: event.target.value }))}
                autoComplete="username"
              />
            </label>
            <label className="field-stack">
              <span>Password</span>
              <input
                type="password"
                value={pendingAuth.password}
                onChange={(event) => setPendingAuth((current) => ({ ...current, password: event.target.value }))}
                autoComplete="current-password"
              />
            </label>
            {error && <div className="banner banner-error">{error}</div>}
            <button className="primary-button" type="submit">Open admin</button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <div>
          <p className="eyebrow">JOIN Admin</p>
          <h1>Operations</h1>
          <p className="sidebar-copy">Runs separately from the MAX Mini App and is protected with HTTP Basic auth.</p>
        </div>
        <nav className="entity-nav">
          {ENTITY_CONFIGS.map((entity) => (
            <button
              key={entity.key}
              className={`entity-tab ${entity.key === config.key ? 'entity-tab-active' : ''}`}
              onClick={() => setActiveEntity(entity.key)}
              type="button"
            >
              {entity.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span>{auth.username}</span>
          <button className="ghost-button" onClick={handleLogout} type="button">Log out</button>
        </div>
      </aside>

      <main className="workspace">
        <section className="workspace-panel workspace-list">
          <div className="panel-header">
            <div>
              <p className="section-label">{config.label}</p>
              <h2>{loading ? 'Loading…' : `${records.length} records`}</h2>
            </div>
            <div className="panel-actions">
              <button className="ghost-button" onClick={() => auth && loadRecords(config.key, auth)} type="button">Refresh</button>
              <button className="primary-button" onClick={() => startCreate(config)} type="button">New record</button>
            </div>
          </div>

          {error && <div className="banner banner-error">{error}</div>}
          {info && <div className="banner banner-info">{info}</div>}

          <div className="records-table-wrapper">
            <table className="records-table">
              <thead>
                <tr>
                  {config.listColumns.map((column) => (
                    <th key={column}>{column}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {records.map((record) => (
                  <tr
                    key={String(record.id)}
                    className={Number(record.id) === selectedId ? 'row-active' : ''}
                    onClick={() => selectRecord(config, record)}
                  >
                    {config.listColumns.map((column) => (
                      <td key={`${String(record.id)}-${column}`}>{displayValue(record[column])}</td>
                    ))}
                  </tr>
                ))}
                {records.length === 0 && (
                  <tr>
                    <td colSpan={config.listColumns.length} className="empty-state">No records yet.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="workspace-panel workspace-editor">
          <div className="panel-header">
            <div>
              <p className="section-label">{selectedId === null ? 'Create' : 'Edit'}</p>
              <h2>{selectedId === null ? `New ${config.label.slice(0, -1)}` : `${config.label.slice(0, -1)} #${selectedId}`}</h2>
            </div>
            <div className="panel-actions">
              {selectedId !== null && (
                <button className="danger-button" disabled={saving} onClick={handleDelete} type="button">Delete</button>
              )}
              <button className="primary-button" disabled={saving} onClick={handleSave} type="button">
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>

          <div className="form-grid">
            {config.fields.map((field) => (
              <label
                key={field.name}
                className={`field-stack ${field.type === 'textarea' || field.type === 'multiselect' ? 'field-span-full' : ''}`}
              >
                <span>{field.label}</span>
                {renderField(field, formState[field.name], metadata, handleFieldChange)}
              </label>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function renderField(
  field: FieldConfig,
  value: FormState[string] | undefined,
  metadata: MetadataState,
  onChange: (field: FieldConfig, rawValue: string | boolean) => void,
) {
  if (field.type === 'textarea') {
    return (
      <textarea
        value={String(value ?? '')}
        onChange={(event) => onChange(field, event.target.value)}
        placeholder={field.placeholder}
        rows={5}
      />
    );
  }

  if (field.type === 'select') {
    const options = resolveOptions(field, metadata);
    return (
      <select value={String(value ?? '')} onChange={(event) => onChange(field, event.target.value)}>
        <option value="">Select…</option>
        {options.map((option) => (
          <option key={option} value={option}>{option}</option>
        ))}
      </select>
    );
  }

  if (field.type === 'multiselect') {
    const selectedValues = Array.isArray(value) ? value : [];
    const options = resolveOptions(field, metadata);
    return (
      <div className="multi-select">
        {options.map((option) => (
          <label key={option} className="checkbox-chip">
            <input
              type="checkbox"
              checked={selectedValues.includes(option)}
              onChange={() => onChange(field, option)}
            />
            <span>{option}</span>
          </label>
        ))}
      </div>
    );
  }

  if (field.type === 'checkbox') {
    return (
      <label className="checkbox-inline">
        <input
          type="checkbox"
          checked={Boolean(value)}
          onChange={(event) => onChange(field, event.target.checked)}
        />
        <span>Enabled</span>
      </label>
    );
  }

  const inputType = field.type === 'decimal' || field.type === 'number' ? 'number' : field.type;
  return (
    <input
      type={inputType}
      value={String(value ?? '')}
      step={field.step}
      placeholder={field.placeholder}
      onChange={(event) => onChange(field, event.target.value)}
    />
  );
}

function buildFormState(config: EntityConfig, record: RecordData): FormState {
  const entries = config.fields.map((field) => [field.name, normalizeValueForInput(field, record[field.name])]);
  return Object.fromEntries(entries);
}

function buildEmptyForm(config: EntityConfig): FormState {
  const entries = config.fields.map((field) => [field.name, defaultValueForField(field)]);
  return Object.fromEntries(entries);
}

function normalizeValueForInput(field: FieldConfig, value: unknown): string | number | boolean | string[] {
  if (field.type === 'checkbox') {
    return Boolean(value);
  }

  if (field.type === 'multiselect') {
    return Array.isArray(value) ? value.map(String) : [];
  }

  if (value === null || value === undefined) {
    return '';
  }

  if (field.type === 'datetime-local') {
    return String(value).slice(0, 16);
  }

  return String(value);
}

function defaultValueForField(field: FieldConfig): string | number | boolean | string[] {
  if (field.type === 'checkbox') {
    return false;
  }

  if (field.type === 'multiselect') {
    return [];
  }

  return '';
}

function serializeForm(config: EntityConfig, formState: FormState): Record<string, unknown> {
  const payload = config.fields.reduce<Record<string, unknown>>((result, field) => {
    const rawValue = formState[field.name];

    if (field.type === 'checkbox') {
      result[field.name] = Boolean(rawValue);
      return result;
    }

    if (field.type === 'multiselect') {
      result[field.name] = Array.isArray(rawValue) ? rawValue : [];
      return result;
    }

    const value = String(rawValue ?? '').trim();
    if (value === '') {
      result[field.name] = null;
      return result;
    }

    if (field.type === 'number') {
      result[field.name] = Number.parseInt(value, 10);
      return result;
    }

    if (field.type === 'decimal') {
      result[field.name] = Number.parseFloat(value);
      return result;
    }

    result[field.name] = value;
    return result;
  }, {});

  for (const field of config.fields) {
    if (field.required && (payload[field.name] === null || payload[field.name] === '')) {
      throw new Error(`Field "${field.label}" is required.`);
    }
  }

  return payload;
}

function resolveOptions(field: FieldConfig, metadata: MetadataState): string[] {
  if (field.options) {
    return field.options;
  }

  if (field.optionsKey) {
    return metadata[field.optionsKey];
  }

  return [];
}

function displayValue(value: unknown): string {
  if (Array.isArray(value)) {
    return value.join(', ');
  }

  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No';
  }

  if (value === null || value === undefined || value === '') {
    return '—';
  }

  return String(value);
}

function getConfig(entityKey: EntityKey): EntityConfig {
  return ENTITY_CONFIGS.find((entity) => entity.key === entityKey) ?? ENTITY_CONFIGS[0]!;
}

async function apiRequest<T>(path: string, auth: AuthState, init?: { method?: string; body?: unknown }): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: init?.method ?? 'GET',
    headers: {
      Authorization: `Basic ${window.btoa(`${auth.username}:${auth.password}`)}`,
      'Content-Type': 'application/json',
    },
    body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
  });

  const text = await response.text();
  const data = text ? safeJsonParse(text) : null;

  if (!response.ok) {
    throw {
      status: response.status,
      message: extractApiMessage(data, response.status),
    } satisfies ApiErrorShape;
  }

  return data as T;
}

function extractApiMessage(data: unknown, status: number): string {
  if (data && typeof data === 'object') {
    const errorValue = 'error' in data ? (data as { error?: unknown }).error : undefined;
    if (typeof errorValue === 'string' && errorValue) {
      return errorValue;
    }

    const messageValue = 'message' in data ? (data as { message?: unknown }).message : undefined;
    if (typeof messageValue === 'string' && messageValue) {
      return messageValue;
    }
  }

  return `Request failed with status ${status}`;
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function extractApiError(error: unknown): ApiErrorShape {
  if (typeof error === 'object' && error !== null && 'status' in error && 'message' in error) {
    return {
      status: Number((error as { status: unknown }).status),
      message: String((error as { message: unknown }).message),
    };
  }

  if (error instanceof Error) {
    return { status: 0, message: error.message };
  }

  return { status: 0, message: 'Unexpected request failure.' };
}

function loadStoredAuth(): AuthState | null {
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as AuthState;
    if (!parsed.username || !parsed.password) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function persistAuth(auth: AuthState) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
}

function clearStoredAuth() {
  window.localStorage.removeItem(STORAGE_KEY);
}
