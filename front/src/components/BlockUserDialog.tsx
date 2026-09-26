import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import apiClient from '../api/client';
import type { BlockStatus } from '../types';

interface BlockUserDialogProps {
  userId: number;
  name: string | null;
  /** Current state: true — the dialog offers to unblock. */
  blockedByMe: boolean;
  onClose: () => void;
  onChanged: (status: BlockStatus) => void;
}

/** Confirms adding a user to (or removing from) the personal black list. */
export default function BlockUserDialog({ userId, name, blockedByMe, onClose, onChanged }: BlockUserDialogProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const who = name || 'этого человека';

  async function handleConfirm() {
    setSubmitting(true);
    setError('');
    try {
      const res = blockedByMe
        ? await apiClient.delete<BlockStatus>(`/users/${userId}/block`)
        : await apiClient.post<BlockStatus>(`/users/${userId}/block`);
      onChanged(res.data);
      onClose();
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(message ?? 'Не получилось, попробуйте ещё раз');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="xs">
      <DialogTitle>{blockedByMe ? `Разблокировать ${who}?` : `Заблокировать ${who}?`}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <Typography variant="body2" color="text.secondary">
          {blockedByMe
            ? 'Вы снова сможете переписываться, а JOIN снова сможет предлагать вас друг другу в напарники.'
            : 'Этот человек не сможет вам писать, JOIN больше не подберёт вас друг другу в напарники, и вы не окажетесь в одной компании. Уведомления о блокировке не будет.'}
        </Typography>
        {error && <Alert severity="error">{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} sx={{ textTransform: 'none' }}>Отмена</Button>
        <Button
          onClick={handleConfirm}
          color={blockedByMe ? 'primary' : 'error'}
          disabled={submitting}
          sx={{ textTransform: 'none' }}
        >
          {blockedByMe ? 'Разблокировать' : 'Заблокировать'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
