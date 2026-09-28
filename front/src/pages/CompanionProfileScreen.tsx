import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { INTEREST_LABELS } from '../types';
import type { BlockStatus, CompanionProfile, Chat, MatchSuggestion } from '../types';
import { acceptMatch, declineMatch, errorMessage, inviteMatch } from '../api/matches';
import LinkifiedText from '../components/LinkifiedText';
import BlockUserDialog from '../components/BlockUserDialog';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import ChatBubbleOutlineOutlined from '@mui/icons-material/ChatBubbleOutlineOutlined';

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Card from '@mui/material/Card';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';

import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined';
import { getTagChipSx } from '../components/tagChipStyles';

const GENDER_LABELS: Record<string, string> = {
  MALE: 'Мужской',
  FEMALE: 'Женский',
  male: 'Мужской',
  female: 'Женский',
};

export default function CompanionProfileScreen() {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();

  const [profile, setProfile] = useState<CompanionProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [chatId, setChatId] = useState<number | null>(null);
  // A found companion without a chat yet: invite or answer the invitation here.
  const [suggestion, setSuggestion] = useState<MatchSuggestion | null>(null);
  const [suggestionBusy, setSuggestionBusy] = useState(false);
  const [suggestionNote, setSuggestionNote] = useState('');
  const [blockStatus, setBlockStatus] = useState<BlockStatus | null>(null);
  const [showBlockDialog, setShowBlockDialog] = useState(false);

  useEffect(() => {
    apiClient
      .get<BlockStatus>(`/users/${userId}/block`)
      .then((res) => setBlockStatus(res.data))
      .catch(() => {});
  }, [userId]);

  useEffect(() => {
    // Offer a shortcut to the existing 1:1 chat with this person, if any.
    apiClient
      .get<Chat[]>('/chats')
      .then((res) => {
        const chat = res.data.find((c) => String(c.companionId) === userId);
        setChatId(chat ? chat.id : null);
      })
      .catch(() => {});
    apiClient
      .get<MatchSuggestion[]>('/matches')
      .then((res) => setSuggestion(res.data.find((m) => String(m.companionId) === userId) ?? null))
      .catch(() => {});
  }, [userId]);

  async function actOnSuggestion(action: (id: number) => Promise<MatchSuggestion>) {
    if (!suggestion) return;
    setSuggestionBusy(true);
    setSuggestionNote('');
    try {
      const updated = await action(suggestion.id);
      if (updated.status === 'ACCEPTED' && updated.chatId) {
        navigate(`/chats/${updated.chatId}`);
        return;
      }
      setSuggestion(updated.status === 'DECLINED' ? null : updated);
    } catch (err) {
      setSuggestionNote(errorMessage(err, 'Не получилось, попробуйте ещё раз'));
    } finally {
      setSuggestionBusy(false);
    }
  }

  const loadProfile = useCallback(async () => {
    if (!userId || isNaN(Number(userId))) {
      setError('Некорректный ID пользователя');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await apiClient.get<CompanionProfile>(`/users/${userId}/profile`);
      setProfile(response.data);
    } catch {
      setError('Не удалось загрузить профиль.');
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const headerBar = (
    <AppBar position="sticky" color="default" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
      <Toolbar>
        <IconButton edge="start" onClick={() => ((window.history.state as { idx?: number } | null)?.idx ? navigate(-1) : navigate('/chats'))} aria-label="Назад" sx={{ mr: 1.5 }}>
          <ArrowBackOutlined />
        </IconButton>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          Профиль
        </Typography>
      </Toolbar>
    </AppBar>
  );

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
        {headerBar}
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', py: 6, gap: 1.5 }}>
          <CircularProgress />
          <Typography variant="body2" color="text.secondary">
            Загрузка профиля...
          </Typography>
        </Box>
      </Box>
    );
  }

  if (error || !profile) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
        {headerBar}
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6, px: 2, gap: 2 }}>
          <Alert severity="error" sx={{ width: '100%', maxWidth: 400 }}>
            {error || 'Профиль не найден'}
          </Alert>
          <Button variant="outlined" onClick={loadProfile} sx={{ textTransform: 'none', borderRadius: 5 }}>
            Повторить
          </Button>
        </Box>
      </Box>
    );
  }

  const fullName = profile.lastName
    ? `${profile.firstName} ${profile.lastName}`
    : profile.firstName;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
      {headerBar}

      <Box sx={{ p: 2, pb: 4, maxWidth: 480, mx: 'auto', width: '100%' }}>
        {/* Hero section */}
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 3, animation: 'fadeInUp 0.4s ease-out' }}>
          <Avatar
            src={mediaUrl(profile.photo)}
            alt={fullName}
            sx={{
              width: 96,
              height: 96,
              mb: 1.5,
              fontSize: '2.5rem',
              fontWeight: 600,
              bgcolor: 'primary.main',
              color: 'primary.contrastText',
              border: 3,
              borderStyle: 'solid',
              borderColor: 'primary.main',
            }}
          >
            {profile.firstName.charAt(0).toUpperCase()}
          </Avatar>
          <Typography variant="h5" fontWeight={700} textAlign="center" sx={{ overflowWrap: 'anywhere' }}>
            {fullName}
          </Typography>
          {profile.status && (
            <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mt: 0.5 }}>
              {profile.status}
            </Typography>
          )}
          {chatId != null && !blockStatus?.blockedByMe && !blockStatus?.blockedMe && (
            <Button
              variant="filled"
              startIcon={<ChatBubbleOutlineOutlined />}
              onClick={() => navigate(`/chats/${chatId}`)}
              sx={{ mt: 2, borderRadius: 3, textTransform: 'none', fontWeight: 600, px: 3 }}
            >
              Написать
            </Button>
          )}
          {chatId == null && suggestion && !blockStatus?.blockedByMe && !blockStatus?.blockedMe && (
            <Box sx={{ mt: 2, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1 }}>
              <Typography variant="body2" color="text.secondary" textAlign="center">
                {suggestion.status === 'REQUESTED' && !suggestion.requestedByMe
                  ? `Зовёт пойти вместе на «${suggestion.eventTitle}»`
                  : suggestion.status === 'REQUESTED'
                    ? 'Позвали — чат откроется, когда придёт ответ'
                    : `Тоже хочет на «${suggestion.eventTitle}»`}
              </Typography>
              {suggestion.status === 'REQUESTED' && !suggestion.requestedByMe && (
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button disabled={suggestionBusy} onClick={() => actOnSuggestion(declineMatch)} sx={{ textTransform: 'none' }}>
                    Не в этот раз
                  </Button>
                  <Button variant="filled" disabled={suggestionBusy} onClick={() => actOnSuggestion(acceptMatch)}
                    sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600, px: 3 }}>
                    Пойдём
                  </Button>
                </Box>
              )}
              {suggestion.status === 'NEW' && (
                <Button variant="filled" disabled={suggestionBusy} onClick={() => actOnSuggestion(inviteMatch)}
                  sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600, px: 3 }}>
                  Позвать пойти вместе
                </Button>
              )}
              {suggestionNote && <Alert severity="error">{suggestionNote}</Alert>}
            </Box>
          )}
        </Box>

        {/* Info section */}
        <Card variant="outlined" sx={{ mb: 3, borderRadius: 3, borderColor: 'outlineVariant.main', animation: 'fadeInUp 0.4s ease-out both', animationDelay: '0.1s' }}>
          <List disablePadding>
            <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
              <ListItemText primary="Возраст" secondary={profile.age} primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }} secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main' }} />
            </ListItem>
            <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
              <ListItemText primary="Город" secondary={profile.city} primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }} secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }} />
            </ListItem>
            {profile.universityName && (
              <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
                <ListItemText primary="ВУЗ" secondary={profile.universityName} primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }} secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }} />
              </ListItem>
            )}
            <ListItem>
              <ListItemText primary="Пол" secondary={GENDER_LABELS[profile.gender] ?? profile.gender} primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }} secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }} />
            </ListItem>
          </List>
        </Card>

        {/* Bio section */}
        {profile.bio && (
          <Card variant="outlined" sx={{ mb: 3, p: 2, borderRadius: 3, borderColor: 'outlineVariant.main', animation: 'fadeInUp 0.4s ease-out both', animationDelay: '0.15s' }}>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
              О себе
            </Typography>
            <Typography variant="body2" sx={{ lineHeight: 1.5, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
              <LinkifiedText text={profile.bio} />
            </Typography>
          </Card>
        )}

        {/* Interests section */}
        {profile.interests.length > 0 && (
          <Box sx={{ mb: 3, animation: 'fadeInUp 0.4s ease-out both', animationDelay: '0.2s' }}>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
              Интересы
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
              {profile.interests.map((interest) => (
                <Chip
                  key={interest}
                  label={INTEREST_LABELS[interest] ?? interest}
                  size="small"
                  sx={getTagChipSx(interest)}
                />
              ))}
            </Box>
          </Box>
        )}

        {blockStatus && (
          <Button
            variant="text"
            color={blockStatus.blockedByMe ? 'primary' : 'error'}
            startIcon={<BlockOutlined />}
            onClick={() => setShowBlockDialog(true)}
            sx={{ textTransform: 'none', alignSelf: 'center', display: 'flex', mx: 'auto', mb: 2 }}
          >
            {blockStatus.blockedByMe ? 'Разблокировать' : 'Заблокировать'}
          </Button>
        )}
      </Box>

      {showBlockDialog && profile && blockStatus && (
        <BlockUserDialog
          userId={Number(userId)}
          name={profile.firstName}
          blockedByMe={blockStatus.blockedByMe}
          onClose={() => setShowBlockDialog(false)}
          onChanged={setBlockStatus}
        />
      )}
    </Box>
  );
}
