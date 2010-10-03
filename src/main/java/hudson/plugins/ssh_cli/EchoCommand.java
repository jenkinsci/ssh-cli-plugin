package hudson.plugins.ssh_cli;

import hudson.Extension;
import hudson.util.IOUtils;

import java.io.IOException;

/**
 * Test command that just echos the input.
 * @author Kohsuke Kawaguchi
 */
@Extension
public class EchoCommand extends LightCLICommand {
    @Override
    protected int execute() throws IOException {
        IOUtils.copy(stdin,stdout);
        return 0;
    }
}
