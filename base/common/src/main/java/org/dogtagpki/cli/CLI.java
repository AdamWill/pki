// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2012 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---

package org.dogtagpki.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;

import com.netscape.certsrv.client.ClientConfig;
import com.netscape.certsrv.client.PKIClient;


/**
 * @author Endi S. Dewata
 */
public class CLI {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CLI.class);

    public static boolean verbose;

    public static CommandLineParser parser = new DefaultParser();
    public static HelpFormatter formatter = new HelpFormatter();

    public String name;
    public String description;
    public CLI parent;

    public Options options = new Options();
    public Map<String, CLIModule> modules = new LinkedHashMap<>();

    public PKIClient client;

    public CLI(String name, String description) {
        this(name, description, null);
    }

    public CLI(String name, String description, CLI parent) {
        this.name = name;
        this.description = description;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return parent == null ? name : parent.getFullName() + "-" + name;
    }

    public String getFullModuleName(String moduleName) {
        return getFullName() + "-" + moduleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CLI getParent() {
        return parent;
    }

    public CLI getRoot() {
        if (parent != null) return parent.getRoot();
        return this;
    }

    public boolean isDeprecated() {
        return getClass().getAnnotation(Deprecated.class) != null;
    }

    public Collection<CLIModule> getModules() {
        return modules.values();
    }

    public CLIModule getModule(String name) {
        return modules.get(name);
    }

    public void addModule(CLI cli) {
        CLIModule module = new CLIModule(this, cli);
        modules.put(cli.getName(), module);
    }

    public void addModule(String name, String className) {
        CLIModule module = new CLIModule(this, className);
        modules.put(name, module);
    }

    public CLIModule removeModule(String name) {
        return modules.remove(name);
    }

    /**
     * Find the list of modules that handle the specified command.
     */
    public List<CLIModule> findModules(String command) throws Exception {

        List<CLIModule> results = new ArrayList<>();

        // split command into list of names:
        // <names[0]>-<names[1]>-<names[2]>-...-<names[n-1]>
        String[] names = command.split("-");

        CLI current = this;
        int i = 0;

        // translate all names into modules starting from the beginning
        while (i < names.length) {

            String moduleName = null;
            CLIModule module = null;
            int j = i;

            // find module that matches the shortest sequence of names
            while (j < names.length) {

                // construct module name
                if (moduleName == null) {
                    moduleName = names[j];
                } else {
                    moduleName = moduleName + "-" + names[j];
                }

                // find module with name <names[i]>-...-<names[j]>
                module = current.getModule(moduleName);

                if (module != null) {
                    // module found, stop
                    break;
                }

                // try again with longer sequence
                j++;
            }

            if (module == null)
                throw new Exception("Invalid module \"" + moduleName + "\".");

            // module found
            results.add(module);

            // repeat for the remaining parts
            current = module.getCLI();
            i = j + 1;
        }

        return results;
    }

    /**
     * Find the last module that handles the specified command.
     */
    public CLIModule findModule(String command) throws Exception {
        List<CLIModule> modules = findModules(command);
        return modules.get(modules.size() - 1);
    }

    public String getManPage() {
        return null;
    }

    public ClientConfig getConfig() throws Exception {
        if (parent != null) return parent.getConfig();
        return null;
    }

    public PKIClient getClient() throws Exception {
        if (parent != null) return parent.getClient();
        return null;
    }

    public Object getClient(String name) throws Exception {
        if (parent != null) return parent.getClient(name);
        return null;
    }

    public void printHelp() throws Exception {

        int leftPadding = 1;
        int rightPadding = 35;

        System.out.println("Commands:");

        for (CLIModule module : modules.values()) {
            CLI cli = module.getCLI();
            if (cli.isDeprecated()) continue;

            String label = cli.getFullName();

            int padding = rightPadding - leftPadding - label.length();
            if (padding < 1)
                padding = 1;

            System.out.print(StringUtils.repeat(" ", leftPadding));
            System.out.print(label);
            System.out.print(StringUtils.repeat(" ", padding));
            System.out.println(cli.getDescription());
        }

        boolean first = true;

        for (CLIModule module : modules.values()) {
            CLI cli = module.getCLI();
            if (!cli.isDeprecated()) continue;

            if (first) {
                System.out.println();
                System.out.println("Deprecated:");
                first = false;
            }

            String label = cli.getFullName();

            int padding = rightPadding - leftPadding - label.length();
            if (padding < 1)
                padding = 1;

            System.out.print(StringUtils.repeat(" ", leftPadding));
            System.out.print(label);
            System.out.print(StringUtils.repeat(" ", padding));
            System.out.println(cli.getDescription());
        }
    }

    public void execute(String[] args) throws Exception {

        if ((args.length == 0) || (args[0].equals("--help"))) {
            // Print commands associated with this module
            printHelp();
            return;
        }

        // TODO: Rewrite using findModules().

        // A command consists of parts joined by dashes: <part 1>-<part 2>-...-<part N>.
        // For example: cert-request-find
        String command = args[0];

        // The command will be split into module name and sub command, for example:
        //  - module name: cert
        //  - sub command: request-find
        String moduleName = null;
        String subCommand = null;

        // Search the module by incrementally adding parts into module name.
        // Repeat until it finds the module or until there is no more parts to add.
        CLI module = null;
        int position = 0;

        while (true) {

            // Find the next dash.
            int i = command.indexOf('-', position);
            if (i >= 0) {
                // Dash found. Split command into module name and sub command.
                moduleName = command.substring(0, i);
                subCommand = command.substring(i+1);

            } else {
                // Dash not found. Use the whole command.
                moduleName = command;
                subCommand = null;
            }

            // Find module with that name.
            CLIModule m = getModule(moduleName);

            if (m != null) {
                CLI c = m.getCLI();
                // Module found. Check sub command.
                if (subCommand == null) {
                    // No sub command. Use this module.
                    module = c;
                    break;
                }

                // There is a sub command. It must be processed by module's children.
                if (!c.getModules().isEmpty()) {
                    // Module has children. Use this module.
                    module = c;
                    break;
                }

                // Module doesn't have children. Keep looking.
            }

            // If there's no more dashes, stop.
            if (i < 0) break;

            position = i + 1;
        }

        if (module == null) {
            throw new Exception("Invalid module \"" + getFullModuleName(moduleName) + "\".");
        }

        logger.info("Module: " + moduleName);

        // Prepare module arguments.
        String[] moduleArgs;
        if (subCommand != null) {
            // If module command exists, include it as arguments: <module command> <args>...
            moduleArgs = new String[args.length];
            moduleArgs[0] = subCommand;
            System.arraycopy(args, 1, moduleArgs, 1, args.length-1);

        } else {
            // Otherwise, pass the original arguments: <args>...
            moduleArgs = new String[args.length-1];
            System.arraycopy(args, 1, moduleArgs, 0, args.length-1);
        }

        // Add default options to all command modules
        module.options.addOption("v", "verbose", false, "Run in verbose mode.");
        module.options.addOption(null, "debug", false, "Run in debug mode.");
        module.options.addOption(null, "help", false, "Show help message.");

        module.execute(moduleArgs);
    }

    public void runExternal(List<String> command) throws CLIException, IOException, InterruptedException {
        String[] array = command.toArray(new String[command.size()]);
        runExternal(array);
    }

    public void runExternal(String[] command) throws CLIException, IOException, InterruptedException {

        if (logger.isDebugEnabled()) {

            StringBuilder sb = new StringBuilder("Command:");

            for (String c : command) {

                boolean quote = c.contains(" ");

                sb.append(' ');

                if (quote) sb.append('"');
                sb.append(c);
                if (quote) sb.append('"');
            }

            logger.debug(sb.toString());
        }

        Runtime rt = Runtime.getRuntime();
        Process p = rt.exec(command);
        int rc = p.waitFor();

        if (rc != 0) {
            throw new CLIException("Command failed. RC: " + rc, rc);
        }
    }
}
