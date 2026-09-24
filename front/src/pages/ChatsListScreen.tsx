import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router';
import { createStompClient } from '../api/realtime';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import type { Chat } from '../types';
import { formatMessageTime } from '../utils/dateUtils';
import {
  Typography,
  Card,
  CardActionArea,
  CardContent,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItemButton,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Badge,
  CircularProgress,
  Divider,
  Box,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  IconButton,
} from '@mui/material';
import {
  SupportAgentOutlined,
  InfoOutlined,
  ExpandMore,
  ChatBubbleOutline,
  DeleteOutline,
} from '@mui/icons-material';

const SWIPE_THRESHOLD = 80;

interface SwipeableChatItemProps {
  chat: Chat;
  onClick: () => void;
  onDelete: () => void;
  isLast: boolean;
}

function SwipeableChatItem({ chat, onClick, onDelete, isLast }: SwipeableChatItemProps) {
  const startXRef = useRef(0);
  const currentXRef = useRef(0);
  const swipingRef = useRef(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const [offsetX, setOffsetX] = useState(0);
  const [showDelete, setShowDelete] = useState(false);

  function handleTouchStart(e: React.TouchEvent) {
    startXRef.current = e.touches[0].clientX;
    currentXRef.current = 0;
    swipingRef.current = false;
  }

  function handleTouchMove(e: React.TouchEvent) {
    const dx = e.touches[0].clientX - startXRef.current;
    currentXRef.current = dx;
    if (dx < -10) {
      swipingRef.current = true;
      setOffsetX(Math.max(dx, -SWIPE_THRESHOLD - 20));
    } else if (showDelete && dx > 10) {
      swipingRef.current = true;
      setOffsetX(Math.min(dx - SWIPE_THRESHOLD, 0));
    }
  }

  function handleTouchEnd() {
    if (currentXRef.current < -SWIPE_THRESHOLD) {
      setOffsetX(-SWIPE_THRESHOLD);
      setShowDelete(true);
    } else {
      setOffsetX(0);
      setShowDelete(false);
    }
  }

  function handleClick() {
    if (swipingRef.current) return;
    if (showDelete) {
      setOffsetX(0);
      setShowDelete(false);
      return;
    }
    onClick();
  }

  return (
    <Box sx={{ position: 'relative', overflow: 'hidden' }}>
      {/* Delete background */}
      <Box
        sx={{
          position: 'absolute',
          right: 0,
          top: 0,
          bottom: 0,
          width: SWIPE_THRESHOLD,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'error.main',
          color: 'onError.main',
          cursor: 'pointer',
        }}
        onClick={onDelete}
        role="button"
        aria-label="Удалить чат"
      >
        <DeleteOutline />
      </Box>

      {/* Swipeable content */}
      <Box
        ref={containerRef}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        sx={{
          transform: `translateX(${offsetX}px)`,
          transition: swipingRef.current ? 'none' : 'transform 0.2s ease-out',
          bgcolor: 'background.paper',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <ListItemButton
          onClick={handleClick}
          sx={{ py: 1.5, px: 2, gap: 1.5 }}
        >
          <ListItemAvatar sx={{ minWidth: 0 }}>
            <Badge
              badgeContent={chat.unreadCount}
              color="primary"
              invisible={chat.unreadCount <= 0}
            >
              <Avatar
                src={mediaUrl(chat.companionPhoto)}
                sx={{
                  width: 48,
                  height: 48,
                  bgcolor: 'primary.main',
                }}
              >
                {(chat.companionName ?? '?').charAt(0).toUpperCase()}
              </Avatar>
            </Badge>
          </ListItemAvatar>
          <ListItemText
            primary={
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1 }}>
                <Typography
                  sx={{
                    minWidth: 0,
                    flex: 1,
                    fontWeight: 600,
                    fontSize: '0.95rem',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {chat.companionName ?? 'Без имени'}
                </Typography>
                {chat.lastMessageTime && (
                  <Typography
                    variant="caption"
                    sx={{ color: 'text.secondary', flexShrink: 0 }}
                  >
                    {formatMessageTime(chat.lastMessageTime)}
                  </Typography>
                )}
              </Box>
            }
            secondary={
              <>
                <Typography
                  component="span"
                  variant="caption"
                  sx={{
                    color: 'primary.main',
                    display: 'block',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {chat.eventTitle}
                </Typography>
                <Typography
                  component="span"
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    display: 'block',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {chat.lastMessage ?? 'Начните общение!'}
                </Typography>
              </>
            }
            disableTypography={false}
            sx={{ my: 0 }}
          />
          <IconButton
            size="small"
            onClick={(e) => { e.stopPropagation(); onDelete(); }}
            sx={{
              // Touch devices delete via swipe; an invisible button would still catch taps.
              display: 'none',
              '@media (hover: hover)': { display: 'inline-flex', opacity: 0 },
              '.MuiListItemButton-root:hover &': { opacity: 1 },
              transition: 'opacity 0.2s',
              color: 'error.main',
              flexShrink: 0,
            }}
            aria-label="Удалить чат"
          >
            <DeleteOutline fontSize="small" />
          </IconButton>
        </ListItemButton>
        {!isLast && <Divider sx={{ borderColor: 'outlineVariant.main', ml: 9 }} />}
      </Box>
    </Box>
  );
}

export default function ChatsListScreen() {
  const navigate = useNavigate();
  const [chats, setChats] = useState<Chat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [systemCardExpanded, setSystemCardExpanded] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Chat | null>(null);

  // silent: refresh in the background (WebSocket push) without flashing the spinner.
  const loadChats = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
      setError('');
    }

    try {
      const response = await apiClient.get<Chat[]>('/chats');
      setChats(response.data);
    } catch {
      if (!silent) setError('Не удалось загрузить чаты. Попробуйте позже.');
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadChats();
  }, [loadChats]);

  // Re-fetch chat list when a new message arrives via WebSocket
  useEffect(() => {
    const client = createStompClient({
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/queue/messages', () => {
          loadChats(true);
        });
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, [loadChats]);

  function handleChatClick(chatId: number) {
    navigate(`/chats/${chatId}`);
  }

  async function handleDeleteConfirm() {
    if (!deleteTarget) return;
    const chatId = deleteTarget.id;
    setDeleteTarget(null);
    setChats((prev) => prev.filter((c) => c.id !== chatId));
    try {
      await apiClient.delete(`/chats/${chatId}`);
    } catch {
      loadChats(true);
    }
  }

  return (
    <Box sx={{ pb: 2 }}>
      <Typography variant="h5" sx={{ fontWeight: 700, px: 2, pt: 2, pb: 1.5 }}>
        Чаты
      </Typography>

      {/* Pinned Support Chat */}
      <Card
        variant="outlined"
        sx={{ mx: 2, mb: 1, borderColor: 'primary.main', borderWidth: 1.5, animation: 'fadeInUp 0.3s ease-out' }}
      >
        <CardActionArea onClick={() => navigate('/support')}>
          <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <SupportAgentOutlined sx={{ color: 'primary.main' }} />
              <Typography sx={{ fontWeight: 700, fontSize: '0.95rem', color: 'primary.main' }}>
                Поддержка
              </Typography>
            </Box>
            <Typography
              variant="body2"
              sx={{ color: 'onSurfaceVariant.main', mt: 0.25, pl: '32px' }}
            >
              Напишите нам — мы поможем!
            </Typography>
          </CardContent>
        </CardActionArea>
      </Card>

      {/* System Info Accordion */}
      <Accordion
        variant="outlined"
        expanded={systemCardExpanded}
        onChange={() => setSystemCardExpanded((prev) => !prev)}
        sx={{ mx: 2, mb: 1.5, borderRadius: '12px !important', '&:before': { display: 'none' } }}
        disableGutters
      >
        <AccordionSummary expandIcon={<ExpandMore />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <InfoOutlined sx={{ color: 'primary.main' }} />
            <Typography sx={{ fontWeight: 600, fontSize: '0.95rem', color: 'primary.main' }}>
              Как пользоваться сервисом JOIN?
            </Typography>
          </Box>
        </AccordionSummary>
        <AccordionDetails>
          <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.5 }}>
            1. Ищите мероприятия в Афише<br />
            2. Лайкайте понравившиеся<br />
            3. Если кто-то тоже лайкнет — появится чат!
          </Typography>
        </AccordionDetails>
      </Accordion>

      {loading && (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6 }}>
          <CircularProgress sx={{ mb: 1.5 }} />
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            Загрузка чатов...
          </Typography>
        </Box>
      )}

      {error && !loading && (
        <Alert
          severity="error"
          sx={{ mx: 2 }}
          action={<Button color="inherit" size="small" onClick={() => loadChats()}>Повторить</Button>}
        >
          {error}
        </Alert>
      )}

      {!loading && !error && chats.length === 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6, px: 3, animation: 'fadeInUp 0.4s ease-out' }}>
          <ChatBubbleOutline sx={{ fontSize: '3rem', color: 'text.disabled', mb: 1.5 }} />
          <Typography sx={{ color: 'text.secondary', textAlign: 'center', lineHeight: 1.5 }}>
            У вас пока нет метчей.<br />
            Лайкайте мероприятия в Афише!
          </Typography>
        </Box>
      )}

      {!loading && !error && chats.length > 0 && (
        <List disablePadding>
          {chats.map((chat, index) => (
            <Box
              key={chat.id}
              sx={{
                animation: 'slideInRight 0.35s ease-out both',
                animationDelay: `${Math.min(index * 0.05, 0.4)}s`,
              }}
            >
              <SwipeableChatItem
                chat={chat}
                onClick={() => handleChatClick(chat.id)}
                onDelete={() => setDeleteTarget(chat)}
                isLast={index === chats.length - 1}
              />
            </Box>
          ))}
        </List>
      )}

      {/* Delete confirmation dialog */}
      <Dialog open={deleteTarget !== null} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Удалить чат?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Чат с {deleteTarget?.companionName ?? 'пользователем'} будет удалён только у вас.
            Если собеседник напишет новое сообщение, чат появится снова.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Отмена</Button>
          <Button color="error" onClick={handleDeleteConfirm}>
            Удалить
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
