import { useEffect, useRef } from 'react';

interface Options {
  /** Whether more pages exist and loading is allowed (false while an error is shown). */
  enabled: boolean;
  /** A request is in flight — don't start another one. */
  loading: boolean;
  /** Changes whenever items are appended (e.g. items.length) so the sentinel is re-checked. */
  itemCount: number;
  /** Start loading this far before the sentinel scrolls into view. */
  rootMargin?: string;
}

/**
 * Infinite scroll: returns a ref for a sentinel element placed after the list;
 * `onLoadMore` fires when it approaches the viewport. The observer is recreated
 * after every page, so a short page that leaves the sentinel visible keeps
 * loading until the screen is filled.
 */
export function useInfiniteScroll<T extends Element>(
  onLoadMore: () => void,
  { enabled, loading, itemCount, rootMargin = '600px 0px' }: Options,
) {
  const sentinelRef = useRef<T | null>(null);
  const callbackRef = useRef(onLoadMore);

  useEffect(() => {
    callbackRef.current = onLoadMore;
  }, [onLoadMore]);

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !enabled || loading || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          observer.disconnect();
          callbackRef.current();
        }
      },
      { rootMargin },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [enabled, loading, itemCount, rootMargin]);

  return sentinelRef;
}
