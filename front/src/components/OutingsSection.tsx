import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import Avatar from '@mui/material/Avatar';
import AvatarGroup from '@mui/material/AvatarGroup';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import Typography from '@mui/material/Typography';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import ChatBubbleOutlineOutlined from '@mui/icons-material/ChatBubbleOutlineOutlined';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import type { Outing } from '../types';
import { formatEventDateTime } from '../utils/dateUtils';
import { PushkinCardChip } from './PushkinCardInfo';
import { usePushkinEligible } from '../utils/pushkin';

function companionsLine(outing: Outing): string {
  const names = outing.companions.map((c) => c.name).filter(Boolean) as string[];
  if (names.length === 0) return 'Компания собрана';
  if (outing.groupChatId) return `Компания: ${names.slice(0, 3).join(', ')}${names.length > 3 ? ` и ещё ${names.length - 3}` : ''}`;
  return `Идёте вместе с ${names[0]}`;
}

/** "Мои походы": upcoming events the user already has company for, each opening its chat. */
export default function OutingsSection() {
  const navigate = useNavigate();
  const pushkinEligible = usePushkinEligible() === true;
  const [outings, setOutings] = useState<Outing[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<Outing[]>('/outings')
      .then((res) => {
        if (!cancelled) setOutings(res.data);
      })
      .catch(() => {
        if (!cancelled) setOutings([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!outings || outings.length === 0) return null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, animation: 'fadeInUp 0.35s ease-out' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <EventAvailableOutlined sx={{ color: 'primary.main' }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          Мои походы
        </Typography>
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
          · {outings.length}
        </Typography>
      </Box>
      {outings.map((outing) => {
        const target = outing.groupChatId ? `/group-chats/${outing.groupChatId}` : `/chats/${outing.chatId}`;
        return (
          <Card key={`${outing.eventId}-${outing.chatId ?? 'g' + outing.groupChatId}`} variant="outlined" sx={{ borderRadius: 3 }}>
            <CardActionArea onClick={() => navigate(target)} sx={{ display: 'flex', alignItems: 'stretch', justifyContent: 'flex-start' }}>
              <Box
                component="img"
                src={mediaUrl(outing.imageUrl) || undefined}
                alt=""
                sx={{ width: 84, minHeight: 84, objectFit: 'cover', bgcolor: 'surfaceVariant.main', flexShrink: 0 }}
              />
              <Box sx={{ p: 1.25, minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', gap: 0.25 }}>
                <Typography variant="body2" sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {outing.title}
                </Typography>
                <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main' }}>
                  {[formatEventDateTime(outing.eventDate, outing.eventTime ?? ''), outing.city].filter(Boolean).join(' · ')}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mt: 0.25 }}>
                  <AvatarGroup max={3} sx={{ '& .MuiAvatar-root': { width: 22, height: 22, fontSize: '0.7rem' } }}>
                    {outing.companions.map((c) => (
                      <Avatar key={c.userId} src={mediaUrl(c.photo) || undefined} alt={c.name ?? ''}>
                        {(c.name ?? '?').charAt(0)}
                      </Avatar>
                    ))}
                  </AvatarGroup>
                  <Typography variant="caption" sx={{ color: 'primary.main', fontWeight: 600, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {companionsLine(outing)}
                  </Typography>
                </Box>
                {outing.pushkinCard && pushkinEligible && (
                  <Box sx={{ mt: 0.25 }}>
                    <PushkinCardChip />
                  </Box>
                )}
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', pr: 1.25, color: 'onSurfaceVariant.main' }}>
                <ChatBubbleOutlineOutlined fontSize="small" />
              </Box>
            </CardActionArea>
          </Card>
        );
      })}
    </Box>
  );
}
