import { Component, type ErrorInfo, type ReactNode } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

interface Props {
  children: ReactNode;
}

interface State {
  failed: boolean;
}

/** Keeps a rendering crash on one screen from taking the whole mini app down. */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Screen crashed', error, info.componentStack);
  }

  private reload = () => {
    window.location.href = '/afisha';
  };

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <Box
        sx={{
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 2,
          p: 3,
          textAlign: 'center',
          bgcolor: 'surface.main',
          color: 'onSurface.main',
        }}
      >
        <Typography sx={{ fontSize: 40, lineHeight: 1 }} aria-hidden>
          😵
        </Typography>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          Что-то пошло не так
        </Typography>
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
          Экран не смог отобразиться. Ваши лайки и чаты на месте — вернитесь в афишу.
        </Typography>
        <Button variant="filled" onClick={this.reload} sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}>
          Вернуться в афишу
        </Button>
      </Box>
    );
  }
}
