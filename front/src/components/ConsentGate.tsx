import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Link,
} from '@mui/material';
import apiClient from '../api/client';
import { PrivacyPolicyDialog } from './PrivacyPolicy';
import DeleteAccountDialog from './DeleteAccountDialog';

/**
 * Accounts created before sign-up asked for consent get it asked once here.
 * The dialog can't be dismissed: accept the policy or delete the account.
 */
export default function ConsentGate() {
  const [open, setOpen] = useState(false);
  const [policyOpen, setPolicyOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<{ personalDataConsent?: boolean }>('/users/me')
      .then((res) => {
        if (!cancelled && res.data.personalDataConsent === false) setOpen(true);
      })
      .catch(() => {
        // Auth problems are handled by the splash screen.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleAccept() {
    setSaving(true);
    setError('');
    try {
      await apiClient.post('/users/me/consent');
      setOpen(false);
    } catch {
      setError('Не получилось сохранить. Попробуйте ещё раз.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <Dialog open={open} disableEscapeKeyDown>
        <DialogTitle>Согласие на обработку данных</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Мы описали, какие данные хранит JOIN и зачем, в{' '}
            <Link component="button" type="button" onClick={() => setPolicyOpen(true)} sx={{ verticalAlign: 'baseline' }}>
              политике конфиденциальности
            </Link>
            . Чтобы пользоваться JOIN дальше, примите её. Если не согласны, аккаунт можно удалить.
          </DialogContentText>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        </DialogContent>
        <DialogActions sx={{ justifyContent: 'space-between' }}>
          <Button color="error" onClick={() => setDeleteOpen(true)} disabled={saving} sx={{ textTransform: 'none' }}>
            Удалить аккаунт
          </Button>
          <Button variant="filled" onClick={handleAccept} disabled={saving} sx={{ textTransform: 'none' }}>
            Принимаю
          </Button>
        </DialogActions>
      </Dialog>
      <PrivacyPolicyDialog open={policyOpen} onClose={() => setPolicyOpen(false)} />
      <DeleteAccountDialog open={deleteOpen} onClose={() => setDeleteOpen(false)} />
    </>
  );
}
