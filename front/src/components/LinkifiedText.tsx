import { Fragment, type MouseEvent } from 'react';
import Link from '@mui/material/Link';
import { openExternalLink } from '../api/maxBridge';

const URL_RE = /(https?:\/\/[^\s<>"«»]+[^\s<>"«».,;:!?)\]}'])/gi;

interface Props {
  text: string;
  /** Link colour; defaults to the theme primary. Use 'inherit' inside coloured bubbles. */
  color?: string;
}

/** Renders plain text with http(s) URLs turned into links that open via MAX. */
export default function LinkifiedText({ text, color = 'primary.main' }: Props) {
  const parts = text.split(URL_RE);
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <Link
            key={i}
            href={part}
            onClick={(e: MouseEvent) => {
              e.preventDefault();
              e.stopPropagation();
              openExternalLink(part);
            }}
            sx={{ color, textDecorationColor: 'currentColor', overflowWrap: 'anywhere', cursor: 'pointer' }}
          >
            {part}
          </Link>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  );
}
