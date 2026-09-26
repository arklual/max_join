import { useEffect, useState } from 'react';
import Card from '@mui/material/Card';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import BlockUserDialog from './BlockUserDialog';
import type { BlockedUser } from '../types';

/** Profile section with the personal black list; hidden while it's empty. */
export default function BlockedUsersCard() {
  const [blocked, setBlocked] = useState<BlockedUser[]>([]);
  const [unblocking, setUnblocking] = useState<BlockedUser | null>(null);

  useEffect(() => {
    apiClient
      .get<BlockedUser[]>('/users/me/blocked')
      .then((res) => setBlocked(res.data ?? []))
      .catch(() => {});
  }, []);

  if (blocked.length === 0) return null;

  return (
    <Card variant="outlined" sx={{ borderRadius: 3, p: 2, borderColor: 'outlineVariant.main' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
        <BlockOutlined fontSize="small" sx={{ color: 'onSurfaceVariant.main' }} />
        <Typography variant="subtitle2">Чёрный список</Typography>
      </Box>
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
        {blocked.map((u) => (
          <Box component="li" key={u.userId} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Avatar src={mediaUrl(u.photo)} alt={u.firstName ?? ''} sx={{ width: 32, height: 32 }}>
              {u.firstName?.charAt(0)}
            </Avatar>
            <Typography variant="body2" sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>
              {u.firstName ?? 'Пользователь'}
            </Typography>
            <Button size="small" onClick={() => setUnblocking(u)} sx={{ textTransform: 'none', flexShrink: 0 }}>
              Разблокировать
            </Button>
          </Box>
        ))}
      </Box>

      {unblocking && (
        <BlockUserDialog
          userId={unblocking.userId}
          name={unblocking.firstName}
          blockedByMe
          onClose={() => setUnblocking(null)}
          onChanged={(status) => {
            if (!status.blockedByMe) setBlocked((prev) => prev.filter((u) => u.userId !== status.userId));
          }}
        />
      )}
    </Card>
  );
}
