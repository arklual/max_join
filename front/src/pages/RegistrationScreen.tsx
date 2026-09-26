import { useState, useRef, type FormEvent, type ChangeEvent } from 'react';
import { useLocation, useNavigate } from 'react-router';
import {
  Container,
  Paper,
  Typography,
  Alert,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Button,
  Avatar,
  FormHelperText,
  Box,
  IconButton,
} from '@mui/material';
import { CloudUploadOutlined, DeleteOutline } from '@mui/icons-material';
import apiClient from '../api/client';
import { ALL_INTERESTS, INTEREST_LABELS, type InterestType } from '../types';
import CitySelect from '../components/CitySelect';
import { takePendingInvite } from '../utils/inviteLinks';
import { takePendingRoute } from '../utils/startTarget';
import { isMessengerApp, setAuthToken } from '../api/platform';
import UniversitySelect from '../components/UniversitySelect';
import LinkMaxCard from '../components/LinkMaxCard';
import CheckOutlined from '@mui/icons-material/CheckOutlined';
import { getTagChipSx } from '../components/tagChipStyles';

/** Where to go after sign-up: the invite or deep link that brought the user here. */
function postRegistrationTarget(): string {
  const invite = takePendingInvite();
  const route = takePendingRoute();
  if (invite) return `/friend-groups?invite=${invite}`;
  return route ?? '/afisha';
}

interface FormData {
  email: string;
  password: string;
  password2: string;
  city: string;
  name: string;
  gender: string;
  age: string;
  interests: InterestType[];
  photo: File | null;
  universityId: number | null;
  universityName: string;
}

interface FormErrors {
  email?: string;
  password?: string;
  password2?: string;
  city?: string;
  name?: string;
  gender?: string;
  age?: string;
  interests?: string;
  photo?: string;
}

const MAX_PHOTO_SIZE_BYTES = 10 * 1024 * 1024;

function validate(data: FormData, withCredentials: boolean): FormErrors {
  const errors: FormErrors = {};

  if (withCredentials) {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email.trim())) {
      errors.email = 'Введите корректный email';
    }
    if (data.password.length < 8) {
      errors.password = 'Пароль должен быть не короче 8 символов';
    }
    if (data.password2 !== data.password) {
      errors.password2 = 'Пароли не совпадают';
    }
  }

  if (!data.city.trim()) {
    errors.city = 'Введите город';
  }

  if (!data.name.trim()) {
    errors.name = 'Введите имя';
  }

  if (!data.gender) {
    errors.gender = 'Выберите пол';
  }

  if (!data.age.trim()) {
    errors.age = 'Введите возраст';
  } else {
    const ageNum = Number(data.age);
    if (!Number.isInteger(ageNum) || ageNum < 1 || ageNum > 150) {
      errors.age = 'Введите корректный возраст';
    } else if (withCredentials && ageNum < 14) {
      errors.age = 'Сервисом можно пользоваться с 14 лет';
    }
  }

  if (data.interests.length === 0) {
    errors.interests = 'Выберите хотя бы один интерес';
  }

  return errors;
}

export default function RegistrationScreen() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Came from "Войти через MAX" for a MAX account without a JOIN profile:
  // the profile is bound to that MAX account, no email / password needed.
  const maxLoginToken = (useLocation().state as { maxLoginToken?: string } | null)?.maxLoginToken ?? null;
  // Otherwise, outside a messenger (Android app, browser) the account uses email + password.
  const withCredentials = !isMessengerApp() && !maxLoginToken;

  const [formData, setFormData] = useState<FormData>({
    email: '',
    password: '',
    password2: '',
    city: '',
    name: '',
    gender: '',
    age: '',
    interests: [],
    photo: null,
    universityId: null,
    universityName: '',
  });

  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);

  function handleInputChange(e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    setErrors((prev) => ({ ...prev, [name]: undefined }));
    setServerError('');
  }

  function handleInterestToggle(interest: InterestType) {
    setFormData((prev) => {
      const exists = prev.interests.includes(interest);
      const updated = exists
        ? prev.interests.filter((i) => i !== interest)
        : [...prev.interests, interest];
      return { ...prev, interests: updated };
    });
    setErrors((prev) => ({ ...prev, interests: undefined }));
  }

  function handlePhotoChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;

    if (file && file.size > MAX_PHOTO_SIZE_BYTES) {
      setFormData((prev) => ({ ...prev, photo: null }));
      setErrors((prev) => ({ ...prev, photo: 'Фото должно быть не больше 10 МБ.' }));
      setServerError('');
      if (photoPreview) {
        URL.revokeObjectURL(photoPreview);
      }
      setPhotoPreview(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    setFormData((prev) => ({ ...prev, photo: file }));
    setErrors((prev) => ({ ...prev, photo: undefined }));
    setServerError('');

    if (photoPreview) {
      URL.revokeObjectURL(photoPreview);
    }

    if (file) {
      setPhotoPreview(URL.createObjectURL(file));
    } else {
      setPhotoPreview(null);
    }
  }

  function handlePhotoPickerOpen() {
    fileInputRef.current?.click();
  }

  function handleRemovePhoto() {
    setFormData((prev) => ({ ...prev, photo: null }));
    if (photoPreview) {
      URL.revokeObjectURL(photoPreview);
    }
    setPhotoPreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  async function uploadPhotoIfAny() {
    if (!formData.photo) return;
    const photo = new FormData();
    photo.append('photo', formData.photo);
    try {
      await apiClient.post('/users/me/photo', photo, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    } catch {
      // Account exists already — the photo can be re-added from the profile.
    }
  }

  async function registerWithMaxLogin(token: string) {
    const res = await apiClient.post<{ token: string }>(`/auth/max-login/${token}/register`, {
      firstName: formData.name.trim(),
      age: Number(formData.age),
      gender: formData.gender,
      city: formData.city.trim(),
      universityId: formData.universityId,
      interests: formData.interests,
    });
    setAuthToken(res.data.token);
    await uploadPhotoIfAny();
  }

  async function registerWithCredentials() {
    const res = await apiClient.post<{ token: string }>('/auth/register', {
      email: formData.email.trim(),
      password: formData.password,
      firstName: formData.name.trim(),
      age: Number(formData.age),
      gender: formData.gender,
      city: formData.city.trim(),
      universityId: formData.universityId,
      interests: formData.interests,
    });
    setAuthToken(res.data.token);
    await uploadPhotoIfAny();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setServerError('');

    const validationErrors = validate(formData, withCredentials);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setSubmitting(true);

    try {
      if (maxLoginToken) {
        await registerWithMaxLogin(maxLoginToken);
        navigate(postRegistrationTarget(), { replace: true });
        return;
      }
      if (withCredentials) {
        await registerWithCredentials();
        navigate(postRegistrationTarget(), { replace: true });
        return;
      }

      const payload = new FormData();
      payload.append('city', formData.city.trim());
      payload.append('name', formData.name.trim());
      payload.append('gender', formData.gender);
      payload.append('age', formData.age);
      formData.interests.forEach((interest) => {
        payload.append('interests', interest);
      });
      if (formData.universityId !== null) {
        payload.append('universityId', String(formData.universityId));
      }
      if (formData.photo) {
        payload.append('photo', formData.photo);
      }

      await apiClient.post('/users/register', payload, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      navigate(postRegistrationTarget(), { replace: true });
    } catch (error: unknown) {
      const message = extractErrorMessage(error);
      setServerError(message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 4,
          bgcolor: 'surface.main',
          animation: 'scaleIn 0.4s ease-out',
        }}
      >
        <Typography
          variant="h5"
          sx={{
            fontWeight: 'bold',
            mb: 3,
            color: 'onSurface.main',
            textAlign: 'center',
          }}
        >
          Регистрация
        </Typography>

        {serverError && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {serverError}
          </Alert>
        )}

        <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {withCredentials && (
            <>
              <TextField
                id="reg-email"
                label="Email"
                name="email"
                type="email"
                value={formData.email}
                onChange={handleInputChange}
                error={!!errors.email}
                helperText={errors.email ?? 'Понадобится для входа'}
                autoComplete="email"
                inputProps={{ inputMode: 'email', autoCapitalize: 'none' }}
                fullWidth
              />
              <TextField
                id="reg-password"
                label="Пароль"
                name="password"
                type="password"
                value={formData.password}
                onChange={handleInputChange}
                error={!!errors.password}
                helperText={errors.password ?? 'Не короче 8 символов'}
                autoComplete="new-password"
                fullWidth
              />
              <TextField
                id="reg-password2"
                label="Повторите пароль"
                name="password2"
                type="password"
                value={formData.password2}
                onChange={handleInputChange}
                error={!!errors.password2}
                helperText={errors.password2}
                autoComplete="new-password"
                fullWidth
              />
            </>
          )}

          {/* City */}
          <Box>
            <Typography variant="body2" sx={{ mb: 0.5, color: 'onSurfaceVariant.main' }}>
              Город
            </Typography>
            <CitySelect
              id="reg-city"
              value={formData.city}
              onChange={(city) => {
                setFormData((prev) => ({ ...prev, city, universityId: null, universityName: '' }));
                setErrors((prev) => ({ ...prev, city: undefined }));
                setServerError('');
              }}
              error={errors.city}
            />
          </Box>

          {/* University */}
          <Box>
            <Typography variant="body2" sx={{ mb: 0.5, color: 'onSurfaceVariant.main' }}>
              ВУЗ (необязательно)
            </Typography>
            <UniversitySelect
              id="reg-university"
              value={formData.universityName}
              universityId={formData.universityId}
              onChange={(universityName, universityId) => {
                setFormData((prev) => ({ ...prev, universityName, universityId }));
              }}
              city={formData.city}
            />
          </Box>

          {/* Name */}
          <TextField
            id="reg-name"
            label="Имя"
            name="name"
            placeholder="Ваше имя"
            value={formData.name}
            onChange={handleInputChange}
            autoComplete="given-name"
            error={!!errors.name}
            helperText={errors.name}
            fullWidth
          />

          {/* Gender */}
          <FormControl fullWidth error={!!errors.gender}>
            <InputLabel id="reg-gender-label">Пол</InputLabel>
            <Select
              labelId="reg-gender-label"
              id="reg-gender"
              name="gender"
              value={formData.gender}
              label="Пол"
              onChange={(e) => {
                setFormData((prev) => ({ ...prev, gender: e.target.value }));
                setErrors((prev) => ({ ...prev, gender: undefined }));
                setServerError('');
              }}
            >
              <MenuItem value="MALE">Мужской</MenuItem>
              <MenuItem value="FEMALE">Женский</MenuItem>
            </Select>
            {errors.gender && <FormHelperText>{errors.gender}</FormHelperText>}
          </FormControl>

          {/* Age */}
          <TextField
            id="reg-age"
            label="Возраст"
            name="age"
            type="number"
            placeholder="25"
            inputProps={{ min: 1, max: 150, inputMode: 'numeric' }}
            value={formData.age}
            onChange={handleInputChange}
            error={!!errors.age}
            helperText={errors.age}
            fullWidth
          />

          {/* Interests */}
          <Box>
            <Typography variant="body2" sx={{ mb: 1, color: 'onSurfaceVariant.main' }}>
              Интересы
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {ALL_INTERESTS.map((interest) => {
                const selected = formData.interests.includes(interest);
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
            {errors.interests && <FormHelperText error>{errors.interests}</FormHelperText>}
          </Box>

          {/* Photo */}
          <Box>
            <Typography variant="body2" sx={{ mb: 1, color: 'onSurfaceVariant.main' }}>
              Фото (необязательно)
            </Typography>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handlePhotoChange}
              hidden
            />
            <Box
              sx={{
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
                alignItems: 'center',
                gap: 2,
                p: 2,
                borderRadius: 3,
                bgcolor: 'surfaceContainerLow.main',
              }}
            >
              <Avatar
                src={photoPreview ?? undefined}
                onClick={handlePhotoPickerOpen}
                sx={{
                  width: 72,
                  height: 72,
                  cursor: 'pointer',
                  bgcolor: 'primaryContainer.main',
                  color: 'onPrimaryContainer.main',
                  fontSize: 28,
                }}
              >
                {!photoPreview && '+'}
              </Avatar>
              <Box sx={{ flex: 1, textAlign: { xs: 'center', sm: 'left' } }}>
                <Typography variant="body2" sx={{ fontWeight: 500, color: 'onSurface.main' }}>
                  {photoPreview ? 'Фото профиля выбрано' : 'Загрузите аватар'}
                </Typography>
                <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main' }}>
                  {photoPreview
                    ? 'Нажмите на фото, чтобы заменить его.'
                    : 'Крупное фото лица работает лучше всего.'}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', flexDirection: { xs: 'row', sm: 'column' }, gap: 0.5 }}>
                <Button
                  variant="tonal"
                  size="small"
                  startIcon={<CloudUploadOutlined />}
                  onClick={handlePhotoPickerOpen}
                >
                  {photoPreview ? 'Заменить' : 'Выбрать'}
                </Button>
                {photoPreview && (
                  <IconButton
                    onClick={handleRemovePhoto}
                    aria-label="Удалить фото"
                    sx={{ color: 'error.main' }}
                  >
                    <DeleteOutline fontSize="small" />
                  </IconButton>
                )}
              </Box>
            </Box>
            {errors.photo && <FormHelperText error>{errors.photo}</FormHelperText>}
          </Box>

          {/* Submit */}
          <Button
            type="submit"
            variant="filled"
            size="large"
            disabled={submitting}
            fullWidth
            sx={{
              mt: 1,
              transition: 'transform 0.15s ease',
              '&:active': { transform: 'scale(0.97)' },
            }}
          >
            {submitting ? 'Отправка...' : 'Зарегистрироваться'}
          </Button>
        </Box>

        {maxLoginToken ? (
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center', mt: 2 }}>
            Профиль будет привязан к вашему аккаунту MAX — пароль не нужен.
          </Typography>
        ) : withCredentials ? (
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5, mt: 2, flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
              Уже есть аккаунт?
            </Typography>
            <Button variant="text" onClick={() => navigate('/login')} sx={{ textTransform: 'none', fontWeight: 600 }}>
              Войти
            </Button>
          </Box>
        ) : (
          <LinkMaxCard onLinked={() => navigate(postRegistrationTarget(), { replace: true })} />
        )}
      </Paper>
    </Container>
  );
}

function extractErrorMessage(error: unknown): string {
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error
  ) {
    const resp = (error as { response?: { data?: { message?: string; error?: string; code?: string } } }).response;
    if (resp?.data?.code === 'EMAIL_TAKEN') {
      return 'Этот email уже зарегистрирован. Войдите или используйте другой.';
    }
    if (resp?.data?.message) {
      return resp.data.message;
    }
    if (resp?.data?.error) {
      return resp.data.error;
    }
  }
  return 'Произошла ошибка при регистрации. Попробуйте ещё раз.';
}
