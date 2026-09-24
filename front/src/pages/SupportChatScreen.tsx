import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router';
import type { Client } from '@stomp/stompjs';
import { createStompClient } from '../api/realtime';
import {
  AppBar,
  Toolbar,
  IconButton,
  Typography,
  Chip,
  Box,
  Paper,
  TextField,
  Button,
  CircularProgress,
  Skeleton,
  Snackbar,
} from '@mui/material';
import {
  ArrowBackOutlined,
  SupportAgentOutlined,
  SendOutlined,
  WarningAmberOutlined,
  ChatBubbleOutlineOutlined,
} from '@mui/icons-material';
import apiClient from '../api/client';
import LinkifiedText from '../components/LinkifiedText';
import { formatTime } from '../utils/dateUtils';
import type { SupportTicket, SupportMessage, PageResponse } from '../types';

const QUICK_SCENARIOS = [
  'Странное поведение пользователя',
  'Баг в приложении',
  'Не нашёл мероприятие',
];

export default function SupportChatScreen() {
  const navigate = useNavigate();

  const [ticket, setTicket] = useState<SupportTicket | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const stompClientRef = useRef<Client | null>(null);
  const isFirstLoad = useRef(true);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  const loadInitial = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const ticketRes = await apiClient.get<SupportTicket>('/support/ticket');
      const t = ticketRes.data;
      setTicket(t);

      const msgsRes = await apiClient.get<PageResponse<SupportMessage>>(
        '/support/ticket/messages',
        { params: { page: 0, size: 20 } },
      );

      const pageData = msgsRes.data;
      const sorted = [...pageData.content].reverse();
      setMessages(sorted);
      setPage(0);
      setHasMore(!pageData.last && pageData.totalPages > 1);

      apiClient.put('/support/ticket/read').catch(() => {/* ignore */});
    } catch {
      setError('Не удалось загрузить чат поддержки. Попробуйте позже.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadInitial();
  }, [loadInitial]);

  useEffect(() => {
    if (!loading && isFirstLoad.current) {
      isFirstLoad.current = false;
      scrollToBottom();
    }
  }, [loading, scrollToBottom]);

  // WebSocket connection
  useEffect(() => {
    if (!ticket) return;

    const client = createStompClient({
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/queue/support', (frame) => {
          try {
            const newMessage = JSON.parse(frame.body) as SupportMessage;
            setMessages((prev) => {
              const exists = prev.some((m) => m.id === newMessage.id);
              if (exists) return prev;
              return [...prev, newMessage];
            });
            setTimeout(() => {
              messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
            }, 50);
            apiClient.put('/support/ticket/read').catch(() => {/* ignore */});
          } catch {
            // ignore parse errors
          }
        });
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
      stompClientRef.current = null;
    };
  }, [ticket]);

  // Infinite scroll upward
  const handleScroll = useCallback(async () => {
    const container = messagesContainerRef.current;
    if (!container || loadingMore || !hasMore) return;

    if (container.scrollTop < 80) {
      setLoadingMore(true);
      const prevScrollHeight = container.scrollHeight;
      const nextPage = page + 1;

      try {
        const res = await apiClient.get<PageResponse<SupportMessage>>(
          '/support/ticket/messages',
          { params: { page: nextPage, size: 20 } },
        );
        const pageData = res.data;
        const older = [...pageData.content].reverse();
        setMessages((prev) => [...older, ...prev]);
        setPage(nextPage);
        setHasMore(!pageData.last && pageData.totalPages > nextPage + 1);

        requestAnimationFrame(() => {
          const newScrollHeight = container.scrollHeight;
          container.scrollTop = newScrollHeight - prevScrollHeight;
        });
      } catch {
        // silently ignore pagination errors
      } finally {
        setLoadingMore(false);
      }
    }
  }, [loadingMore, hasMore, page]);

  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;
    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  async function sendMessage(text: string) {
    const trimmed = text.trim();
    if (!trimmed || sending) return;

    setSending(true);
    setInputText('');

    try {
      const res = await apiClient.post<SupportMessage>('/support/ticket/messages', { text: trimmed });
      setMessages((prev) => {
        const exists = prev.some((m) => m.id === res.data.id);
        if (exists) return prev;
        return [...prev, res.data];
      });
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
      }, 50);
    } catch {
      setInputText(trimmed);
      setSendError('Не удалось отправить сообщение');
    } finally {
      setSending(false);
    }
  }

  function handleSend() {
    sendMessage(inputText);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  // ===== Header =====
  const header = (
    <AppBar
      position="static"
      elevation={0}
      sx={{
        bgcolor: 'surface.main',
        color: 'onSurface.main',
        borderBottom: 1,
        borderColor: 'outlineVariant.main',
      }}
    >
      <Toolbar sx={{ gap: 1 }}>
        <IconButton edge="start" color="inherit" onClick={() => navigate('/chats')}>
          <ArrowBackOutlined />
        </IconButton>
        <SupportAgentOutlined sx={{ color: 'primary.main', fontSize: 28 }} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
            Поддержка JOIN
          </Typography>
          {ticket && (
            <Chip
              label={ticket.status === 'OPEN' ? 'Открыто' : 'Закрыто'}
              size="small"
              sx={{
                height: 20,
                fontSize: '0.7rem',
                mt: 0.25,
                bgcolor: ticket.status === 'OPEN' ? 'primaryContainer.main' : 'surfaceContainerHigh.main',
                color: ticket.status === 'OPEN' ? 'onPrimaryContainer.main' : 'onSurfaceVariant.main',
              }}
            />
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );

  // ===== Loading State =====
  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'surface.main' }}>
        {header}
        <Box sx={{ flex: 1, overflow: 'hidden', p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {[1, 2, 3].map((n) => (
            <Skeleton
              key={n}
              variant="rounded"
              sx={{
                borderRadius: 4,
                height: 44,
                width: n % 2 === 0 ? '50%' : '60%',
                alignSelf: n % 2 === 0 ? 'flex-end' : 'flex-start',
              }}
            />
          ))}
        </Box>
        <Paper
          elevation={0}
          sx={{
            display: 'flex',
            alignItems: 'flex-end',
            gap: 1,
            p: 1,
            px: 2,
            borderTop: 1,
            borderColor: 'outlineVariant.main',
            borderRadius: 0,
          }}
        >
          <Skeleton variant="rounded" sx={{ flex: 1, height: 40, borderRadius: 5 }} />
          <Skeleton variant="circular" width={40} height={40} />
        </Paper>
      </Box>
    );
  }

  // ===== Error State =====
  if (error) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'surface.main' }}>
        {header}
        <Box
          sx={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            p: 3,
            textAlign: 'center',
          }}
        >
          <WarningAmberOutlined sx={{ fontSize: 48, color: 'error.main', mb: 1.5 }} />
          <Typography variant="body1" sx={{ color: 'error.main', mb: 2.5, lineHeight: 1.4 }}>
            {error}
          </Typography>
          <Button
            variant="outlined"
            onClick={loadInitial}
            sx={{ borderRadius: 5, px: 3 }}
          >
            Повторить
          </Button>
        </Box>
      </Box>
    );
  }

  const isEmpty = messages.length === 0;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'surface.main' }}>
      {header}

      {/* Messages Area */}
      <Box
        ref={messagesContainerRef}
        sx={{
          flex: 1,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
          p: 2,
          display: 'flex',
          flexDirection: 'column',
          gap: 1,
        }}
      >
        {loadingMore && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
            <CircularProgress size={20} />
          </Box>
        )}

        {isEmpty ? (
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              flex: 1,
              p: 3,
              textAlign: 'center',
              animation: 'fadeInUp 0.4s ease-out',
            }}
          >
            <ChatBubbleOutlineOutlined sx={{ fontSize: 48, color: 'primary.main', mb: 1.5 }} />
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 0.5 }}>
              Напишите нам — мы ответим!
            </Typography>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 3 }}>
              Выберите тему или напишите свой вопрос
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25, width: '100%', maxWidth: 320 }}>
              {QUICK_SCENARIOS.map((scenario, index) => (
                <Button
                  key={scenario}
                  variant="outlined"
                  disabled={sending}
                  onClick={() => sendMessage(scenario)}
                  sx={{
                    borderRadius: 5,
                    textTransform: 'none',
                    fontSize: '0.88rem',
                    py: 1,
                    bgcolor: 'secondaryContainer.main',
                    color: 'onSecondaryContainer.main',
                    borderColor: 'transparent',
                    animation: 'fadeInUp 0.35s ease-out both',
                    animationDelay: `${0.15 + index * 0.08}s`,
                    transition: 'background-color 0.2s ease, color 0.2s ease, transform 0.15s ease',
                    '&:hover': {
                      bgcolor: 'primary.main',
                      color: 'onPrimary.main',
                      borderColor: 'transparent',
                    },
                    '&:active': {
                      transform: 'scale(0.97)',
                    },
                  }}
                >
                  {scenario}
                </Button>
              ))}
            </Box>
          </Box>
        ) : (
          messages.map((msg) => {
            const isUser = msg.senderType === 'USER';
            return (
              <Paper
                key={msg.id}
                elevation={0}
                sx={{
                  maxWidth: '80%',
                  px: 1.75,
                  py: 1,
                  borderRadius: 4,
                  alignSelf: isUser ? 'flex-end' : 'flex-start',
                  bgcolor: isUser ? 'primaryContainer.main' : 'surfaceContainerHigh.main',
                  color: isUser ? 'onPrimaryContainer.main' : 'onSurface.main',
                  borderBottomRightRadius: isUser ? 4 : undefined,
                  borderBottomLeftRadius: !isUser ? 4 : undefined,
                  animation: 'messagePop 0.25s ease-out',
                }}
              >
                {!isUser && (
                  <Typography
                    variant="caption"
                    sx={{ fontWeight: 600, color: 'primary.main', display: 'block', mb: 0.25 }}
                  >
                    Поддержка
                  </Typography>
                )}
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', lineHeight: 1.4 }}>
                  <LinkifiedText text={msg.text} color="inherit" />
                </Typography>
                <Typography
                  variant="caption"
                  sx={{
                    display: 'block',
                    mt: 0.5,
                    fontSize: '0.68rem',
                    opacity: 0.7,
                    textAlign: isUser ? 'right' : 'left',
                  }}
                >
                  {formatTime(msg.createdAt)}
                </Typography>
              </Paper>
            );
          })
        )}

        <div ref={messagesEndRef} />
      </Box>

      {/* Input Area */}
      <Paper
        elevation={0}
        sx={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 1,
          px: 2,
          py: 1,
          pb: 'calc(8px + var(--safe-area-bottom, 0px))',
          borderTop: 1,
          borderColor: 'outlineVariant.main',
          borderRadius: 0,
          flexShrink: 0,
        }}
      >
        <TextField
          multiline
          maxRows={4}
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Напишите сообщение..."
          inputProps={{ maxLength: 2000, enterKeyHint: 'send' }}
          sx={{
            flex: 1,
            '& .MuiOutlinedInput-root': {
              borderRadius: 5,
              bgcolor: 'surfaceContainerHigh.main',
              '& fieldset': { borderColor: 'outlineVariant.main' },
            },
            '& .MuiInputBase-input': {
              fontSize: '0.9rem',
              py: 1,
            },
          }}
          size="small"
        />
        <IconButton
          onClick={handleSend}
          aria-label="Отправить"
          disabled={!inputText.trim() || sending}
          sx={{
            bgcolor: 'primary.main',
            color: 'onPrimary.main',
            width: 40,
            height: 40,
            '&:hover': { bgcolor: 'primary.dark' },
            '&.Mui-disabled': { bgcolor: 'surfaceContainerHigh.main', color: 'onSurfaceVariant.main' },
            transition: 'transform 0.15s ease, background-color 0.2s ease',
            '&:active': { transform: 'scale(0.9)' },
          }}
        >
          {sending ? <CircularProgress size={18} color="inherit" /> : <SendOutlined />}
        </IconButton>
        <Snackbar
          open={!!sendError}
          autoHideDuration={3000}
          onClose={() => setSendError('')}
          message={sendError}
          sx={{ bottom: { xs: 140 } }}
        />
      </Paper>
    </Box>
  );
}
