//
// Copyright Red Hat, Inc.
//
// SPDX-License-Identifier: GPL-2.0-or-later
//
package com.netscape.cmstools.system;

import org.apache.commons.cli.CommandLine;
import org.dogtagpki.cli.CommandCLI;

import com.netscape.certsrv.system.SecurityDomainClient;
import com.netscape.certsrv.system.SecurityDomainHost;
import com.netscape.cmstools.cli.MainCLI;

/**
 * @author Endi S. Dewata
 */
public class SecurityDomainHostShowCLI extends CommandCLI {

    public SecurityDomainHostCLI securityDomainHostCLI;

    public SecurityDomainHostShowCLI(SecurityDomainHostCLI securityDomainHostCLI) {
        super("show", "Show security domain host", securityDomainHostCLI);
        this.securityDomainHostCLI = securityDomainHostCLI;
    }

    public void printHelp() {
        formatter.printHelp(getFullName() + " [OPTIONS...] <Host ID>", options);
    }

    public void execute(CommandLine cmd) throws Exception {

        String[] cmdArgs = cmd.getArgs();

        if (cmdArgs.length < 1) {
            throw new Exception("Missing host ID");
        }

        String hostID = cmdArgs[0];

        MainCLI mainCLI = (MainCLI) getRoot();
        mainCLI.init();

        SecurityDomainClient securityDomainClient = securityDomainHostCLI.getSecurityDomainClient();
        SecurityDomainHost host = securityDomainClient.getHost(hostID);

        SecurityDomainHostCLI.printSecurityDomainHost(host);
    }
}
