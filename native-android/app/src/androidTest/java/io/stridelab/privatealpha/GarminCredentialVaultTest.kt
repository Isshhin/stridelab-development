package io.stridelab.privatealpha

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GarminCredentialVaultTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val ownerA="garmin-vault-owner-a"
    private val ownerB="garmin-vault-owner-b"

    @Test fun credentialSurvivesVaultRecreationAndRemainsAccountScopedAndEncrypted() {
        val vault=GarminCredentialVault(context)
        runCatching { vault.clear(ownerA) };runCatching { vault.clear(ownerB) }
        try {
            val record=GarminCredentialRecord(ownerA,"private-api-key","private-token",
                Instant.parse("2026-11-06T00:00:00Z"))
            vault.save(record)
            assertEquals(record,GarminCredentialVault(context).read(ownerA))
            assertNull(GarminCredentialVault(context).read(ownerB))
            val files=java.io.File(context.filesDir,"garmin-personal").listFiles().orEmpty()
            assertTrue(files.isNotEmpty())
            val disk=files.joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
            assertFalse(disk.contains("private-api-key"))
            assertFalse(disk.contains("private-token"))
            assertFalse(record.toString().contains("private-api-key"))
            assertFalse(record.toString().contains("private-token"))
        } finally {
            vault.clear(ownerA);vault.clear(ownerB)
        }
        assertNull(GarminCredentialVault(context).read(ownerA))
    }
}
