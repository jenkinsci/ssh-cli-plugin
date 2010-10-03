package hudson.plugins.ssh_cli;

import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.Util;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import org.apache.sshd.common.util.Buffer;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class SSHKeyUserProperty extends UserProperty {
    /**
     * Public keys as in the "~/.ssh/authorized_keys" format.
     */
    public final String keys;

    @DataBoundConstructor
    public SSHKeyUserProperty(String keys) {
        this.keys = Util.fixNull(keys);
    }

    /**
     * Does this user has the specified public key?
     */
    public boolean hasKey(PublicKey key) {
        for (String line : keys.split("\n")) {
            line = line.trim();
            try {
                PublicKey k = parseKey(line);
                if (key.equals(k))  return true;
            } catch (GeneralSecurityException e) {
                LOGGER.log(WARNING,"Failed to load public key: "+line,e);
            } catch (IOException e) {
                LOGGER.log(WARNING,"Failed to load public key: "+line,e);
            }
        }
        return false;
    }

    /**
     * Parses one line like "ssh-rsa ABCDEF.... kohsuke@somewhere" into a public key.
     */
    private static PublicKey parseKey(String key) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        String[] keyComponents = key.split(" ");
        if(keyComponents.length<2)
            throw new InvalidKeySpecException("Invalid line: "+key);

        try {
            Buffer buf = new Buffer(Base64.decode(keyComponents[1].toCharArray()));
            return buf.getRawPublicKey();
        } catch (IllegalStateException e) {
            throw (InvalidKeySpecException)new InvalidKeySpecException("Invalid line: "+key).initCause(e);
        }
    }

    @Extension
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public String getDisplayName() {
            return "SSH Keys";
        }

        public UserProperty newInstance(User user) {
            return new SSHKeyUserProperty("");
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SSHKeyUserProperty.class.getName());
}
