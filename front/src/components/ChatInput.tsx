import { forwardRef } from 'react';
import { Paper, TextField, IconButton, Snackbar } from '@mui/material';
import { SendOutlined } from '@mui/icons-material';
import EmojiPicker from './EmojiPicker';

interface ChatInputProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  disabled?: boolean;
  /** Shown as a toast above the input, e.g. when sending failed. */
  error?: string;
  onErrorClose?: () => void;
}

const ChatInput = forwardRef<HTMLTextAreaElement, ChatInputProps>(
  ({ value, onChange, onSend, disabled, error, onErrorClose }, ref) => {
    function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        onSend();
      }
    }

    function handleEmoji(emoji: string) {
      onChange(value + emoji);
    }

    return (
      <Paper
        elevation={0}
        square
        sx={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 0.5,
          px: 1,
          py: 1,
          pb: 'calc(8px + var(--safe-area-bottom, 0px))',
          borderTop: 1,
          borderColor: 'divider',
          flexShrink: 0,
        }}
      >
        <EmojiPicker onPick={handleEmoji} disabled={disabled} />
        <TextField
          inputRef={ref}
          multiline
          maxRows={4}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Сообщение..."
          inputProps={{ enterKeyHint: 'send' }}
          variant="outlined"
          size="small"
          fullWidth
          sx={{
            '& .MuiOutlinedInput-root': {
              borderRadius: '20px',
            },
          }}
        />
        <IconButton
          onClick={onSend}
          aria-label="Отправить"
          disabled={!value.trim() || disabled}
          sx={{
            bgcolor: 'primary.main',
            color: 'onPrimary.main',
            '&:hover': { bgcolor: 'primary.dark' },
            '&.Mui-disabled': { bgcolor: 'action.disabledBackground', color: 'action.disabled' },
            flexShrink: 0,
            transition: 'transform 0.15s ease, background-color 0.2s ease',
            '&:active': { transform: 'scale(0.9)' },
          }}
        >
          <SendOutlined />
        </IconButton>
        <Snackbar
          open={!!error}
          autoHideDuration={3000}
          onClose={onErrorClose}
          message={error}
          sx={{ bottom: { xs: 140 } }}
        />
      </Paper>
    );
  },
);

ChatInput.displayName = 'ChatInput';

export default ChatInput;
