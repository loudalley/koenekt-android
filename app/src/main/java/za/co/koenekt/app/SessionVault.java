package za.co.koenekt.app;

import android.app.KeyguardManager;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Session tokens only, encrypted with a non-exportable, device-authentication-bound key. */
final class SessionVault {
    private static final String ALIAS = "koenekt.saved-session.v1";
    private static final byte[] AAD = "za.co.koenekt.app/session/v1".getBytes(StandardCharsets.UTF_8);
    private final Context context;
    private final AtomicFile file;

    SessionVault(Context context) {
        this.context = context;
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "saved-session.bin"));
    }
    boolean exists() { return file.getBaseFile().exists(); }
    boolean deviceSecured() { return context.getSystemService(KeyguardManager.class).isDeviceSecure(); }

    @SuppressWarnings("deprecation")
    void prepare() throws Exception {
        if (!deviceSecured()) throw new Exception("Set a device PIN, pattern or password to remember your sign-in.");
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(false);
        if (android.os.Build.VERSION.SDK_INT >= 30) builder.setUserAuthenticationParameters(30,
                KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        else builder.setUserAuthenticationValidityDurationSeconds(30);
        generator.init(builder.build()); generator.generateKey();
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        return (SecretKey) store.getKey(ALIAS, null);
    }
    void save(JSONObject session) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(AAD);
        byte[] ciphertext = cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8));
        FileOutputStream out = null;
        try {
            out = file.startWrite(); out.write(cipher.getIV().length); out.write(cipher.getIV()); out.write(ciphertext); file.finishWrite(out);
        } catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
    JSONObject read() throws Exception {
        if (!deviceSecured()) throw new Exception("Device lock was removed. Sign in again.");
        byte[] bytes = file.readFully();
        int length = bytes.length == 0 ? 0 : bytes[0] & 0xff;
        if (length != 12 || bytes.length < length + 17 || bytes.length > 32768) throw new Exception("Invalid saved session");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 1, length)); cipher.updateAAD(AAD);
        return new JSONObject(new String(cipher.doFinal(bytes, length + 1, bytes.length - length - 1), StandardCharsets.UTF_8));
    }
    void clear() {
        file.delete();
        try { KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(ALIAS); }
        catch (Exception ignored) { /* Deleted ciphertext is sufficient to forget the sign-in. */ }
    }
}
