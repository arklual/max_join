import { useNavigate } from 'react-router';
import { Box, Container, IconButton, Typography } from '@mui/material';
import { ArrowBack } from '@mui/icons-material';
import { PRIVACY_POLICY_EDITION, PrivacyPolicyText } from '../components/PrivacyPolicy';

export default function PrivacyScreen() {
  const navigate = useNavigate();

  function handleBack() {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0;
    if (idx > 0) navigate(-1);
    else navigate('/', { replace: true });
  }

  return (
    <Container maxWidth="sm" sx={{ py: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, ml: -1 }}>
        <IconButton onClick={handleBack} aria-label="Назад">
          <ArrowBack />
        </IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          Политика конфиденциальности
        </Typography>
      </Box>

      <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 3 }}>
        Как JOIN обрабатывает персональные данные. {PRIVACY_POLICY_EDITION}
      </Typography>

      <PrivacyPolicyText />
    </Container>
  );
}
