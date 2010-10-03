package hudson.plugins.ssh_cli;

import com.trilead.ssh2.crypto.Base64;
import hudson.Plugin;
import hudson.util.QuotedStringTokenizer;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.session.ServerSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {
    @Override
    public void start() throws Exception {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(new UserAuthPublicKey.Factory()));
        sshd.setCipherFactories(Arrays.asList(// AES 256 and 192 requires unlimited crypto, so don't use that
                new AES128CBC.Factory(),
                new TripleDESCBC.Factory(),
                new BlowfishCBC.Factory()));

        sshd.setPort(9500);

        // TODO: perhaps we can compute the digest of the userdata and somehow turn it into the key?
        // for the Hudson master to be able to authenticate the EC2 instance (in the face of man-in-the-middle attack possibility),
        // we need the server to know some secret.
        sshd.setKeyPairProvider(new KeyPairProviderImpl());     // for now, Hudson doesn't authenticate the EC2 instance.

        sshd.setCommandFactory(new CommandFactory() {
            public Command createCommand(String command) {
                // find the right command and execute it.
                String name = new QuotedStringTokenizer(command).nextToken();
                LightCLICommand cmd = LightCLICommand.clone(name);
                if (cmd==null)  cmd = new ErrorCommand();
                cmd.setCommand(command);
                return cmd;
            }
        });

        // the client needs to possess the private key used for launching EC2 instance.
        // this enables us to authenticate the legitimate user.
        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            public boolean authenticate(String username, PublicKey key, ServerSession session) {
                try {
                    BufferedReader r = new BufferedReader(new FileReader(new File("/home/kohsuke/.ssh/authorized_keys")));
                    String line;
                    while ((line=r.readLine())!=null) {
                        try {
                            PublicKey k = parseKey(line);
                            if (key.equals(k))  return true;
                        } catch (NoSuchAlgorithmException e) {
                            LOGGER.log(Level.WARNING,"Failed to load public keys",e);
                        } catch (InvalidKeySpecException e) {
                            LOGGER.log(Level.WARNING,"Failed to load public keys",e);
                        } catch (NoSuchProviderException e) {
                            LOGGER.log(Level.WARNING,"Failed to load public keys",e);
                        }
                    }
                    return false;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,"Failed to load public keys",e);
                    return false;
                }
            }
        });

        sshd.start();
    }

    private static PublicKey parseKey(String key) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        String[] keyComponents = key.split(" ");
        if(keyComponents.length!=3 || !keyComponents[0].equals("ssh-rsa"))
            return null;

        Buffer buf = new Buffer(Base64.decode(keyComponents[1].toCharArray()));
        return buf.getRawPublicKey();
    }

    private static final Logger LOGGER = Logger.getLogger(PluginImpl.class.getName());
}
