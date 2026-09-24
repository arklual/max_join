import { useState, useEffect, useCallback, useRef, useContext, type ChangeEvent, type FormEvent } from 'react';
import {
  Box,
  Typography,
  Avatar,
  Card,
  List,
  ListItem,
  ListItemText,
  Chip,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  Snackbar,
  CircularProgress,
  IconButton,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Link,
  ToggleButton,
  ToggleButtonGroup,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import { CameraAltOutlined, InfoOutlined, ExpandMore, TuneOutlined } from '@mui/icons-material';
import { useNavigate } from 'react-router';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { openExternalLink } from '../api/maxBridge';
import { telegramUrl } from '../utils/format';
import LinkifiedText from '../components/LinkifiedText';
import { isMaxApp, isMessengerApp, setAuthToken } from '../api/platform';
import MaxLinkCard from '../components/MaxLinkCard';
import PushkinCardInfo from '../components/PushkinCardInfo';
import { ThemeModeContext } from '../theme/M3/providers/ThemeModeProvider';
import SettingsBrightnessOutlined from '@mui/icons-material/SettingsBrightnessOutlined';
import LightModeOutlined from '@mui/icons-material/LightModeOutlined';
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined';
import LogoutOutlined from '@mui/icons-material/LogoutOutlined';
import { ALL_INTERESTS, INTEREST_LABELS, type InterestType } from '../types';
import type { UserProfile } from '../types';
import CitySelect from '../components/CitySelect';
import UniversitySelect from '../components/UniversitySelect';
import { LinkEmailCard } from '../components/LinkEmailCard';
import CheckOutlined from '@mui/icons-material/CheckOutlined';
import { getTagChipSx } from '../components/tagChipStyles';

const GENDER_LABELS: Record<string, string> = {
  MALE: 'Мужской',
  FEMALE: 'Женский',
  male: 'Мужской',
  female: 'Женский',
};

interface FormErrors {
  firstName?: string;
  city?: string;
  age?: string;
  interests?: string;
}

function validateProfile(data: UserProfile): FormErrors {
  const errors: FormErrors = {};

  if (!data.firstName.trim()) {
    errors.firstName = 'Введите имя';
  }

  if (!data.city.trim()) {
    errors.city = 'Введите город';
  }

  if (!data.age || data.age < 1 || data.age > 150) {
    errors.age = 'Введите корректный возраст';
  }

  if (data.interests.length === 0) {
    errors.interests = 'Выберите хотя бы один интерес';
  }

  return errors;
}

export default function ProfileScreen() {
  const { preference: themePreference, setPreference: setThemePreference } = useContext(ThemeModeContext);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editMode, setEditMode] = useState(false);
  const [editData, setEditData] = useState<UserProfile | null>(null);
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState('');
  const [toast, setToast] = useState('');
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [photoFile, setPhotoFile] = useState<File | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  function handleLogout() {
    setLogoutOpen(false);
    setAuthToken(null);
    navigate('/login', { replace: true });
  }

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError('');

    try {
      const response = await apiClient.get<UserProfile>('/users/me/profile');
      setProfile(response.data);
    } catch {
      setError('Не удалось загрузить профиль.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  function showToast(message: string) {
    setToast(message);
  }

  function handleStartEdit() {
    if (!profile) {
      return;
    }
    setEditData({ ...profile, interests: [...profile.interests] });
    setPhotoPreview(null);
    setPhotoFile(null);
    setFormErrors({});
    setServerError('');
    setEditMode(true);
  }

  function handleCancelEdit() {
    setEditMode(false);
    setEditData(null);
    setFormErrors({});
    setServerError('');
    if (photoPreview) {
      URL.revokeObjectURL(photoPreview);
    }
    setPhotoPreview(null);
    setPhotoFile(null);
  }

  function handleFieldChange(e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) {
    if (!editData) {
      return;
    }
    const { name, value } = e.target;
    setEditData({ ...editData, [name]: name === 'age' ? Number(value) || 0 : value });
    setFormErrors((prev) => ({ ...prev, [name]: undefined }));
    setServerError('');
  }

  function handleInterestToggle(interest: InterestType) {
    if (!editData) {
      return;
    }
    const exists = editData.interests.includes(interest);
    const updated = exists
      ? editData.interests.filter((i) => i !== interest)
      : [...editData.interests, interest];
    setEditData({ ...editData, interests: updated });
    setFormErrors((prev) => ({ ...prev, interests: undefined }));
  }

  function handlePhotoSelect(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (photoPreview) {
      URL.revokeObjectURL(photoPreview);
    }
    if (file) {
      setPhotoFile(file);
      setPhotoPreview(URL.createObjectURL(file));
    } else {
      setPhotoFile(null);
      setPhotoPreview(null);
    }
  }

  function handlePhotoClick() {
    fileInputRef.current?.click();
  }

  async function handleSave(e: FormEvent) {
    e.preventDefault();

    if (!editData) {
      return;
    }

    setServerError('');
    const errors = validateProfile(editData);
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors);
      return;
    }

    setSaving(true);

    try {
      if (photoFile) {
        const photoFormData = new FormData();
        photoFormData.append('photo', photoFile);
        await apiClient.post('/users/me/photo', photoFormData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      }

      const response = await apiClient.put<UserProfile>('/users/me/profile', {
        city: editData.city.trim(),
        firstName: editData.firstName.trim(),
        lastName: editData.lastName?.trim() || null,
        gender: editData.gender,
        age: editData.age,
        interests: editData.interests,
        telegramChannel: editData.telegramChannel?.trim() || null,
        status: editData.status?.trim() || null,
        bio: editData.bio?.trim() || null,
        universityId: editData.universityId ?? null,
      });

      setProfile(response.data);
      setEditMode(false);
      setEditData(null);
      if (photoPreview) {
        URL.revokeObjectURL(photoPreview);
      }
      setPhotoPreview(null);
      setPhotoFile(null);
      showToast('Профиль сохранен!');
    } catch (err: unknown) {
      const message = extractErrorMessage(err);
      setServerError(message);
    } finally {
      setSaving(false);
    }
  }

  // ===== Loading State =====
  if (loading) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          p: 6,
          minHeight: 'calc(100vh - var(--tab-bar-height, 0px) - var(--safe-area-bottom, 0px))',
        }}
      >
        <CircularProgress size={32} sx={{ mb: 1.5 }} />
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
          Загрузка профиля...
        </Typography>
      </Box>
    );
  }

  // ===== Error State =====
  if (error && !profile) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          p: 6,
          minHeight: 'calc(100vh - var(--tab-bar-height, 0px) - var(--safe-area-bottom, 0px))',
        }}
      >
        <Alert severity="error" sx={{ mb: 2, width: '100%', maxWidth: 400 }}>
          {error}
        </Alert>
        <Button variant="outlined" onClick={loadProfile} sx={{ borderRadius: 5, px: 3 }}>
          Повторить
        </Button>
      </Box>
    );
  }

  if (!profile) {
    return null;
  }

  const displayPhoto = photoPreview ?? mediaUrl(profile.photo) ?? null;

  // ===== Edit Mode =====
  if (editMode && editData) {
    return (
      <Box sx={{ p: 2, maxWidth: 480, mx: 'auto' }}>
        <Snackbar
          open={!!toast}
          autoHideDuration={3000}
          onClose={() => setToast('')}
          anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
        >
          <Alert severity="success" onClose={() => setToast('')} sx={{ width: '100%' }}>
            {toast}
          </Alert>
        </Snackbar>

        <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
          Редактирование
        </Typography>

        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}

        <Box component="form" onSubmit={handleSave} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* Photo Section */}
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, mb: 1 }}>
            <Box sx={{ position: 'relative' }}>
              <Avatar
                src={displayPhoto ?? undefined}
                sx={{
                  width: 96,
                  height: 96,
                  fontSize: '2.5rem',
                  fontWeight: 600,
                  bgcolor: 'primaryContainer.main',
                  color: 'onPrimaryContainer.main',
                }}
              >
                {editData.firstName.charAt(0).toUpperCase()}
              </Avatar>
              <IconButton
                onClick={handlePhotoClick}
                aria-label="Сменить фото"
                sx={{
                  position: 'absolute',
                  bottom: -6,
                  right: -6,
                  width: 40,
                  height: 40,
                  bgcolor: 'primary.main',
                  color: 'onPrimary.main',
                  border: 2,
                  borderColor: 'surface.main',
                  '&:hover': { bgcolor: 'primary.dark' },
                }}
              >
                <CameraAltOutlined sx={{ fontSize: 20 }} />
              </IconButton>
            </Box>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={handlePhotoSelect}
            />
          </Box>

          <TextField
            label="Имя"
            name="firstName"
            value={editData.firstName}
            onChange={handleFieldChange}
            error={!!formErrors.firstName}
            helperText={formErrors.firstName}
            autoComplete="given-name"
            fullWidth
            size="small"
          />

          <TextField
            label="Фамилия"
            name="lastName"
            value={editData.lastName ?? ''}
            onChange={handleFieldChange}
            autoComplete="family-name"
            fullWidth
            size="small"
          />

          <Box>
            <CitySelect
              id="prof-city"
              label="Город"
              value={editData.city}
              onChange={(city) => {
                setEditData((prev) =>
                  prev ? { ...prev, city, universityId: null, universityName: '' } : prev
                );
                setFormErrors((prev) => ({ ...prev, city: undefined }));
                setServerError('');
              }}
              error={formErrors.city}
            />
          </Box>

          <Box>
            <UniversitySelect
              id="prof-university"
              label="ВУЗ (необязательно)"
              value={editData.universityName ?? ''}
              universityId={editData.universityId ?? null}
              onChange={(universityName, universityId) => {
                setEditData((prev) =>
                  prev ? { ...prev, universityName, universityId } : prev
                );
              }}
              city={editData.city}
            />
          </Box>

          <FormControl fullWidth size="small">
            <InputLabel>Пол</InputLabel>
            <Select
              name="gender"
              value={editData.gender}
              label="Пол"
              onChange={(e) => {
                if (!editData) return;
                setEditData({ ...editData, gender: e.target.value });
                setServerError('');
              }}
            >
              <MenuItem value="" disabled>Выберите пол</MenuItem>
              <MenuItem value="MALE">Мужской</MenuItem>
              <MenuItem value="FEMALE">Женский</MenuItem>
            </Select>
          </FormControl>

          <TextField
            label="Возраст"
            name="age"
            type="number"
            inputProps={{ min: 1, max: 150, inputMode: 'numeric' }}
            value={editData.age || ''}
            onChange={handleFieldChange}
            error={!!formErrors.age}
            helperText={formErrors.age}
            fullWidth
            size="small"
          />

          {/* Interests */}
          <Box>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 1 }}>
              Интересы
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
              {ALL_INTERESTS.map((interest) => {
                const selected = editData.interests.includes(interest);
                return (
                  <Chip
                    key={interest}
                    label={INTEREST_LABELS[interest]}
                    clickable
                    aria-pressed={selected}
                    icon={selected ? <CheckOutlined sx={{ fontSize: 16 }} /> : undefined}
                    onClick={() => handleInterestToggle(interest)}
                    sx={{ ...getTagChipSx(interest, { selected, clickable: true }), '& .MuiChip-icon': { color: 'inherit', ml: 0.75, mr: -0.5 } }}
                  />
                );
              })}
            </Box>
            {formErrors.interests && (
              <Typography variant="caption" sx={{ color: 'error.main', mt: 0.5, display: 'block' }}>
                {formErrors.interests}
              </Typography>
            )}
          </Box>

          <TextField
            label="Telegram канал"
            name="telegramChannel"
            placeholder="@channel"
            helperText="@username или ссылка t.me/…"
            value={editData.telegramChannel ?? ''}
            onChange={handleFieldChange}
            fullWidth
            size="small"
          />

          <TextField
            label="Статус"
            name="status"
            placeholder="Ваш статус"
            value={editData.status ?? ''}
            onChange={handleFieldChange}
            fullWidth
            size="small"
          />

          <TextField
            label="О себе"
            name="bio"
            placeholder="Расскажите о себе"
            value={editData.bio ?? ''}
            onChange={handleFieldChange}
            multiline
            rows={3}
            fullWidth
            size="small"
          />

          {/* Actions */}
          <Box sx={{ display: 'flex', gap: 1.5, mt: 1 }}>
            <Button
              variant="outlined"
              onClick={handleCancelEdit}
              sx={{ flex: 1, borderRadius: 3, py: 1.5, textTransform: 'none', fontWeight: 600, fontSize: '1rem' }}
            >
              Отмена
            </Button>
            <Button
              type="submit"
              variant="filled"
              disabled={saving}
              sx={{ flex: 1, borderRadius: 3, py: 1.5, textTransform: 'none', fontWeight: 600, fontSize: '1rem' }}
            >
              {saving ? 'Сохранение...' : 'Сохранить'}
            </Button>
          </Box>
        </Box>
      </Box>
    );
  }

  // ===== View Mode =====
  return (
    <Box sx={{ p: 2, maxWidth: 480, mx: 'auto' }}>
      <Snackbar
        open={!!toast}
        autoHideDuration={3000}
        onClose={() => setToast('')}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setToast('')} sx={{ width: '100%' }}>
          {toast}
        </Alert>
      </Snackbar>

      {/* Profile Header */}
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 3, animation: 'fadeInUp 0.4s ease-out' }}>
        <Avatar
          src={mediaUrl(profile.photo)}
          sx={{
            width: 96,
            height: 96,
            fontSize: '2.5rem',
            fontWeight: 600,
            mb: 1.5,
            bgcolor: 'primaryContainer.main',
            color: 'onPrimaryContainer.main',
          }}
        >
          {profile.firstName.charAt(0).toUpperCase()}
        </Avatar>
        <Typography variant="h5" sx={{ fontWeight: 700, textAlign: 'center' }}>
          {profile.firstName}{profile.lastName ? ` ${profile.lastName}` : ''}
        </Typography>
        {profile.status && (
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mt: 0.5, textAlign: 'center' }}>
            {profile.status}
          </Typography>
        )}
      </Box>

      {/* Info Section */}
      <Card variant="outlined" sx={{ mb: 3, borderRadius: 3, borderColor: 'outlineVariant.main', animation: 'fadeInUp 0.4s ease-out both', animationDelay: '0.1s' }}>
        <List disablePadding>
          <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
            <ListItemText
              primary="Email"
              secondary={
                profile.email || (
                  <Typography component="span" variant="body2" sx={{ fontStyle: 'italic', color: 'onSurfaceVariant.main' }}>
                    Не указан — можно привязать ниже, в «Вход с других устройств»
                  </Typography>
                )
              }
              primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }}
              secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }}
            />
          </ListItem>
          <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
            <ListItemText
              primary="Город"
              secondary={profile.city}
              primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }}
              secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }}
            />
          </ListItem>
          {profile.universityName && (
            <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
              <ListItemText
                primary="ВУЗ"
                secondary={profile.universityName}
                primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }}
                secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }}
              />
            </ListItem>
          )}
          <ListItem divider sx={{ borderColor: 'outlineVariant.main' }}>
            <ListItemText
              primary="Пол"
              secondary={GENDER_LABELS[profile.gender] ?? profile.gender}
              primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }}
              secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }}
            />
          </ListItem>
          <ListItem divider={!!profile.telegramChannel} sx={{ borderColor: 'outlineVariant.main' }}>
            <ListItemText
              primary="Возраст"
              secondary={profile.age}
              primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }}
              secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }}
            />
          </ListItem>
          {profile.telegramChannel && (
            <ListItem>
              <ListItemText
                primary="Telegram"
                secondary={
                  telegramUrl(profile.telegramChannel) ? (
                    <Link
                      component="button"
                      type="button"
                      variant="body2"
                      onClick={() => openExternalLink(telegramUrl(profile.telegramChannel)!)}
                      sx={{ textAlign: 'left', overflowWrap: 'anywhere' }}
                    >
                      {profile.telegramChannel}
                    </Link>
                  ) : (
                    profile.telegramChannel
                  )
                }
                primaryTypographyProps={{ variant: 'caption', color: 'onSurfaceVariant.main' }}
                secondaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', sx: { overflowWrap: 'anywhere' } }}
              />
            </ListItem>
          )}
        </List>
      </Card>

      {/* Bio Section */}
      {profile.bio && (
        <Card variant="outlined" sx={{ mb: 3, borderRadius: 3, p: 2, borderColor: 'outlineVariant.main', animation: 'fadeInUp 0.4s ease-out both', animationDelay: '0.15s' }}>
          <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', display: 'block', mb: 0.5 }}>
            О себе
          </Typography>
          <Typography variant="body2" sx={{ lineHeight: 1.5, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
            <LinkifiedText text={profile.bio} />
          </Typography>
        </Card>
      )}

      {/* Interests */}
      {profile.interests.length > 0 && (
        <Box sx={{ mb: 3, animation: 'fadeInUp 0.4s ease-out both', animationDelay: '0.2s' }}>
          <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', display: 'block', mb: 1 }}>
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

      {/* Pushkin card: ages 14–22 get 5 000 ₽ a year for culture — lives in MAX */}
      {profile.age >= 14 && profile.age <= 22 && (
        <Box sx={{ mb: 2 }}>
          <PushkinCardInfo variant="promo" />
        </Box>
      )}

      {/* One-tap MAX linking (outside MAX): notifications + same account in MAX */}
      {!isMaxApp() && (
        <Box sx={{ mb: 2 }}>
          <MaxLinkCard maxId={profile.maxId} onLinked={() => loadProfile()} />
        </Box>
      )}

      {/* Link email / password for mobile sign-in */}
      <Box sx={{ mb: 3 }}>
        <LinkEmailCard
          email={profile.hasPassword ? profile.email ?? null : null}
          defaultEmail={profile.email ?? ''}
          onLinked={() => loadProfile()}
        />
      </Box>

      {/* Edit Button */}
      <Button
        variant="filled"
        fullWidth
        onClick={handleStartEdit}
        sx={{
          borderRadius: 3,
          py: 1.5,
          textTransform: 'none',
          fontWeight: 600,
          fontSize: '1rem',
          animation: 'fadeInUp 0.4s ease-out both',
          animationDelay: '0.25s',
          transition: 'transform 0.15s ease',
          '&:active': { transform: 'scale(0.97)' },
        }}
      >
        Редактировать
      </Button>

      {/* Search Preferences Button */}
      <Button
        variant="outlined"
        fullWidth
        onClick={() => navigate('/search-preferences')}
        startIcon={<TuneOutlined />}
        sx={{
          mt: 1.5,
          borderRadius: 3,
          py: 1.5,
          textTransform: 'none',
          fontWeight: 600,
          fontSize: '1rem',
          animation: 'fadeInUp 0.4s ease-out both',
          animationDelay: '0.3s',
          transition: 'transform 0.15s ease',
          '&:active': { transform: 'scale(0.97)' },
        }}
      >
        Настройки поиска
      </Button>

      {/* Appearance */}
      <Box sx={{ mt: 2.5 }}>
        <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', display: 'block', mb: 1 }}>
          Тема оформления
        </Typography>
        <ToggleButtonGroup
          exclusive
          fullWidth
          size="small"
          value={themePreference}
          onChange={(_e, value) => value && setThemePreference(value)}
          aria-label="Тема оформления"
        >
          <ToggleButton value="system" sx={{ textTransform: 'none', gap: 0.5 }}>
            <SettingsBrightnessOutlined fontSize="small" /> Системная
          </ToggleButton>
          <ToggleButton value="light" sx={{ textTransform: 'none', gap: 0.5 }}>
            <LightModeOutlined fontSize="small" /> Светлая
          </ToggleButton>
          <ToggleButton value="dark" sx={{ textTransform: 'none', gap: 0.5 }}>
            <DarkModeOutlined fontSize="small" /> Тёмная
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {/* How to use JOIN */}
      <Accordion
        variant="outlined"
        sx={{ mt: 2, borderRadius: '12px !important', '&:before': { display: 'none' } }}
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
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
                Как найти компанию 1 на 1
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.6 }}>
                1. На вкладке «Афиша» листайте мероприятия и нажимайте «❤️» на тех, куда хотите пойти.<br />
                2. Если кто-то ещё лайкнул то же мероприятие и совпадает по вашим фильтрам (пол, возраст, ВУЗ) —
                автоматически создастся метч.<br />
                3. Уведомление о метче придёт в колокольчик в правом верхнем углу. Откройте уведомление,
                посмотрите профиль и начните чат, если совпадает по интересам.
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
                Группы друзей — ходить компанией
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.6 }}>
                Это отдельная фича — позволяет собрать своих друзей в одну группу и искать вместе.<br />
                <br />
                <b>Как это работает:</b><br />
                1. Создайте «Группу друзей» (раздел «Группы» → «Группы друзей» → «Создать»).<br />
                2. Поделитесь с друзьями кодом приглашения или QR-кодом — они присоединятся в эту же группу.<br />
                3. Каждый из вас лайкает мероприятия в Афише.<br />
                4. В разделе группы появится список <b>общих лайков</b> — то, куда хотят все. Это и есть готовые
                варианты, чтобы пойти всей компанией.<br />
                5. У группы свой чат: обсуждайте идеи и договаривайтесь о встрече.
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
                Открытые группы на мероприятие
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.6 }}>
                На странице мероприятия можно создать или вступить в «открытый сбор» — публичную группу,
                куда смогут присоединяться незнакомые люди, тоже идущие на это мероприятие. У сбора свой чат.
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
                Фильтры
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.6 }}>
                В Афише под поиском есть две кнопки: «Афиша» — фильтр мероприятий (теги, дата, цена и пр.)
                и «Кого ищу» — фильтр людей (пол, возраст, ВУЗ), который задаёт ограничения для метчей.
                Без него вы будете метчиться со всеми, кто лайкнул то же мероприятие.
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
                Лимиты и правила
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', lineHeight: 1.6 }}>
                В сутки можно поставить до 10 лайков. Если вы убрали лайк после метча — чат метча удаляется
                (вас предупредят).
              </Typography>
            </Box>
          </Box>
        </AccordionDetails>
      </Accordion>

      {!isMessengerApp() && (
        <Button
          variant="text"
          color="error"
          fullWidth
          startIcon={<LogoutOutlined />}
          onClick={() => setLogoutOpen(true)}
          sx={{ mt: 2, borderRadius: 3, py: 1.25, textTransform: 'none', fontWeight: 600 }}
        >
          Выйти из аккаунта
        </Button>
      )}

      <Dialog open={logoutOpen} onClose={() => setLogoutOpen(false)}>
        <DialogTitle>Выйти из аккаунта?</DialogTitle>
        <DialogContent>
          <DialogContentText>Чтобы вернуться, понадобятся email и пароль.</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLogoutOpen(false)} sx={{ textTransform: 'none' }}>Отмена</Button>
          <Button color="error" onClick={handleLogout} sx={{ textTransform: 'none' }}>Выйти</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function extractErrorMessage(error: unknown): string {
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error
  ) {
    const resp = (error as { response?: { data?: { message?: string } } }).response;
    if (resp?.data?.message) {
      return resp.data.message;
    }
  }
  return 'Произошла ошибка. Попробуйте ещё раз.';
}
