package io.stridelab.privatealpha;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SessionVaultTest {
    private static final String A="synthetic-vault-a",B="synthetic-vault-b";
    private Context context;private SessionVault vault;
    @Before public void setup() throws Exception {context=InstrumentationRegistry.getInstrumentation().getTargetContext();vault=new SessionVault(context);vault.clear(A);vault.clear(B);}
    @After public void cleanup() throws Exception {vault.clear(A);vault.clear(B);}
    private File file(String account) throws Exception {StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8)))name.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return new File(new File(context.getFilesDir(),"native-sessions"),name+".vault");}
    private NativeSession session(String account) {return new NativeSession(account,"synthetic-access-token","synthetic-refresh-token",1800000000L);}
    @Test public void encryptedSessionSurvivesReopeningWithoutPlaintextTokensOnDisk() throws Exception {
        vault.save(session(A));byte[] bytes=Files.readAllBytes(file(A).toPath());String raw=new String(bytes,StandardCharsets.UTF_8);assertFalse(raw.contains("synthetic-access-token"));assertFalse(raw.contains("synthetic-refresh-token"));
        NativeSession loaded=new SessionVault(context).read(A);assertNotNull(loaded);assertEquals(A,loaded.account);assertEquals("synthetic-refresh-token",loaded.refreshToken);assertNull(vault.read(B));
    }
    @Test public void modifiedCiphertextIsRejectedWithoutDeletingIt() throws Exception {
        vault.save(session(A));byte[] bytes=Files.readAllBytes(file(A).toPath());bytes[bytes.length-1]^=1;Files.write(file(A).toPath(),bytes);
        assertThrows(java.io.IOException.class,()->vault.read(A));assertTrue(file(A).exists());
    }
    @Test public void copyingOneAccountsCiphertextCannotAuthenticateAnotherAccount() throws Exception {
        vault.save(session(A));vault.save(session(B));Files.write(file(B).toPath(),Files.readAllBytes(file(A).toPath()));assertThrows(java.io.IOException.class,()->vault.read(B));assertNotNull(vault.read(A));
    }
    @Test public void clearingOneAccountRetainsTheOtherAndInvalidatesOldEncryptedCopies() throws Exception {
        vault.save(session(A));vault.save(session(B));byte[] old=Files.readAllBytes(file(A).toPath());vault.clear(A);assertNull(vault.read(A));assertNotNull(vault.read(B));
        try(FileOutputStream output=new FileOutputStream(file(A))){output.write(old);}assertThrows(java.io.IOException.class,()->vault.read(A));
    }
}
