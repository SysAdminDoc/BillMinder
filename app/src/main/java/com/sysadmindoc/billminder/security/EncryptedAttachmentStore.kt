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
        val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
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

    fun delete(context: Context, attachmentFile: String) {
        safeAttachmentFile(context, attachmentFile)?.delete()
    }

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
