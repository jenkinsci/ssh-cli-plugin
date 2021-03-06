package hudson.plugins.ssh_cli;

import hudson.AbortException;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class LightCLICommand implements Command, ExtensionPoint {
    /**
     * Can be used to read from SSH client.
     */
    protected InputStream stdin;
    
    /**
     * Unbuffered stdout/stderr that can be used to write to SSH client.
     */
    protected PrintStream stdout, stderr;

    /**
     * The command to be executed as specified by the client.
     */
    protected String command;

    /**
     * Tokenized arguments, excluding the command name.
     */
    protected List<String> arguments;

    private ExitCallback exitCallback;

    /**
     * Access to the terminal and signal handling. Always non-null.
     */
    protected Environment env;

    /**
     * Thread that's executing the command.
     */
    private Thread thread;

    /**
     * Gets the quick summary of what this command does. Should be a one liner.
     * Used by the help command to generate the list of commands.
     */
    public abstract String getShortDescription();

    /*package*/ void setCommand(String command, List<String> arguments) {
        this.command = command;
        this.arguments = arguments;
    }

    public void setInputStream(InputStream in) {
        this.stdin = in;
    }

    public void setOutputStream(OutputStream out) {
        this.stdout = new PrintStream(out,true);
    }

    public void setErrorStream(OutputStream err) {
        this.stderr = new PrintStream(err,true);
    }

    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    public void start(Environment env) throws IOException {
        this.env = env;
        thread = new Thread("SSH command: "+command) {
            @Override
            public void run() {
                try {
                    exitCallback.onExit(main());
                } catch (AbortException e) {
                    // signals an error without stack trace
                    stderr.println(e.getMessage());
                    exitCallback.onExit(-1,e.getMessage());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,getName()+" failed",e);
                    e.printStackTrace(new PrintStream(stderr));
                    exitCallback.onExit(-1,e.getMessage());
                }
            }
        };
        thread.start();
    }

    /**
     * Parses arguments with args4j and sets them to this object, then call into {@link #execute()}.
     * See {@link #execute()} method fo the contract of the return value and exceptions.
     */
    protected int main() throws Exception {
        CmdLineParser p = new CmdLineParser(this);
        try {
            p.parseArgument(arguments);
            return execute();
        } catch (CmdLineException e) {
            stderr.println(e.getMessage());
            printUsage(p);
            return -1;
        }
    }

    /**
     * The real meat of the command execution.
     *
     * This method is called in a separate thread after all the initialization is done.
     *
     * @return
     *      0 to indicate a success, otherwise an error code. Used as the exit code of ssh.
     * @throws AbortException
     *      If the processing should be aborted. Hudson will report the error message
     *      without stack trace, and then exits this command.
     * @throws Exception
     *      The problem will be logged, the stack trace reported to stderr, and the client will get a non-zero exit code.
     */
    protected abstract int execute() throws Exception;

    protected void printUsage(CmdLineParser p) {
        stderr.println("ssh host "+getName()+" args...");
        printUsageSummary(stderr);
        p.printUsage(stderr);
    }

    /**
     * Called while producing usage. This is a good method to override
     * to render the general description of the command that goes beyond
     * a single-line summary.
     */
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(getShortDescription());
    }

    public void destroy() {
        thread.interrupt();
    }

    /**
     * Gets the command name.
     *
     * <p>
     * For example, if the CLI is invoked as <tt>ssh hudson foo bar zot</tt>,
     * on the server side {@link LightCLICommand} that returns "foo" from {@link #getName()}
     * will be invoked.
     *
     * <p>
     * By default, this method creates "foo-bar-zot" from "FooBarZotCommand".
     */
    public String getName() {
        String name = getClass().getName();
        name = name.substring(name.lastIndexOf('.')+1); // short name
        name = name.substring(name.lastIndexOf('$')+1);
        if(name.endsWith("Command"))
            name = name.substring(0,name.length()-7); // trim off the command

        // convert "FooBarZot" into "foo-bar-zot"
        // Locale is fixed so that "CreateInstance" always become "create-instance" no matter where this is run.
        return name.replaceAll("([a-z0-9])([A-Z])","$1-$2").toLowerCase(Locale.ENGLISH);
    }

    /**
     * Creates a clone to be used to execute a command.
     */
    protected LightCLICommand createClone() {
        try {
            return getClass().newInstance();
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns all the registered {@link LightCLICommand}s.
     */
    public static ExtensionList<LightCLICommand> all() {
        return Hudson.getInstance().getExtensionList(LightCLICommand.class);
    }

    /**
     * Obtains a copy of the command for invocation.
     */
    public static LightCLICommand clone(String name) {
        for (LightCLICommand cmd : all())
            if(name.equals(cmd.getName()))
                return cmd.createClone();
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(LightCLICommand.class.getName());
}
