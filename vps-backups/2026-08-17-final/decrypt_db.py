import base64
import os
import sys
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python decrypt_db.py INPUT.bak.aes OUTPUT.bak")

    key_text = os.environ.get("HOTEL_DB_BACKUP_KEY", "").strip()
    if not key_text:
        raise SystemExit("HOTEL_DB_BACKUP_KEY is required")

    payload = Path(sys.argv[1]).read_bytes()
    if payload[:8] != b"HOTELDB1":
        raise SystemExit("Unsupported backup format")

    nonce = payload[8:20]
    ciphertext = payload[20:]
    key = base64.urlsafe_b64decode(key_text)
    plaintext = AESGCM(key).decrypt(
        nonce, ciphertext, b"HotelDB-final-20260817"
    )
    Path(sys.argv[2]).write_bytes(plaintext)


if __name__ == "__main__":
    main()
