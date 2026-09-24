import { alpha } from '@mui/material/styles';
import type { SystemStyleObject } from '@mui/system';
import type { Theme } from '@mui/material/styles';

const TAG_PALETTES = [
  { bg: '#FFD9E2', text: '#7A1D47', border: '#F06292' },
  { bg: '#FFE1CC', text: '#8A3A00', border: '#FF8A65' },
  { bg: '#FFE9B3', text: '#785300', border: '#FFB300' },
  { bg: '#DDF3C8', text: '#355E00', border: '#8BC34A' },
  { bg: '#C9F1E4', text: '#005B46', border: '#26A69A' },
  { bg: '#D4EEFF', text: '#005B7A', border: '#29B6F6' },
  { bg: '#DCE8FF', text: '#23408E', border: '#5C6BC0' },
  { bg: '#E6DEFF', text: '#5B2E91', border: '#9575CD' },
  { bg: '#F6D9FF', text: '#7B1FA2', border: '#BA68C8' },
];

function hashTag(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function getPalette(tag: string) {
  return TAG_PALETTES[hashTag(tag.trim().toLowerCase()) % TAG_PALETTES.length];
}

interface TagChipStyleOptions {
  selected?: boolean;
  clickable?: boolean;
  withDelete?: boolean;
}

export function getTagChipSx(
  tag: string,
  options: TagChipStyleOptions = {},
): SystemStyleObject<Theme> {
  const { selected = true, clickable = false, withDelete = false } = options;
  const palette = getPalette(tag);

  return {
    bgcolor: selected ? palette.bg : alpha(palette.border, 0.06),
    color: selected ? palette.text : alpha(palette.text, 0.92),
    border: '1px solid',
    borderColor: selected ? alpha(palette.border, 0.9) : alpha(palette.border, 0.3),
    fontWeight: selected ? 600 : 500,
    transition: 'transform 0.15s ease, filter 0.15s ease, background-color 0.15s ease',
    ...(clickable && {
      cursor: 'pointer',
      // Only on real hover devices — on touch the :hover state sticks after a tap
      // and makes an unselected chip look selected.
      '@media (hover: hover)': {
        '&:hover': {
          bgcolor: selected ? alpha(palette.border, 0.28) : alpha(palette.border, 0.18),
          transform: 'translateY(-1px)',
        },
      },
    }),
    ...(withDelete && {
      '& .MuiChip-deleteIcon': {
        color: selected ? alpha(palette.text, 0.72) : alpha(palette.text, 0.58),
        '&:hover': {
          color: palette.text,
        },
      },
    }),
  };
}
