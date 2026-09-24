import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router';
import type { Client } from '@stomp/stompjs';
import { createStompClient } from '../api/realtime';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import LinkifiedText from '../components/LinkifiedText';
import { openExternalLink } from '../api/maxBridge';
import type { Chat, ChatMessage, PageResponse, IceBreakerResponse } from '../types';
import { formatTime, formatDateSeparator, getDateKey } from '../utils/dateUtils';
import IceBreakerSection from '../components/IceBreakerSection';
import ChatInput from '../components/ChatInput';
import {
  AppBar,
  Toolbar,
  IconButton,
  Avatar,
  Typography,
  Box,
  Paper,
  Chip,
  Button,
  CircularProgress,
  Alert,
} from '@mui/material';
import { ArrowBackOutlined, ConfirmationNumberOutlined } from '@mui/icons-material';

export default function ChatScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const chatId = Number(id);

  const [chat, setChat] = useState<Chat | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [iceBreaker, setIceBreaker] = useState<IceBreakerResponse | null>(null);
  const [iceBreakerLoading, setIceBreakerLoading] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const stompClientRef = useRef<Client | null>(null);
  const currentUserIdRef = useRef<number | null>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  const loadChatData = useCallback(async () => {
    if (isNaN(chatId)) {
      setError('Некорректный ID чата');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const [chatsResponse, messagesResponse, userResponse] = await Promise.all([
        apiClient.get<Chat[]>('/chats'),
        apiClient.get<PageResponse<ChatMessage>>(`/chats/${chatId}/messages`, {
          params: { page: 0, size: 100 },
        }),
        apiClient.get<{ id: number }>('/users/me'),
      ]);

      const currentChat = chatsResponse.data.find((item) => item.id === chatId);
      if (!currentChat) {
        throw new Error('Chat not found');
      }

      setChat(currentChat);
      setMessages(messagesResponse.data.content);
      currentUserIdRef.current = userResponse.data.id;

      apiClient.put(`/chats/${chatId}/read`).catch(() => {});
    } catch {
      setError('Не удалось загрузить чат. Попробуйте позже.');
    } finally {
      setLoading(false);
    }
  }, [chatId]);

  useEffect(() => {
    loadChatData();
  }, [loadChatData]);

  useEffect(() => {
    if (!loading && !iceBreaker && !iceBreakerLoading) {
      setIceBreakerLoading(true);
      apiClient
        .get<IceBreakerResponse>(`/chats/${chatId}/icebreakers`)
        .then((res) => setIceBreaker(res.data))
        .catch(() => {})
        .finally(() => setIceBreakerLoading(false));
    }
  }, [loading, chatId, iceBreaker, iceBreakerLoading]);

  useEffect(() => {
    if (!loading && messages.length > 0) {
      scrollToBottom();
    }
  }, [loading, messages.length, scrollToBottom]);

  useEffect(() => {
    const client = createStompClient({
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/queue/messages', (frame) => {
          try {
            const newMessage = JSON.parse(frame.body) as ChatMessage;
            // /user/queue/messages carries messages from all of the user's chats.
            if (newMessage.chatId != null && newMessage.chatId !== chatId) return;
            setMessages((prev) => {
              if (prev.some((m) => m.id === newMessage.id)) return prev;
              return [...prev, newMessage];
            });
            apiClient.put(`/chats/${chatId}/read`).catch(() => {});
          } catch {
            // Ignore parse errors
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
  }, [chatId]);

  async function handleSend() {
    const trimmed = inputText.trim();
    if (!trimmed || sending) return;

    setSending(true);
    setInputText('');

    try {
      const response = await apiClient.post<ChatMessage>(
        `/chats/${chatId}/messages`,
        { text: trimmed },
      );
      setMessages((prev) => {
        if (prev.some((m) => m.id === response.data.id)) return prev;
        return [...prev, response.data];
      });
    } catch {
      setInputText(trimmed);
      setSendError('Не удалось отправить сообщение');
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }

  function handleSuggestionClick(suggestion: string) {
    setInputText(suggestion);
    inputRef.current?.focus();
  }

  const headerBar = (
    <AppBar position="static" color="default" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
      <Toolbar sx={{ gap: 1 }}>
        <IconButton edge="start" aria-label="Назад" onClick={() => navigate('/chats')} sx={{ color: 'primary.main' }}>
          <ArrowBackOutlined />
        </IconButton>
        {chat ? (
          <Box
            onClick={() => chat.companionId && navigate(`/profile/${chat.companionId}`)}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.25,
              flex: 1,
              minWidth: 0,
              cursor: 'pointer',
              borderRadius: 1,
              p: 0.5,
              mx: -0.5,
              '&:active': { bgcolor: 'action.hover' },
            }}
          >
            <Avatar
              src={mediaUrl(chat.companionPhoto)}
              sx={{ width: 36, height: 36, bgcolor: 'primary.main' }}
            >
              {(chat.companionName ?? '?').charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography
                sx={{
                  fontWeight: 600,
                  fontSize: '1rem',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {chat.companionName ?? ''}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  color: 'primary.main',
                  display: 'block',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {chat.eventTitle ?? ''}
              </Typography>
            </Box>
          </Box>
        ) : (
          <Typography sx={{ fontWeight: 600 }}>
            {loading ? 'Загрузка...' : 'Ошибка'}
          </Typography>
        )}
      </Toolbar>
    </AppBar>
  );

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {headerBar}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 1 }}>
          <CircularProgress />
        </Box>
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {headerBar}
        <Alert severity="error" sx={{ m: 2 }}>{error}</Alert>
      </Box>
    );
  }

  const renderedMessages: React.ReactNode[] = [];
  let lastDateKey = '';

  for (const message of messages) {
    const dateKey = getDateKey(message.createdAt);
    if (dateKey !== lastDateKey) {
      renderedMessages.push(
        <Box key={`date-${dateKey}`} sx={{ textAlign: 'center', my: 1 }}>
          <Chip label={formatDateSeparator(message.createdAt)} size="small" sx={{ fontSize: '0.7rem' }} />
        </Box>,
      );
      lastDateKey = dateKey;
    }

    const isOwn = message.senderId === currentUserIdRef.current;
    renderedMessages.push(
      <Paper
        key={message.id}
        elevation={0}
        sx={{
          maxWidth: '80%',
          px: 1.75,
          py: 1,
          borderRadius: '16px',
          alignSelf: isOwn ? 'flex-end' : 'flex-start',
          borderBottomRightRadius: isOwn ? '4px' : '16px',
          borderBottomLeftRadius: isOwn ? '16px' : '4px',
          bgcolor: isOwn ? 'primaryContainer.main' : 'surfaceContainerHigh.main',
          color: isOwn ? 'onPrimaryContainer.main' : 'onSurface.main',
          wordBreak: 'break-word',
          animation: 'messagePop 0.25s ease-out',
        }}
      >
        <Typography variant="body2" sx={{ lineHeight: 1.4, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
          <LinkifiedText text={message.text} color="inherit" />
        </Typography>
        <Typography
          variant="caption"
          sx={{ display: 'block', textAlign: 'right', mt: 0.25, opacity: 0.8, fontSize: '0.68rem' }}
        >
          {formatTime(message.createdAt)}
        </Typography>
      </Paper>,
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {headerBar}

      <Box
        ref={messagesContainerRef}
        sx={{
          flex: 1,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
          px: 2,
          py: 1.5,
          display: 'flex',
          flexDirection: 'column',
          gap: 0.75,
        }}
      >
        {messages.length === 0 ? (
          <IceBreakerSection
            iceBreaker={iceBreaker}
            loading={iceBreakerLoading}
            onSuggestionClick={handleSuggestionClick}
          />
        ) : (
          <>
            {iceBreaker?.event?.ticketUrl && (
              <Button
                variant="filled"
                size="small"
                fullWidth
                startIcon={<ConfirmationNumberOutlined />}
                onClick={() => openExternalLink(iceBreaker.event!.ticketUrl!)}
                sx={{ borderRadius: 2, textTransform: 'none', mb: 1, flexShrink: 0 }}
              >
                Купить билет
              </Button>
            )}
            {renderedMessages}
          </>
        )}
        <div ref={messagesEndRef} />
      </Box>

      <ChatInput
        ref={inputRef}
        value={inputText}
        onChange={setInputText}
        onSend={handleSend}
        disabled={sending}
        error={sendError}
        onErrorClose={() => setSendError('')}
      />
    </Box>
  );
}
