package com.sysadmindoc.billminder.security

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedAttachment(
    val displayName: String,
    val fileName: String,
    val mimeType: String
)

object EncryptedAttachmentStore {
    private const val KEY_ALIAS = "billminder_receipt_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ATTACHMENT_DIRECTORY = "attachments"
    private const val CACHE_DIRECTORY = "attachment-cache"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val MAX_BYTES = 10L * 1024L * 1024L

    suspend fun importUri(context: Context, uri: Uri): EncryptedAttachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "receipt-${System.currentTimeMillis()}"
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val directory = File(context.filesDir, ATTACHMENT_DIRECTORY).apply { mkdirs() }
        val storedName = "${UUID.randomUUID()}.bin"
        val target = File(directory, storedName)

        try {
            val input = resolver.openInputStream(uri) ?: return@withContext null
            input.use { source ->
                val iv = ByteArray(IV_LENGTH).also(SecureRandom()::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                }
                FileOutputStream(target).use { rawOutput ->
                    rawOutput.write(iv)
                    CipherOutputStream(rawOutput, cipher).use { encryptedOutput ->
                        copyBounded(source, encryptedOutput)
                    }
                }
            }
            EncryptedAttachment(displayName, storedName, mimeType)
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    suspend fun decryptToCache(context: Context, attachmentFile: String): File? = withContext(Dispatchers.IO) {
        val source = safeAttachmentFile(context, attachmentFile) ?: return@withContext null
        if (!source.exists()) return@withContext null
        val cacheDirectory = cacheWorkspace(context).apply { mkdirs() }
        val output = File(cacheDirectory, "view-${UUID.randomUUID()}")

        try {
            FileInputStream(source).use { rawInput ->
                val iv = ByteArray(IV_LENGTH)
                readFully(rawInput, iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                }
                CipherInputStream(rawInput, cipher).use { decryptedInput ->
                    FileOutputStream(output).use { clearOutput ->
                        copyBounded(decryptedInput, clearOutput)
                    }
                }
            }
            output
        } catch (_: Exception) {
            output.delete()
            null
        }
    }

    /** Decrypts one stored receipt into a caller-owned temporary file for portable backup. */
    internal suspend fun exportPlaintextTo(
        context: Context,
        attachmentFile: String,
        target: File
    ): Long = withContext(Dispatchers.IO) {
        val source = safeAttachmentFile(context, attachmentFile)
            ?: throw IOException("Unsafe receipt file name")
        if (!source.isFile) throw IOException("Receipt file is missing")
        target.parentFile?.mkdirs()
        try {
            FileInputStream(source).use { rawInput ->
                val iv = ByteArray(IV_LENGTH)
                readFully(rawInput, iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                }
                CipherInputStream(rawInput, cipher).use { decryptedInput ->
                    FileOutputStream(target).use { output -> copyBounded(decryptedInput, output) }
                }
            }
            target.length()
        } catch (error: Exception) {
            target.delete()
            throw IOException("Receipt could not be decrypted", error)
        }
    }

    /** Encrypts restored plaintext into a prepared file that is not yet live. */
    internal suspend fun prepareRestoreFile(
        context: Context,
        plaintext: File,
        target: File
    ) = withContext(Dispatchers.IO) {
        require(plaintext.isFile) { "Restored receipt is missing" }
        target.parentFile?.mkdirs()
        try {
            FileInputStream(plaintext).use { source ->
                val iv = ByteArray(IV_LENGTH).also(SecureRandom()::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                }
                FileOutputStream(target).use { rawOutput ->
                    rawOutput.write(iv)
                    CipherOutputStream(rawOutput, cipher).use { output -> copyBounded(source, output) }
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw IOException("Receipt could not be prepared for restore", error)
        }
    }

    internal fun newStoredName(): String = "${UUID.randomUUID()}.bin"

    /** Atomically makes one fully prepared encrypted receipt visible to the live database. */
    internal fun installPrepared(context: Context, prepared: File, storedName: String) {
        check(isSafeStoredName(storedName)) { "Unsafe receipt file name" }
        val directory = File(context.filesDir, ATTACHMENT_DIRECTORY).apply { mkdirs() }.canonicalFile
        val target = File(directory, storedName).canonicalFile
        check(target.parentFile == directory && !target.exists()) { "Receipt destination is invalid" }
        try {
            Files.move(prepared.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(prepared.toPath(), target.toPath())
        }
    }

    fun delete(context: Context, attachmentFile: String) {
        safeAttachmentFile(context, attachmentFile)?.delete()
    }

    /**
     * Deletes stored receipts that no payment refers to any more. Rows can go without their bytes:
     * the schema migration drops duplicate and orphaned payments, and a process killed during an
     * undo window leaves files behind with nothing pointing at them.
     */
    suspend fun purgeOrphans(context: Context, referenced: Set<String>): Int = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, ATTACHMENT_DIRECTORY)
        if (!directory.isDirectory) return@withContext 0
        directory.listFiles().orEmpty().count { file ->
            file.name !in referenced && file.delete()
        }
    }

    /** Removes every plaintext copy made for viewing or sharing a receipt. */
    suspend fun clearCache(context: Context): Int = withContext(Dispatchers.IO) {
        val directory = cacheWorkspace(context)
        if (!directory.isDirectory) return@withContext 0
        directory.listFiles().orEmpty().count { it.delete() }
    }

    /**
     * Drops one plaintext copy that was handed to an external viewer. The path is resolved against
     * the cache workspace first, so a caller that has lost track of what it opened cannot delete
     * anything outside it. Returns true once no copy remains, including when it was already gone.
     */
    fun releaseCachedView(context: Context, file: File): Boolean {
        val directory = runCatching { cacheWorkspace(context).canonicalFile }.getOrNull() ?: return false
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return false
        if (target.parentFile != directory) return false
        return !target.exists() || target.delete()
    }

    /** Where every decrypted-for-viewing copy lives, and the only place a release may delete from. */
    internal fun cacheWorkspace(context: Context): File = File(context.cacheDir, CACHE_DIRECTORY)

    internal fun isSafeStoredName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && !name.contains("..")

    private fun safeAttachmentFile(context: Context, name: String): File? {
        if (!isSafeStoredName(name)) return null
        val directory = File(context.filesDir, ATTACHMENT_DIRECTORY).canonicalFile
        val file = File(directory, name).canonicalFile
        return file.takeIf { it.parentFile == directory }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            if (copied > MAX_BYTES) throw IOException("Attachment exceeds 10 MB limit")
            output.write(buffer, 0, count)
        }
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("Attachment is truncated")
            offset += count
        }
    }
}
