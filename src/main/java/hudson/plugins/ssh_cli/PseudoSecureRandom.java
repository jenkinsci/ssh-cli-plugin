package hudson.plugins.ssh_cli;

import java.security.SecureRandom;
import java.security.SecureRandomSpi;
import java.util.Random;

/**
 * Pseudo {@link SecureRandom} implementation
 * that enables predictable seeding.
 *
 * @author Kohsuke Kawaguchi
 */
class PseudoSecureRandom extends SecureRandom {
    public PseudoSecureRandom(Random r) {
        super(new PredictableSecureRandomSpi(r), null);
    }

    private static class PredictableSecureRandomSpi extends SecureRandomSpi {
        private final Random r;

        public PredictableSecureRandomSpi(Random r) {
            this.r = r;
        }

        @Override
        protected void engineSetSeed(byte[] seed) {
            // ignored
        }

        @Override
        protected void engineNextBytes(byte[] bytes) {
            r.nextBytes(bytes);
        }

        @Override
        protected byte[] engineGenerateSeed(int numBytes) {
            return new byte[0];
        }
    }
}
