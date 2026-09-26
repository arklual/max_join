import { shareToMax } from '../api/maxBridge';
import { nativeCopy } from '../api/native';

/**
 * Shares a link: MAX share sheet → system share sheet → clipboard.
 * Returns 'copied' when the link ended up in the clipboard, so the caller can say so.
 */
export async function shareLink(title: string, text: string, link: string): Promise<'shared' | 'copied' | 'cancelled' | 'failed'> {
  if (await shareToMax(text, link)) return 'shared';
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text, url: link });
      return 'shared';
    } catch (err) {
      if ((err as { name?: string })?.name === 'AbortError') return 'cancelled';
    }
  }
  try {
    if (!(await nativeCopy(`${text} ${link}`))) await navigator.clipboard.writeText(`${text} ${link}`);
    return 'copied';
  } catch {
    return 'failed';
  }
}
