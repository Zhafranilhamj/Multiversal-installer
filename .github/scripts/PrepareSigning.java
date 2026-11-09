import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Properties;

/** Validates CI signing material without printing credentials or exception details. */
class PrepareSigning {
    public static void main(String[] args) throws Exception {
        String file = System.getenv("RUNNER_TEMP") + "/release.jks";
        String storePassword = System.getenv("STORE_PASSWORD");
        String keyPassword = System.getenv("KEY_PASSWORD");
        String alias = System.getenv("KEY_ALIAS");
        KeyStore store;
        try {
            store = KeyStore.getInstance(new File(file), storePassword.toCharArray());
        } catch (Exception e) {
            fail("Keystore cannot be opened. Check KEYSTORE_BASE64 and STORE_PASSWORD (or KEYSTORE_PASSWORD). No credentials were printed.");
            return;
        }
        if (!store.containsAlias(alias) || !store.isKeyEntry(alias)) {
            fail("KEY_ALIAS does not identify a private-key entry in the supplied keystore.");
            return;
        }
        try {
            if (!(store.getKey(alias, keyPassword.toCharArray()) instanceof PrivateKey)) {
                fail("The configured signing entry is not a private key.");
                return;
            }
        } catch (Exception e) {
            fail("Cannot unlock the signing key. Check KEY_PASSWORD for the configured alias.");
            return;
        }
        Properties props = new Properties();
        props.setProperty("storeFile", file);
        props.setProperty("storePassword", storePassword);
        props.setProperty("keyAlias", alias);
        props.setProperty("keyPassword", keyPassword);
        // Properties.store correctly escapes backslashes, whitespace and non-ASCII values.
        try (var output = Files.newOutputStream(Path.of("key.properties"))) {
            props.store(output, "CI signing configuration; do not commit");
        }
        System.out.println("Keystore, alias and key password verified; signing configuration prepared.");
    }
    private static void fail(String message) {
        System.err.println("::error::" + message);
        System.exit(1);
    }
}
