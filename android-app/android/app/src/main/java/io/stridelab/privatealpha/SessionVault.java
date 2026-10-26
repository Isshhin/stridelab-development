package io.stridelab.privatealpha;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Android Keystore encryption + atomic app-private files; no credentials in WebView storage. */
public final class SessionVault {
    private static final Object LOCK=new Object();
    private static final int MAX_BYTES=128*1024;
    private static final byte[] MAGIC={'S','L','V',1};
    private final File directory;
    public SessionVault(Context context) {directory=new File(context.getApplicationContext().getFilesDir(),"native-sessions");}
    private static String digest(String account) throws Exception {
        RecordPolicy.identifier(account);byte[] bytes=MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();
    }
    private static String alias(String account) throws Exception {return "io.stridelab.privatealpha.session.v1."+digest(account);}
    private AtomicFile file(String account) throws Exception {return new AtomicFile(new File(directory,digest(account)+".vault"));}
    private static byte[] aad(String account) {return ("io.stridelab.privatealpha|session-v1|"+account).getBytes(StandardCharsets.UTF_8);}
    private static SecretKey key(String account,boolean create) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String name=alias(account);
        if(store.containsAlias(name))return (SecretKey)store.getKey(name,null);
        if(!create)throw new IOException("Saved session key is unavailable; sign in again");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(name,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    public void save(NativeSession session) throws IOException {
        if(session==null)throw new IllegalArgumentException("A session is required");
        synchronized(LOCK) {
            FileOutputStream stream=null;AtomicFile target=null;
            try {
                if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Session directory is unavailable");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(session.account,true));cipher.updateAAD(aad(session.account));
                byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(session.encode().getBytes(StandardCharsets.UTF_8));
                if(iv.length!=12||encrypted.length+16>MAX_BYTES)throw new IOException("Session encryption format is unavailable");
                target=file(session.account);stream=target.startWrite();stream.write(MAGIC);stream.write(iv);stream.write(encrypted);target.finishWrite(stream);stream=null;
            } catch(Exception e) {
                if(target!=null&&stream!=null)target.failWrite(stream);
                throw new IOException("Could not securely save the session; existing session files were retained where possible");
            }
        }
    }
    public NativeSession read(String account) throws IOException {
        RecordPolicy.identifier(account);
        synchronized(LOCK) {
            try {
                AtomicFile source=file(account);
                if(!source.getBaseFile().exists()&&!new File(source.getBaseFile().getPath()+".bak").exists())return null;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(FileInputStream input=source.openRead()) {byte[] block=new byte[4096];int count;while((count=input.read(block))!=-1){if(bytes.size()+count>MAX_BYTES)throw new IOException("Saved session is too large");bytes.write(block,0,count);}}
                byte[] encoded=bytes.toByteArray();
                if(encoded.length<32||!Arrays.equals(MAGIC,Arrays.copyOfRange(encoded,0,4)))throw new IOException("Unsupported saved session format");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(account,false),new GCMParameterSpec(128,Arrays.copyOfRange(encoded,4,16)));cipher.updateAAD(aad(account));
                return NativeSession.decode(new String(cipher.doFinal(Arrays.copyOfRange(encoded,16,encoded.length)),StandardCharsets.UTF_8),account);
            } catch(Exception e) {throw new IOException("Saved session is unavailable or could not be verified; sign in again. No session data was returned");}
        }
    }
    public void clear(String account) throws IOException {
        RecordPolicy.identifier(account);
        synchronized(LOCK) {
            try {
                AtomicFile target=file(account);target.delete();
                KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(alias(account)))store.deleteEntry(alias(account));
                if(target.getBaseFile().exists())throw new IOException("Session file could not be removed");
            } catch(Exception e) {throw new IOException("Could not finish removing the saved session");}
        }
    }
}
