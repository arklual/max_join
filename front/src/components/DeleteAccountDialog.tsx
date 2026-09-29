import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';
import apiClient from '../api/client';
import { setAuthToken } from '../api/platform';
import { rememberProfileAge } from '../utils/pushkin';

/** Deletes the account for good — this is also how consent to data processing is withdrawn. */
export default function DeleteAccountDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');

  async function handleDelete() {
    setDeleting(true);
    setError('');
    try {
      await apiClient.delete('/users/me');
      setAuthToken(null);
      rememberProfileAge(null);
      // The splash screen sends a MAX user to sign-up and a browser user to the login screen.
      navigate('/', { replace: true });
    } catch {
      setError('Не получилось удалить аккаунт. Попробуйте ещё раз или напишите в поддержку.');
      setDeleting(false);
    }
  }

  function handleClose() {
    if (deleting) return;
    setError('');
    onClose();
  }

  return (
    <Dialog open={open} onClose={handleClose}>
      <DialogTitle>Удалить аккаунт?</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Удалятся профиль, сохранённые события, совпадения, личные чаты с сообщениями и созданные вами
          компании. Из компаний друзей вы выйдете. Отменить это нельзя.
        </DialogContentText>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={deleting} sx={{ textTransform: 'none' }}>
          Отмена
        </Button>
        <Button color="error" onClick={handleDelete} disabled={deleting} sx={{ textTransform: 'none' }}>
          {deleting ? 'Удаляем...' : 'Удалить'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
