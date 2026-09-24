import { useState, useEffect, useCallback, useRef, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
  Typography,
  CircularProgress,
  Alert,
  Box,
  Button,
  Card,
  Stack,
  AvatarGroup,
  Avatar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Slider,
  AppBar,
  Toolbar,
  IconButton,
} from '@mui/material';
import {
  Diversity3,
  ArrowBackOutlined,
  AddOutlined,
  LoginOutlined,
  QrCodeScannerOutlined,
} from '@mui/icons-material';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { extractInviteCode } from '../utils/inviteLinks';
import { isCodeReaderSupported, scanQrCode } from '../api/maxBridge';
import { plural } from '../utils/format';
import type {
  FriendGroup,
  PageResponse,
  CreateFriendGroupRequest,
  JoinFriendGroupResponse,
} from '../types';

interface AxiosErrorLike {
  response?: { status?: number; data?: { message?: string } };
}

export default function FriendGroupsScreen() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [groups, setGroups] = useState<FriendGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState('');
  const [createMaxSize, setCreateMaxSize] = useState(5);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [createError, setCreateError] = useState('');

  const [joinOpen, setJoinOpen] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [joinSubmitting, setJoinSubmitting] = useState(false);
  const [joinError, setJoinError] = useState('');

  const [scanSupported, setScanSupported] = useState(false);
  const [scanError, setScanError] = useState('');

  // Holds the latest reference to submitJoin so effects can call it without
  // becoming a dependency of every state change submitJoin closes over.
  const submitJoinRef = useRef<((code: string) => Promise<boolean>) | null>(null);

  const loadGroups = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const resp = await apiClient.get<PageResponse<FriendGroup>>('/friend-groups');
      setGroups(resp.data.content ?? []);
    } catch {
      setError('Не удалось загрузить группы друзей. Попробуйте позже.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadGroups();
  }, [loadGroups]);

  useEffect(() => {
    setScanSupported(isCodeReaderSupported());
  }, []);

  useEffect(() => {
    const invite = searchParams.get('invite');
    if (!invite || !invite.trim()) return;
    const code = invite.trim().toLowerCase();
    // Drop the query immediately so reopening the screen / hot-reload doesn't
    // re-trigger another join attempt with the same code.
    const next = new URLSearchParams(searchParams);
    next.delete('invite');
    setSearchParams(next, { replace: true });
    // Show the join dialog with the code pre-filled, then auto-submit it.
    // If the join fails (already-member, expired, etc.) the dialog stays open
    // with the inline error explaining why.
    setJoinCode(code);
    setJoinOpen(true);
    submitJoinRef.current?.(code);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  async function handleCreate(e?: FormEvent) {
    e?.preventDefault();
    if (createSubmitting) return;
    setCreateError('');
    const trimmed = createName.trim();
    if (!trimmed) {
      setCreateError('Укажите название группы');
      return;
    }
    setCreateSubmitting(true);
    try {
      const body: CreateFriendGroupRequest = { name: trimmed, maxSize: createMaxSize };
      const resp = await apiClient.post<FriendGroup>('/friend-groups', body);
      setCreateOpen(false);
      setCreateName('');
      setCreateMaxSize(5);
      navigate(`/friend-groups/${resp.data.id}`);
    } catch (err) {
      const axiosErr = err as AxiosErrorLike;
      setCreateError(axiosErr.response?.data?.message ?? 'Не удалось создать группу');
    } finally {
      setCreateSubmitting(false);
    }
  }

  const submitJoin = useCallback(async (code: string): Promise<boolean> => {
    setJoinError('');
    setJoinSubmitting(true);
    try {
      const resp = await apiClient.post<JoinFriendGroupResponse>('/friend-groups/join', {
        inviteCode: code,
      });
      setJoinOpen(false);
      setJoinCode('');
      navigate(`/friend-groups/${resp.data.friendGroupId}`);
      return true;
    } catch (err) {
      const axiosErr = err as AxiosErrorLike;
      const status = axiosErr.response?.status;
      if (status === 404) {
        setJoinError('Код приглашения не найден');
      } else if (status === 409 || status === 400) {
        setJoinError(axiosErr.response?.data?.message ?? 'Не удалось вступить в группу');
      } else {
        setJoinError('Не удалось вступить в группу. Попробуйте позже.');
      }
      return false;
    } finally {
      setJoinSubmitting(false);
    }
  }, [navigate]);

  // Keep the ref pointing at the latest submitJoin closure so the
  // invite-from-URL effect doesn't capture a stale one.
  useEffect(() => {
    submitJoinRef.current = submitJoin;
  }, [submitJoin]);

  async function handleJoin(e?: FormEvent) {
    e?.preventDefault();
    if (joinSubmitting) return;
    const raw = joinCode.trim();
    if (!raw) {
      setJoinError('Введите код или ссылку-приглашение');
      return;
    }
    // Accept a full invite link (https://max.ru/<bot>?startapp=join_xxx) as well as the bare code.
    const code = extractInviteCode(raw) ?? raw.toLowerCase();
    await submitJoin(code);
  }

  async function handleScan() {
    setScanError('');
    if (!isCodeReaderSupported()) {
      setScanError('Сканер недоступен здесь — введите код вручную.');
      return;
    }
    const text = await scanQrCode();
    if (!text) return;
    const code = extractInviteCode(text);
    if (!code) {
      setScanError('Это не QR-код приглашения JOIN.');
      return;
    }
    setJoinCode(code);
    setJoinOpen(true);
    // Fire join in the background; result navigates on success or shows
    // the inline error inside the join dialog if it fails.
    submitJoin(code);
  }


  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
      <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
        <Toolbar>
          <IconButton edge="start" onClick={() => navigate('/groups')} sx={{ color: 'primary.main' }} aria-label="Назад">
            <ArrowBackOutlined />
          </IconButton>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
            Группы друзей
          </Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Button
            variant="filled"
            startIcon={<AddOutlined />}
            onClick={() => setCreateOpen(true)}
            sx={{ flex: 1, minWidth: 120 }}
          >
            Создать
          </Button>
          <Button
            variant="tonal"
            startIcon={<LoginOutlined />}
            onClick={() => setJoinOpen(true)}
            sx={{ flex: 1, minWidth: 120 }}
          >
            По коду
          </Button>
          {scanSupported && (
            <Button
              variant="tonal"
              startIcon={<QrCodeScannerOutlined />}
              onClick={handleScan}
              aria-label="Сканировать QR-код приглашения"
              sx={{ flex: 1, minWidth: 120, whiteSpace: 'nowrap' }}
            >
              QR-код
            </Button>
          )}
        </Stack>
        {scanError && (
          <Alert severity="warning" sx={{ mt: -1 }}>{scanError}</Alert>
        )}

        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}

        {error && !loading && (
          <Alert
            severity="error"
            action={<Button color="inherit" size="small" onClick={loadGroups}>Повторить</Button>}
          >
            {error}
          </Alert>
        )}

        {!loading && !error && groups.length === 0 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, py: 5, textAlign: 'center' }}>
            <Diversity3 sx={{ fontSize: 64, color: 'onSurfaceVariant.main' }} />
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', maxWidth: 280 }}>
              Создайте группу с друзьями и лайкайте мероприятия — найдём то, что хотят все.
            </Typography>
          </Box>
        )}

        {!loading && !error && groups.length > 0 && (
          <Stack spacing={1.5}>
            {groups.map((group, index) => (
              <Card
                key={group.id}
                variant="outlined"
                onClick={() => navigate(`/friend-groups/${group.id}`)}
                sx={{
                  borderRadius: 3.5,
                  p: 1.75,
                  cursor: 'pointer',
                  animation: 'fadeInUp 0.3s ease-out both',
                  animationDelay: `${index * 0.06}s`,
                  '&:active': { bgcolor: 'action.hover' },
                }}
              >
                <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'onSurface.main', mb: 0.75, overflowWrap: 'anywhere' }}>
                  {group.name}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <AvatarGroup max={4} sx={{ '& .MuiAvatar-root': { width: 28, height: 28, fontSize: '0.7rem' } }}>
                    {group.members.map((m) => (
                      <Avatar
                        key={m.userId}
                        src={mediaUrl(m.photo)}
                        alt={m.firstName ?? ''}
                        sx={{ bgcolor: 'primary.main' }}
                      >
                        {!m.photo && m.firstName?.charAt(0)}
                      </Avatar>
                    ))}
                  </AvatarGroup>
                  <Typography variant="caption" sx={{ fontWeight: 600, color: 'primary.main' }}>
                    {group.currentSize}/{group.maxSize} {plural(group.maxSize, ['участник', 'участника', 'участников'])}
                  </Typography>
                </Box>
              </Card>
            ))}
          </Stack>
        )}
      </Box>

      {/* Create Dialog */}
      <Dialog
        open={createOpen}
        onClose={() => !createSubmitting && setCreateOpen(false)}
        fullWidth
        maxWidth="xs"
        PaperProps={{ component: 'form', onSubmit: handleCreate, noValidate: true }}
      >
        <DialogTitle>Создать группу друзей</DialogTitle>
        <DialogContent>
          <TextField
            label="Название"
            value={createName}
            onChange={(e) => setCreateName(e.target.value)}
            fullWidth
            margin="normal"
            inputProps={{ maxLength: 100 }}
            placeholder="Например: Лучшие друзья"
          />
          <Box sx={{ mt: 2 }}>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 1 }}>
              Максимум участников: <b>{createMaxSize}</b>
            </Typography>
            <Slider
              value={createMaxSize}
              min={2}
              max={10}
              step={1}
              marks
              onChange={(_e, value) => setCreateMaxSize(value as number)}
            />
          </Box>
          {createError && <Alert severity="error" sx={{ mt: 1 }}>{createError}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)} disabled={createSubmitting} sx={{ textTransform: 'none' }}>Отмена</Button>
          <Button type="submit" variant="filled" disabled={createSubmitting}>
            {createSubmitting ? 'Создаём...' : 'Создать'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Join Dialog */}
      <Dialog
        open={joinOpen}
        onClose={() => !joinSubmitting && setJoinOpen(false)}
        fullWidth
        maxWidth="xs"
        PaperProps={{ component: 'form', onSubmit: handleJoin, noValidate: true }}
      >
        <DialogTitle>Войти по коду приглашения</DialogTitle>
        <DialogContent>
          <TextField
            label="Код или ссылка-приглашение"
            value={joinCode}
            onChange={(e) => {
              setJoinCode(e.target.value);
              if (joinError) setJoinError('');
            }}
            fullWidth
            margin="normal"
            inputProps={{ autoCapitalize: 'none', autoCorrect: 'off', spellCheck: false, enterKeyHint: 'go' }}
            placeholder="abc12345"
          />
          {joinError && <Alert severity="error" sx={{ mt: 1 }}>{joinError}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setJoinOpen(false)} disabled={joinSubmitting} sx={{ textTransform: 'none' }}>Отмена</Button>
          <Button type="submit" variant="filled" disabled={joinSubmitting}>
            {joinSubmitting ? 'Входим...' : 'Войти'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
