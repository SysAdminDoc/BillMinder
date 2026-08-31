# BillMinder Portable Backup Format

BillMinder writes portable backups with the `.bmbak` extension. Schema 1 contains the complete bill graph, selected preferences, and the plaintext bytes of every attached receipt inside one encrypted container.

The app never stores the backup passphrase. Losing it means the file cannot be recovered.

## Encryption container

Every file starts with this binary header. Integers are signed 32-bit values in network byte order.

| Field | Value |
| --- | --- |
| Magic | ASCII `BMBACKUP` |
| Container version | `1` |
| KDF iterations | `600000` for files created by the app |
| Salt length and salt | 16 random bytes |
| Nonce length and nonce | 12 random bytes |
| Remaining bytes | AES-256-GCM ciphertext with a 128-bit tag |

The encryption key is derived with PBKDF2-HMAC-SHA256. The full binary header is authenticated as additional data, so changing the version, iteration count, salt, or nonce invalidates the file.

Imports accept KDF counts from 100,000 through 2,000,000. This range leaves room for future tuning without allowing a crafted header to request unreasonable work.

The app completes the encrypted container in private cache before copying it to the selected Storage Access Framework destination. If publication fails, it asks the document provider to remove the incomplete destination.

## Encrypted ZIP payload

Successful decryption produces a ZIP with these entries:

| Path | Contents |
| --- | --- |
| `manifest.json` | Format identifier, schema version, app version, export time, and the size and SHA-256 checksum of every payload entry |
| `data.json` | Bills, cycle-keyed payments, split payees, receipt links, and supported preferences |
| `receipts/<uuid>.bin` | Original receipt bytes, one entry per linked payment |

Local encrypted receipt filenames are never placed in the bundle. A random receipt identifier joins each payment to its ZIP entry. Restore creates a fresh local filename and encrypts the bytes with the destination device's Android Keystore key.

## Included preferences

Schema 1 includes:

- Display currency and manual exchange rates
- Category budgets
- Full-screen reminder and vacation settings
- External privacy masking and in-app amount masking

It does not include app-lock PIN records, the duress PIN, biometric state, failed attempts, auto-lock timing, operating system permissions, scheduled alarm handles, or learned CSV mappings. Device-bound keys and encrypted receipt files are also excluded because ciphertext restored without its Android Keystore key cannot be opened.

## Restore rules

BillMinder decrypts and validates the entire bundle before showing its preview. Validation checks the container tag, schema, exact entry paths, row identifiers, foreign-key references, cycle uniqueness, entry sizes, and every SHA-256 checksum. Receipt and archive size limits are applied while streaming, not after extraction.

Merge keeps current rows and remaps every imported identifier. Replace swaps the current graph for the backup. Both choices apply the included preferences.

Receipt bytes are encrypted into a private staging directory first. Database rows, final receipt installation, and preference commits then run against one Room transaction boundary. A failure rolls the database back, restores the previous preferences, and removes prepared receipt files. Replace deletes old receipt files only after the transaction succeeds.

BillMinder 2.4.0 and earlier wrote partial JSON files. Settings can still import those files for compatibility, but they contain only bills and payments. Missing split payees, settings, and receipt bytes cannot be reconstructed.

## Limits

- Passphrase length: 8 to 128 characters
- Bills, payments, or payees: 100,000 records per type
- Receipt: 10 MB each
- `data.json`: 20 MB
- Expanded encrypted payload: 512 MB

Android Auto Backup and device transfer are disabled for BillMinder data. Use a `.bmbak` file when moving data between devices.
