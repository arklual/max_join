import { useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import FavoriteBorderOutlined from '@mui/icons-material/FavoriteBorderOutlined';
import Diversity3Outlined from '@mui/icons-material/Diversity3Outlined';
import ChatBubbleOutlineOutlined from '@mui/icons-material/ChatBubbleOutlineOutlined';

const STORAGE_KEY = 'join_how_it_works_hidden';

const STEPS = [
  { icon: <FavoriteBorderOutlined fontSize="small" />, text: 'Лайкни событие, на которое хочешь пойти' },
  { icon: <Diversity3Outlined fontSize="small" />, text: 'Найдём того, кто хочет туда же' },
  { icon: <ChatBubbleOutlineOutlined fontSize="small" />, text: 'Договоритесь в чате и идите вместе' },
];

function isHidden(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

/** Three-step explanation of the main scenario, shown on the afisha until dismissed. */
export default function HowItWorksCard() {
  const [hidden, setHidden] = useState(isHidden);
  if (hidden) return null;

  function hide() {
    setHidden(true);
    try {
      localStorage.setItem(STORAGE_KEY, '1');
    } catch {
      // storage unavailable — hidden for this session only
    }
  }

  return (
    <Card
      variant="outlined"
      sx={{ borderRadius: 4, p: 2, pr: 1, bgcolor: 'primaryContainer.main', borderColor: 'transparent', animation: 'fadeInUp 0.4s ease-out' }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
        <Typography sx={{ fontWeight: 700, color: 'onPrimaryContainer.main' }}>Как найти, с кем сходить</Typography>
        <IconButton size="small" onClick={hide} aria-label="Скрыть подсказку" sx={{ color: 'onPrimaryContainer.main', mt: -0.5 }}>
          <CloseOutlined fontSize="small" />
        </IconButton>
      </Box>
      <Box component="ol" sx={{ listStyle: 'none', p: 0, m: 0, mt: 1, display: 'flex', flexDirection: 'column', gap: 1 }}>
        {STEPS.map((step) => (
          <Box component="li" key={step.text} sx={{ display: 'flex', alignItems: 'center', gap: 1.25, color: 'onPrimaryContainer.main' }}>
            <Box
              sx={{
                width: 32,
                height: 32,
                borderRadius: '50%',
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'surface.main',
                color: 'primary.main',
              }}
            >
              {step.icon}
            </Box>
            <Typography variant="body2">{step.text}</Typography>
          </Box>
        ))}
      </Box>
    </Card>
  );
}
