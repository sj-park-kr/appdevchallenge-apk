package com.carmagotchi.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 파일 한 개를 통째로 감싸는 암호화. JVM 테스트는 일반 키로 같은 형식을 쓴다. */
interface BlobCrypto {
    fun seal(plain: ByteArray, aad: ByteArray): ByteArray
    fun open(blob: ByteArray, aad: ByteArray): ByteArray // 키가 없거나 태그가 다르면 예외
    fun destroyKey()
}

/** 형식: "CHM1" + IV(12) + 암호문 + 태그(16). IV는 Cipher가 매번 새로 만든다. */
class AesGcmCrypto(private val key: () -> SecretKey, private val destroy: () -> Unit = {}) : BlobCrypto {
    override fun seal(plain: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
        return MAGIC + c.iv + c.doFinal(plain)
    }

    override fun open(blob: ByteArray, aad: ByteArray): ByteArray {
        require(blob.size > MAGIC.size + IV_LEN && blob.copyOf(MAGIC.size).contentEquals(MAGIC)) { "bad header" }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, MAGIC.size, IV_LEN)); updateAAD(aad)
        }
        return c.doFinal(blob, MAGIC.size + IV_LEN, blob.size - MAGIC.size - IV_LEN)
    }

    override fun destroyKey() = destroy()

    private companion object {
        val MAGIC = "CHM1".toByteArray()
        const val IV_LEN = 12
    }
}

/** Android Keystore AES-256 키(별칭 chama_store_v1). 사용자 인증 없음: 차는 무인으로도 동작한다. */
object KeystoreCrypto {
    private const val ALIAS = "chama_store_v1"
    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun create(): BlobCrypto = AesGcmCrypto(
        key = {
            (ks().getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
                generateKey()
            }
        },
        destroy = { runCatching { ks().deleteEntry(ALIAS) } },
    )
}
