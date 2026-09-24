import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router';
import type { Client } from '@stomp/stompjs';
import { createStompClient } from '../api/realtime';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { plural } from '../utils/format';
import LinkifiedText from '../components/LinkifiedText';
import { openExternalLink } from '../api/maxBridge';
import type { GroupResponse, PageResponse, IceBreakerResponse } from '../types';
import { formatTime, formatDateSeparator, getDateKey } from '../utils/dateUtils';
import IceBreakerSection from '../components/IceBreakerSection';
import ChatInput from '../components/ChatInput';
import {
  AppBar,
  Toolbar,
  IconButton,
  Typography,
  Collapse,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Chip,
  Box,
  Paper,
  Button,
  CircularProgress,
  Alert,
} from '@mui/material';
import {
  ArrowBackOutlined,
  Groups,
  ExpandMoreOutlined,
  ExpandLessOutlined,
  ConfirmationNumberOutlined,
} from '@mui/icons-material';

interface GroupChatMessage {
  id: number;
  senderId: number;
  senderName: string;
  senderPhoto: string | null;
  text: string;
  createdAt: string;
}

export default function GroupChatScreen() {
  const { groupChatId } = useParams<{ groupChatId: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  const groupId = (location.state as { groupId?: number } | null)?.groupId ?? null;
  const chatId = Number(groupChatId);

  const [group, setGroup] = useState<GroupResponse | null>(null);
  const [messages, setMessages] = useState<GroupChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [showMembers, setShowMembers] = useState(false);
  const [iceBreaker, setIceBreaker] = useState<IceBreakerResponse | null>(null);
  const [iceBreakerLoading, setIceBreakerLoading] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const stompClientRef = useRef<Client | null>(null);
  const currentUserIdRef = useRef<number | null>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  const loadData = useCallback(async () => {
    if (isNaN(chatId)) {
      setError('Некорректный ID чата');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const requests: Promise<unknown>[] = [
        apiClient.get<PageResponse<GroupChatMessage>>(`/group-chats/${chatId}/messages`, {
          params: { page: 0, size: 100, sort: 'createdAt,asc' },
        }),
        apiClient.get<{ id: number }>('/users/me'),
      ];

      if (groupId) {
        requests.push(apiClient.get<GroupResponse>(`/groups/${groupId}`));
      }

      const results = await Promise.all(requests);

      const messagesResp = results[0] as { data: PageResponse<GroupChatMessage> };
      const userResp = results[1] as { data: { id: number } };

      setMessages(messagesResp.data.content);
      currentUserIdRef.current = userResp.data.id;

      if (groupId && results[2]) {
        const groupResp = results[2] as { data: GroupResponse };
        setGroup(groupResp.data);
      } else if (!groupId) {
        // Opened via a direct link / reload: no router state, so find the group by its chat id.
        apiClient
          .get<GroupResponse[] | PageResponse<GroupResponse>>('/groups/my')
          .then((res) => {
            const list = Array.isArray(res.data) ? res.data : res.data.content ?? [];
            const found = list.find((g) => g.groupChatId === chatId);
            if (found) setGroup(found);
          })
          .catch(() => {});
      }
    } catch {
      setError('Не удалось загрузить чат. Попробуйте позже.');
    } finally {
      setLoading(false);
    }
  }, [chatId, groupId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (!loading && !iceBreaker && !iceBreakerLoading) {
      setIceBreakerLoading(true);
      apiClient
        .get<IceBreakerResponse>(`/group-chats/${chatId}/icebreakers`)
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
    if (isNaN(chatId)) return;

    const client = createStompClient({
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/group/${chatId}`, (frame) => {
          try {
            const newMessage = JSON.parse(frame.body) as GroupChatMessage;
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
  }, [chatId, scrollToBottom]);

  async function handleSend() {
    const trimmed = inputText.trim();
    if (!trimmed || sending) return;

    setSending(true);
    setInputText('');

    try {
      const response = await apiClient.post<GroupChatMessage>(
        `/group-chats/${chatId}/messages`,
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

  function handleSuggestionClick(suggestion: string) {
    setInputText(suggestion);
    inputRef.current?.focus();
  }

  const groupTitle = group?.title ?? (group ? `Группа #${group.id}` : 'Групповой чат');
  const memberCount = group?.currentSize ?? group?.members?.length ?? null;

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Toolbar>
            <IconButton edge="start" aria-label="Назад" onClick={() => navigate('/groups')} sx={{ color: 'primary.main' }}>
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
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Toolbar>
            <IconButton edge="start" aria-label="Назад" onClick={() => navigate('/groups')} sx={{ color: 'primary.main' }}>
              <ArrowBackOutlined />
            </IconButton>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
              Ошибка
            </Typography>
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
              alt={message.senderName}
              sx={{ width: 24, height: 24, fontSize: '0.65rem', bgcolor: 'primary.main' }}
            >
              {!message.senderPhoto && message.senderName.charAt(0).toUpperCase()}
            </Avatar>
            <Typography variant="caption" sx={{ fontWeight: 600, color: 'primary.main' }}>
              {message.senderName}
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
          <Typography variant="body2" sx={{ lineHeight: 1.4, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
            <LinkifiedText text={message.text} color="inherit" />
          </Typography>
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
      {/* Header */}
      <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main', flexShrink: 0 }}>
        <Toolbar>
          <IconButton edge="start" aria-label="Назад" onClick={() => navigate('/groups')} sx={{ color: 'primary.main' }}>
            <ArrowBackOutlined />
          </IconButton>
          <Box
            component="button"
            onClick={() => setShowMembers((v) => !v)}
            aria-expanded={showMembers}
            aria-label="Показать участников"
            sx={{
              flex: 1,
              minWidth: 0,
              font: 'inherit',
              color: 'inherit',
              display: 'flex',
              alignItems: 'center',
              gap: 1.25,
              border: 'none',
              bgcolor: 'transparent',
              cursor: 'pointer',
              textAlign: 'left',
              p: 0.5,
              borderRadius: 2,
              '&:active': { bgcolor: 'action.hover' },
            }}
          >
            <Avatar sx={{ width: 36, height: 36, bgcolor: 'primary.main' }}>
              <Groups />
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
                {groupTitle}
              </Typography>
              {memberCount !== null && (
                <Typography variant="caption" sx={{ color: 'primary.main' }}>
                  {memberCount} {plural(memberCount, ['участник', 'участника', 'участников'])}
                </Typography>
              )}
            </Box>
            {group?.members && group.members.length > 0 && (
              <Box component="span" sx={{ display: 'flex', p: 0.5, color: 'onSurfaceVariant.main' }}>
                {showMembers ? <ExpandLessOutlined /> : <ExpandMoreOutlined />}
              </Box>
            )}
          </Box>
        </Toolbar>
      </AppBar>

      {/* Members Panel */}
      <Collapse in={showMembers && !!group?.members && group.members.length > 0}>
        <Box sx={{ bgcolor: 'surfaceContainer.main', borderBottom: 1, borderColor: 'outlineVariant.main', maxHeight: 200, overflowY: 'auto' }}>
          <List dense>
            {group?.members?.map((member) => (
              <ListItem
                key={member.userId}
                component="div"
                onClick={() => member.userId !== currentUserIdRef.current && navigate(`/profile/${member.userId}`)}
                sx={{
                  cursor: member.userId !== currentUserIdRef.current ? 'pointer' : 'default',
                  '&:active': member.userId !== currentUserIdRef.current ? { bgcolor: 'action.hover' } : {},
                }}
              >
                <ListItemAvatar>
                  <Avatar
                    src={mediaUrl(member.photo)}
                    alt={member.firstName}
                    sx={{ width: 32, height: 32, bgcolor: 'primary.main' }}
                  >
                    {!member.photo && member.firstName.charAt(0).toUpperCase()}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={member.firstName}
                  primaryTypographyProps={{ variant: 'body2', color: 'onSurface.main' }}
                />
                {member.role === 'CREATOR' && (
                  <Chip
                    size="small"
                    label="Создатель"
                    color="primary"
                    variant="outlined"
                    sx={{ fontSize: '0.7rem', height: 22 }}
                  />
                )}
              </ListItem>
            ))}
          </List>
        </Box>
      </Collapse>

      {/* Messages */}
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
