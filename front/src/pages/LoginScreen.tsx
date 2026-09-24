import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { Alert, Box, Button, Container, Divider, Paper, TextField, Typography } from '@mui/material';
import MaxLoginPanel from '../components/MaxLoginPanel';
import apiClient from '../api/client';
import { setAuthToken } from '../api/platform';
import Logo from '../components/Logo';
import { takePendingInvite } from '../utils/inviteLinks';

interface AuthResponse {
  token: string;
}

function messageForError(err: unknown): string {
  const data = (err as { response?: { status?: number; data?: { code?: string; message?: string } } })?.response;
  if (!data) return 'Не удалось подключиться к серверу. Проверьте интернет.';
  if (data.data?.code === 'BAD_CREDENTIALS' || data.status === 401) return 'Неверный email или пароль';
  return data.data?.message || 'Не удалось войти. Попробуйте ещё раз.';
}

/** Email + password sign-in, used outside MAX (the Android app, a plain browser). */
export default function LoginScreen() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (!email.trim() || !password) {
      setError('Введите email и пароль');
      return;
    }
    setSubmitting(true);
    try {
      const res = await apiClient.post<AuthResponse>('/auth/login', { email: email.trim(), password });
      setAuthToken(res.data.token);
      const invite = takePendingInvite();
      navigate(invite ? `/friend-groups?invite=${invite}` : '/afisha', { replace: true });
    } catch (err) {
      setError(messageForError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box sx={{ minHeight: '100dvh', display: 'flex', alignItems: 'center', bgcolor: 'surfaceContainer.main' }}>
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <Paper
          elevation={0}
          component="form"
          noValidate
          onSubmit={handleSubmit}
          sx={{
            p: 3,
            borderRadius: 4,
            bgcolor: 'surface.main',
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            animation: 'scaleIn 0.4s ease-out',
          }}
        >
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, mb: 1 }}>
            <Logo size={72} />
            <Typography variant="h5" sx={{ fontWeight: 'bold', color: 'onSurface.main', textAlign: 'center' }}>
              Вход в JOIN
            </Typography>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center' }}>
              Мероприятия и компания, чтобы на них сходить
            </Typography>
          </Box>

          <MaxLoginPanel />

          <Divider sx={{ my: 0.5, color: 'onSurfaceVariant.main', fontSize: '0.8rem' }}>или по email</Divider>

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
            inputProps={{ inputMode: 'email', autoCapitalize: 'none' }}
            fullWidth
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
          />

          <Button
            type="submit"
            variant="tonal"
            disabled={submitting}
            fullWidth
            sx={{ borderRadius: 3, py: 1.25, textTransform: 'none', fontWeight: 600, fontSize: '1rem' }}
          >
            {submitting ? 'Входим...' : 'Войти'}
          </Button>

          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5, flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
              Нет аккаунта?
            </Typography>
            <Button
              variant="text"
              onClick={() => navigate('/register')}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              Зарегистрироваться
            </Button>
          </Box>
        </Paper>
      </Container>
    </Box>
  );
}
