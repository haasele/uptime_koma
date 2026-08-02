package dev.haasele.koma.shared.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestTest {

    @Test
    fun `sha256 matches known vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digest(ByteArray(0)).toHex(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digest("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun `sha1 matches known vectors`() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Sha1.digest(ByteArray(0)).toHex())
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1.digest("abc".encodeToByteArray()).toHex())
    }

    @Test
    fun `md5 matches rfc 1321 vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.digest(ByteArray(0)).toHex())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.digest("abc".encodeToByteArray()).toHex())
        assertEquals(
            "d174ab98d277d9f5a5611c2c9f419d9f",
            Md5.digest(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".encodeToByteArray(),
            ).toHex(),
        )
    }

    @Test
    fun `hmac sha256 matches rfc 4231 case 1`() {
        val key = ByteArray(20) { 0x0b }
        val mac = hmac(Sha256, key, "Hi There".encodeToByteArray())
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", mac.toHex())
    }

    @Test
    fun `hmac sha1 matches rfc 2202 case 1`() {
        val key = ByteArray(20) { 0x0b }
        val mac = hmac(Sha1, key, "Hi There".encodeToByteArray())
        assertEquals("b617318655057264e28bc0b6fb378c8ef146be00", mac.toHex())
    }

    @Test
    fun `pbkdf2 matches rfc 6070 style vector`() {
        val derived = pbkdf2("password".encodeToByteArray(), "salt".encodeToByteArray(), 1, 32, Sha256)
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", derived.toHex())
    }
}

class PasswordsTest {

    @Test
    fun `hash verifies the original password`() {
        val encoded = Passwords.hash("correct horse battery staple", iterations = 1_000)
        assertTrue(Passwords.verify("correct horse battery staple", encoded))
        assertFalse(Passwords.verify("wrong password", encoded))
    }

    @Test
    fun `hashes are salted so two hashes differ`() {
        val first = Passwords.hash("same", iterations = 1_000)
        val second = Passwords.hash("same", iterations = 1_000)
        assertTrue(first != second)
    }

    @Test
    fun `low iteration counts are flagged for rehashing`() {
        assertTrue(Passwords.needsRehash(Passwords.hash("x", iterations = 1_000)))
    }
}

class TotpTest {

    @Test
    fun `base32 round trips`() {
        val data = "Uptime Koma".encodeToByteArray()
        assertTrue(Base32.decode(Base32.encode(data)).contentEquals(data))
    }

    @Test
    fun `rfc 6238 reference secret produces the documented code`() {
        val secret = Base32.encode("12345678901234567890".encodeToByteArray())
        assertEquals("287082", Totp.code(secret, 59))
        assertEquals("081804", Totp.code(secret, 1_111_111_109))
    }

    @Test
    fun `verification accepts a one step clock drift`() {
        val secret = Totp.generateSecret()
        val code = Totp.code(secret, 1_000_000)
        assertTrue(Totp.verify(secret, code, 1_000_029))
        assertFalse(Totp.verify(secret, code, 1_000_200))
    }
}
