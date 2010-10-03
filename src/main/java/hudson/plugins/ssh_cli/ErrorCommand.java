package hudson.plugins.ssh_cli;

import java.io.PrintStream;

/**
 * {@link LightCLICommand} used to report an undefined command.
 * @author Kohsuke Kawaguchi
 */
class ErrorCommand extends LightCLICommand {
    @Override
    protected int main() throws Exception {
        return super.main();
    }

    @Override
    protected int execute() throws Exception {
        new PrintStream(stderr,true).println("Undefined command: "+ command);
        return -1;
    }

    @Override
    public String getShortDescription() {
        return ""; // unused
    }
}
