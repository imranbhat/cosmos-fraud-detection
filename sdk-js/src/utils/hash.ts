/**
 * Computes a SHA-256 hex digest of the given string using the Web Crypto API (SubtleCrypto).
 * Falls back to a simple djb2 hash string if SubtleCrypto is unavailable.
 */
export async function sha256Hex(input: string): Promise<string> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const encoder = new TextEncoder();
    const data = encoder.encode(input);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  // Fallback: djb2 hash (non-cryptographic, used only when SubtleCrypto is absent)
  return djb2Hash(input);
}

function djb2Hash(str: string): string {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) + hash + str.charCodeAt(i);
    hash |= 0; // force 32-bit int
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}
