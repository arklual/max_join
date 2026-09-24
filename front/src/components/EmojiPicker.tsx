import { useState } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Popover from '@mui/material/Popover';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import EmojiEmotionsOutlined from '@mui/icons-material/EmojiEmotionsOutlined';

interface EmojiPickerProps {
  onPick: (text: string) => void;
  disabled?: boolean;
}

const EMOJI_CATEGORIES: { label: string; emojis: string[] }[] = [
  {
    label: 'Смайлы',
    emojis: [
      '😀', '😁', '😂', '🤣', '😊', '😍', '😘', '😎', '🤩', '🥰',
      '😏', '😉', '😜', '🤪', '😝', '🤗', '🤔', '🤨', '😐', '😑',
      '🙄', '😴', '😪', '😋', '😛', '😬', '🤐', '😶', '😯', '😦',
      '😧', '😮', '😲', '🥺', '😢', '😭', '😤', '😠', '😡', '🤬',
      '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓', '🤝',
    ],
  },
  {
    label: 'Жесты',
    emojis: [
      '👍', '👎', '👌', '✌️', '🤞', '🤘', '🤙', '👏', '🙌', '🤲',
      '👋', '🤚', '🖐️', '✋', '🖖', '👊', '✊', '🤛', '🤜', '💪',
      '🙏', '✍️', '💅', '🤳', '💃', '🕺', '🚶', '🏃', '🤝', '💋',
    ],
  },
  {
    label: 'Сердца',
    emojis: [
      '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💕',
      '💞', '💓', '💗', '💖', '💘', '💝', '💟', '❣️', '💔', '💌',
      '😍', '😘', '🥰', '🥹', '✨', '🌹', '💐', '🌸', '🌷', '🌺',
    ],
  },
  {
    label: 'Стикеры',
    emojis: [
      '🎉', '🎊', '🥳', '🎂', '🍰', '🍕', '🍔', '🍻', '🥂', '☕',
      '🎵', '🎶', '🎸', '🎤', '🎧', '🎬', '🎭', '🎨', '📚', '🏖️',
      '🌅', '🌃', '🌌', '🎆', '🎇', '🌠', '⭐', '🌟', '💫', '✨',
      '🔥', '💯', '🏆', '🥇', '🎯', '👀', '💎', '🦄', '🌈', '🎁',
    ],
  },
];

export default function EmojiPicker({ onPick, disabled }: EmojiPickerProps) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [tab, setTab] = useState(0);

  function handleOpen(e: React.MouseEvent<HTMLElement>) {
    setAnchorEl(e.currentTarget);
  }

  function handleClose() {
    setAnchorEl(null);
  }

  function handleSelect(emoji: string) {
    onPick(emoji);
  }

  const open = Boolean(anchorEl);

  return (
    <>
      <IconButton
        onClick={handleOpen}
        disabled={disabled}
        aria-label="Эмодзи и стикеры"
        sx={{
          color: 'onSurfaceVariant.main',
          flexShrink: 0,
        }}
      >
        <EmojiEmotionsOutlined />
      </IconButton>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        PaperProps={{
          sx: {
            width: { xs: 320, sm: 360 },
            maxWidth: 'calc(100vw - 16px)',
            borderRadius: 3,
            boxShadow: 6,
          },
        }}
      >
        <Tabs
          value={tab}
          onChange={(_e, v) => setTab(v)}
          variant="scrollable"
          scrollButtons={false}
          sx={{
            borderBottom: 1,
            borderColor: 'outlineVariant.main',
            minHeight: 40,
            '& .MuiTab-root': {
              minHeight: 40,
              textTransform: 'none',
              fontSize: '0.8rem',
              fontWeight: 600,
            },
          }}
        >
          {EMOJI_CATEGORIES.map((cat) => (
            <Tab key={cat.label} label={cat.label} />
          ))}
        </Tabs>

        <Box sx={{ p: 1, maxHeight: 300, overflow: 'auto' }}>
          {EMOJI_CATEGORIES[tab].emojis.length === 0 ? (
            <Typography variant="body2" sx={{ color: 'text.secondary', p: 2 }}>
              Пусто
            </Typography>
          ) : (
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: tab === 3
                  ? 'repeat(auto-fill, minmax(56px, 1fr))'
                  : 'repeat(auto-fill, minmax(40px, 1fr))',
                gap: 0.5,
              }}
            >
              {EMOJI_CATEGORIES[tab].emojis.map((emoji, idx) => (
                <IconButton
                  key={`${tab}-${idx}`}
                  onClick={() => handleSelect(emoji)}
                  sx={{
                    fontSize: tab === 3 ? '2rem' : '1.5rem',
                    lineHeight: 1,
                    width: tab === 3 ? 56 : 40,
                    height: tab === 3 ? 56 : 40,
                    borderRadius: 2,
                    '&:hover': { bgcolor: 'action.hover' },
                  }}
                >
                  <span style={{ fontSize: 'inherit' }}>{emoji}</span>
                </IconButton>
              ))}
            </Box>
          )}
        </Box>
      </Popover>
    </>
  );
}
