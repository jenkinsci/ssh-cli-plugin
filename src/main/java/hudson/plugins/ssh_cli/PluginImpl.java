package hudson.plugins.ssh_cli;

import hudson.Plugin;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.util.QuotedStringTokenizer;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.session.ServerSession;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {
    private SshServer sshd;

    @Override
    public void start() throws Exception {
        sshd = SshServer.setUpDefaultServer();
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
                List<String> args = Arrays.asList(new QuotedStringTokenizer(command).toArray());
                String name = args.get(0);
                LightCLICommand cmd = LightCLICommand.clone(name);
                if (cmd==null)  cmd = new ErrorCommand();
                cmd.setCommand(command,args.subList(1,args.size()));
                return cmd;
            }
        });

        // the client needs to possess the private key used for launching EC2 instance.
        // this enables us to authenticate the legitimate user.
        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            public boolean authenticate(String username, PublicKey key, ServerSession session) {
                // on unsecured Hudson, perform no authentication
                Hudson h = Hudson.getInstance();
                if (!h.isUseSecurity()) return true;

                User u = h.getUser(username);
                if (u==null)    return false;
                SSHKeyUserProperty p = u.getProperty(SSHKeyUserProperty.class);
                if (p==null)    return false;

                return p.hasKey(key);
            }
        });

        sshd.start();
    }

    @Override
    public void stop() throws Exception {
        if (sshd!=null)
            sshd.stop();
    }
}
