import { useState } from 'react';
import {
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Alert,
  Box,
  Button,
  TextField,
  Typography,
} from '@mui/material';
import { ExpandMore, PhoneIphoneOutlined } from '@mui/icons-material';
import apiClient from '../api/client';
import { messengerName } from '../api/platform';

interface Props {
  /** Called after the messenger account was attached to an existing JOIN account. */
  onLinked: () => void;
}

function messageForCode(code: string | undefined): string {
  switch (code) {
    case 'BAD_CREDENTIALS':
      return 'Неверный email или пароль';
    case 'MAX_MISMATCH':
    case 'TELEGRAM_MISMATCH':
      return `К этому аккаунту уже привязан другой аккаунт ${messengerName() ?? 'мессенджера'}`;
    case 'MAX_TAKEN':
    case 'TELEGRAM_TAKEN':
      return `Этот аккаунт ${messengerName() ?? 'мессенджера'} уже привязан к другому аккаунту JOIN`;
    default:
      return 'Не удалось войти. Попробуйте ещё раз.';
  }
}

/**
 * Shown on the registration screen: a user who already created a JOIN account
 * in the Android app can sign in with those credentials, which attaches this
 * MAX to that account instead of creating a duplicate one.
 */
export default function LinkMaxCard({ onLinked }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit() {
    setError('');
    if (!email.trim() || !password) {
      setError('Введите email и пароль');
      return;
    }
    setSubmitting(true);
    try {
      // Relative to apiClient's baseURL (`/api` in production).
      await apiClient.post('/auth/link-messenger', {
        email: email.trim(),
        password,
      });
      onLinked();
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      setError(messageForCode(code));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Accordion
      variant="outlined"
      disableGutters
      sx={{ mt: 2, borderRadius: '12px !important', '&:before': { display: 'none' } }}
    >
      <AccordionSummary expandIcon={<ExpandMore />}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <PhoneIphoneOutlined sx={{ color: 'primary.main' }} />
          <Typography sx={{ fontWeight: 600, fontSize: '0.95rem', color: 'primary.main' }}>
            Уже есть аккаунт JOIN в приложении?
          </Typography>
        </Box>
      </AccordionSummary>
      <AccordionDetails>
        <Box
          component="form"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            handleSubmit();
          }}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
        >
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
            Войдите с email и паролем — мы привяжем этот аккаунт {messengerName()} к тому же аккаунту,
            и вы сможете пользоваться JOIN и здесь, и в приложении одновременно.
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setError('');
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
              setError('');
            }}
            autoComplete="current-password"
            fullWidth
            size="small"
          />
          <Button
            type="submit"
            variant="filled"
            disabled={submitting}
            fullWidth
            sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
          >
            {submitting ? 'Входим...' : `Войти и привязать ${messengerName() ?? 'аккаунт'}`}
          </Button>
        </Box>
      </AccordionDetails>
    </Accordion>
  );
}
