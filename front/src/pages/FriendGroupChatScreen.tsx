import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router';
import type { Client } from '@stomp/stompjs';
import { createStompClient } from '../api/realtime';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { plural } from '../utils/format';
import LinkifiedText from '../components/LinkifiedText';
import type { FriendGroup, PageResponse, FriendGroupChatMessage } from '../types';
import { formatTime, formatDateSeparator, getDateKey } from '../utils/dateUtils';
import ChatInput from '../components/ChatInput';
import {
  AppBar,
  Toolbar,
  IconButton,
  Typography,
  Avatar,
  Chip,
  Box,
  Paper,
  CircularProgress,
  Alert,
} from '@mui/material';
import { ArrowBackOutlined, Diversity3 } from '@mui/icons-material';

export default function FriendGroupChatScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const groupId = Number(id);

  const [group, setGroup] = useState<FriendGroup | null>(null);
  const [messages, setMessages] = useState<FriendGroupChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const stompClientRef = useRef<Client | null>(null);
  const currentUserIdRef = useRef<number | null>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  const loadData = useCallback(async () => {
    if (isNaN(groupId)) {
      setError('Некорректный ID группы');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [messagesResp, userResp, groupResp] = await Promise.all([
        apiClient.get<PageResponse<FriendGroupChatMessage>>(
          `/friend-groups/${groupId}/messages`,
          { params: { page: 0, size: 100, sort: 'createdAt,asc' } },
        ),
        apiClient.get<{ id: number }>('/users/me'),
        apiClient.get<FriendGroup>(`/friend-groups/${groupId}`),
      ]);
      setMessages(messagesResp.data.content);
      currentUserIdRef.current = userResp.data.id;
      setGroup(groupResp.data);
    } catch {
      setError('Не удалось загрузить чат. Попробуйте позже.');
    } finally {
      setLoading(false);
    }
  }, [groupId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (!loading && messages.length > 0) {
      scrollToBottom();
    }
  }, [loading, messages.length, scrollToBottom]);

  useEffect(() => {
    if (isNaN(groupId)) return;
    const client = createStompClient({
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/friend-group/${groupId}`, (frame) => {
          try {
            const newMessage = JSON.parse(frame.body) as FriendGroupChatMessage;
            setMessages((prev) => {
              if (prev.some((m) => m.id === newMessage.id)) return prev;
              return [...prev, newMessage];
            });
            setTimeout(scrollToBottom, 50);
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
  }, [groupId, scrollToBottom]);

  async function handleSend() {
    const trimmed = inputText.trim();
    if (!trimmed || sending) return;
    setSending(true);
    setInputText('');
    try {
      const response = await apiClient.post<FriendGroupChatMessage>(
        `/friend-groups/${groupId}/messages`,
        { text: trimmed },
      );
      setMessages((prev) => {
        if (prev.some((m) => m.id === response.data.id)) return prev;
        return [...prev, response.data];
      });
      setTimeout(scrollToBottom, 50);
    } catch {
      setInputText(trimmed);
      setSendError('Не удалось отправить сообщение');
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Toolbar>
            <IconButton edge="start" aria-label="Назад" onClick={() => navigate(`/friend-groups/${groupId}`)} sx={{ color: 'primary.main' }}>
              <ArrowBackOutlined />
            </IconButton>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
              Загрузка...
            </Typography>
          </Toolbar>
        </AppBar>
        <Box sx={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center' }}>
          <CircularProgress />
        </Box>
      </Box>
    );
  }

  if (error) {
    return (
      <Box>
        <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Toolbar>
            <IconButton edge="start" aria-label="Назад" onClick={() => navigate(`/friend-groups/${groupId}`)} sx={{ color: 'primary.main' }}>
              <ArrowBackOutlined />
            </IconButton>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main' }}>Ошибка</Typography>
          </Toolbar>
        </AppBar>
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
        <Box key={`date-${dateKey}`} sx={{ display: 'flex', justifyContent: 'center', my: 1 }}>
          <Chip
            size="small"
            label={formatDateSeparator(message.createdAt)}
            sx={{ bgcolor: 'surfaceContainerHigh.main', color: 'onSurfaceVariant.main', fontSize: '0.7rem' }}
          />
        </Box>,
      );
      lastDateKey = dateKey;
    }
    const isOwn = message.senderId === currentUserIdRef.current;
    renderedMessages.push(
      <Box
        key={message.id}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          maxWidth: '80%',
          alignSelf: isOwn ? 'flex-end' : 'flex-start',
          alignItems: isOwn ? 'flex-end' : 'flex-start',
        }}
      >
        {!isOwn && (
          <Box
            onClick={() => navigate(`/profile/${message.senderId}`)}
            sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5, cursor: 'pointer' }}
          >
            <Avatar
              src={mediaUrl(message.senderPhoto)}
              alt={message.senderName ?? ''}
              sx={{ width: 24, height: 24, fontSize: '0.65rem', bgcolor: 'primary.main' }}
            >
              {!message.senderPhoto && (message.senderName ?? '?').charAt(0).toUpperCase()}
            </Avatar>
            <Typography variant="caption" sx={{ fontWeight: 600, color: 'primary.main' }}>
              {message.senderName ?? '—'}
            </Typography>
          </Box>
        )}
        <Paper
          elevation={0}
          sx={{
            px: 1.75,
            py: 1,
            borderRadius: 4,
            ...(isOwn
              ? { bgcolor: 'primaryContainer.main', color: 'onPrimaryContainer.main', borderBottomRightRadius: 4 }
              : { bgcolor: 'surfaceContainerHigh.main', color: 'onSurface.main', borderBottomLeftRadius: 4 }),
            wordBreak: 'break-word',
            animation: 'messagePop 0.25s ease-out',
          }}
        >
          <Typography variant="body2" sx={{ lineHeight: 1.4, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}><LinkifiedText text={message.text} color="inherit" /></Typography>
          <Typography
            variant="caption"
            sx={{
              display: 'block',
              fontSize: '0.68rem',
              mt: 0.25,
              textAlign: 'right',
              opacity: 0.7,
              color: isOwn ? 'onPrimaryContainer.main' : 'onSurfaceVariant.main',
            }}
          >
            {formatTime(message.createdAt)}
          </Typography>
        </Paper>
      </Box>,
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main', flexShrink: 0 }}>
        <Toolbar>
          <IconButton edge="start" aria-label="Назад" onClick={() => navigate(`/friend-groups/${groupId}`)} sx={{ color: 'primary.main' }}>
            <ArrowBackOutlined />
          </IconButton>
          <Avatar sx={{ width: 36, height: 36, bgcolor: 'primary.main', mr: 1.25 }}>
            <Diversity3 />
          </Avatar>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 600,
                color: 'onSurface.main',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {group?.name ?? 'Группа друзей'}
            </Typography>
            {group && (
              <Typography variant="caption" sx={{ color: 'primary.main' }}>
                {group.currentSize} {plural(group.currentSize, ['участник', 'участника', 'участников'])}
              </Typography>
            )}
          </Box>
        </Toolbar>
      </AppBar>

      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
          px: 2,
          py: 1.5,
          display: 'flex',
          flexDirection: 'column',
          gap: 1,
        }}
      >
        {messages.length === 0 ? (
          <Box sx={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', textAlign: 'center', px: 3 }}>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
              Сообщений ещё нет — начни первым.
            </Typography>
          </Box>
        ) : (
          renderedMessages
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
