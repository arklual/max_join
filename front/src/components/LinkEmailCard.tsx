import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Collapse,
  TextField,
  Typography,
} from '@mui/material';
import { CheckCircleOutlined, DevicesOutlined } from '@mui/icons-material';
import apiClient from '../api/client';
import { messengerName } from '../api/platform';

type Props = {
  /** Email of an account with password sign-in set up; null shows the setup form. */
  email: string | null | undefined;
  /** Pre-fills the form (e.g. an email already present in the profile). */
  defaultEmail?: string;
  onLinked: () => void;
};

function maskEmail(email: string): string {
  const [local, domain] = email.split('@');
  if (!domain) return email;
  return `${local[0]}***@${domain}`;
}

const cardSx = {
  borderRadius: 3,
  p: 2,
  borderColor: 'outlineVariant.main',
  animation: 'fadeInUp 0.4s ease-out both',
  animationDelay: '0.22s',
} as const;

function CardHeader({ linked, subtitle }: { linked: boolean; subtitle: string }) {
  const Icon = linked ? CheckCircleOutlined : DevicesOutlined;
  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
      <Box
        sx={{
          width: 40,
          height: 40,
          flexShrink: 0,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: linked ? 'primaryContainer.main' : 'surfaceContainerHigh.main',
        }}
      >
        <Icon sx={{ fontSize: 22, color: linked ? 'onPrimaryContainer.main' : 'primary.main' }} />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography sx={{ fontWeight: 600, fontSize: '0.95rem', color: 'onSurface.main' }}>
          Вход с других устройств
        </Typography>
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mt: 0.25, wordBreak: 'break-word' }}>
          {subtitle}
        </Typography>
      </Box>
    </Box>
  );
}

export function LinkEmailCard({ email, defaultEmail = '', onLinked }: Props) {
  const [open, setOpen] = useState(false);
  const [emailInput, setEmailInput] = useState(defaultEmail);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (email) {
    return (
      <Card variant="outlined" sx={cardSx}>
        <CardHeader linked subtitle={`Email привязан: ${maskEmail(email)}`} />
      </Card>
    );
  }

  const submit = async () => {
    setError(null);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailInput.trim())) {
      setError('Введите корректный email');
      return;
    }
    if (password !== confirm) {
      setError('Пароли не совпадают');
      return;
    }
    if (password.length < 8) {
      setError('Пароль должен быть не короче 8 символов');
      return;
    }
    setSubmitting(true);
    try {
      // Paths are relative to apiClient's baseURL (`/api` in production) — an
      // extra `/api` prefix here would resolve to `/api/api/...` and 404.
      await apiClient.post('/auth/link-email', { email: emailInput.trim(), password });
      onLinked();
    } catch (e: unknown) {
      const err = e as { response?: { data?: { code?: string } } };
      const code = err?.response?.data?.code;
      const msg =
        code === 'EMAIL_TAKEN'
          ? 'Этот email уже используется. Возьмите другой.'
          : code === 'ALREADY_LINKED'
          ? 'Email уже привязан. Перезагрузите страницу.'
          : code === 'EMAIL_INVALID'
          ? 'Некорректный email'
          : 'Не удалось привязать email';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const clearError = () => setError(null);

  return (
    <Card variant="outlined" sx={cardSx}>
      <CardHeader
        linked={false}
        subtitle={`Задайте email и пароль, чтобы входить в JOIN из приложения для Android и других мессенджеров${messengerName() ? `, а не только из ${messengerName()}` : ''}.`}
      />

      <Collapse in={!open} unmountOnExit>
        <Button
          variant="tonal"
          fullWidth
          onClick={() => setOpen(true)}
          sx={{ mt: 2, borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
        >
          Настроить вход по email
        </Button>
      </Collapse>

      <Collapse in={open} unmountOnExit>
        <Box
          component="form"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}
        >
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Email"
            type="email"
            value={emailInput}
            onChange={(e) => {
              setEmailInput(e.target.value);
              clearError();
            }}
            autoComplete="email"
            fullWidth
            size="small"
          />
          <TextField
            label="Пароль"
            type="password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              clearError();
            }}
            autoComplete="new-password"
            helperText="Не короче 8 символов"
            fullWidth
            size="small"
          />
          <TextField
            label="Повторите пароль"
            type="password"
            value={confirm}
            onChange={(e) => {
              setConfirm(e.target.value);
              clearError();
            }}
            autoComplete="new-password"
            fullWidth
            size="small"
          />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              variant="text"
              onClick={() => {
                setOpen(false);
                clearError();
              }}
              disabled={submitting}
              sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
            >
              Отмена
            </Button>
            <Button
              type="submit"
              variant="filled"
              disabled={submitting}
              fullWidth
              sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
            >
              {submitting ? 'Привязываем...' : 'Привязать'}
            </Button>
          </Box>
        </Box>
      </Collapse>
    </Card>
  );
}
