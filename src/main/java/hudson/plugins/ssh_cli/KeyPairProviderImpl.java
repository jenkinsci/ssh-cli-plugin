package hudson.plugins.ssh_cli;

import hudson.model.Hudson;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Uses the Hudson's secret key to generate a consistent SSH host key.
 */
final class KeyPairProviderImpl extends AbstractKeyPairProvider {
    private KeyPair hostKey;
    @Override
    protected synchronized KeyPair[] loadKeys() {
        if (hostKey==null)
            hostKey = generate();
        return new KeyPair[]{hostKey};
    }

    private KeyPair generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");

            long l=0;
            for (byte b : Hudson.getInstance().getSecretKey().getBytes()) {
                l |= b;
                l <<= 1;
            }

            SecureRandom r = new PseudoSecureRandom(new Random(l));
            kpg.initialize(512, r);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }
}
