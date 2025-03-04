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
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cms.authentication;

// ldap java sdk
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;

import org.dogtagpki.server.authentication.AuthManager;
import org.dogtagpki.server.authentication.AuthManagerConfig;
import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authentication.AuthenticationConfig;
import org.mozilla.jss.netscape.security.util.Utils;
import org.mozilla.jss.netscape.security.x509.CertificateExtensions;
import org.mozilla.jss.netscape.security.x509.CertificateSubjectName;
import org.mozilla.jss.netscape.security.x509.CertificateValidity;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CertInfo;

import com.netscape.certsrv.authentication.AuthCredentials;
import com.netscape.certsrv.authentication.EAuthException;
import com.netscape.certsrv.authentication.EFormSubjectDN;
import com.netscape.certsrv.authentication.EInvalidCredentials;
import com.netscape.certsrv.authentication.EMissingCredential;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.EPropertyNotFound;
import com.netscape.certsrv.base.IExtendedPluginInfo;
import com.netscape.certsrv.ldap.ELdapException;
import com.netscape.certsrv.ldap.LdapConnFactory;
import com.netscape.certsrv.profile.EProfileException;
import com.netscape.certsrv.property.IDescriptor;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.EngineConfig;
import com.netscape.cmscore.ldapconn.LDAPAuthenticationConfig;
import com.netscape.cmscore.ldapconn.LDAPConfig;
import com.netscape.cmscore.ldapconn.LdapAnonConnFactory;
import com.netscape.cmscore.ldapconn.LdapBoundConnFactory;
import com.netscape.cmscore.ldapconn.PKISocketConfig;
import com.netscape.cmscore.request.Request;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPEntry;
import netscape.ldap.LDAPException;
import netscape.ldap.LDAPSearchResults;
import netscape.ldap.LDAPv3;

/**
 * Abstract class for directory based authentication managers
 * Uses a pattern for formulating subject names.
 * The pattern is read from configuration file.
 * Syntax of the pattern is described in the init() method.
 *
 * <P>
 *
 * @version $Revision$, $Date$
 */
public abstract class DirBasedAuthentication extends AuthManager implements IExtendedPluginInfo {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DirBasedAuthentication.class);

    protected static final String USER_DN = "userDN";

    /* configuration parameter keys */
    protected static final String PROP_GROUPS_ENABLE = "groupsEnable";
    protected static final String PROP_GROUPS_BASEDN = "groupsBasedn";
    protected static final String PROP_GROUPS = "groups";
    protected static final String PROP_GROUP_OBJECT_CLASS = "groupObjectClass";
    protected static final String PROP_GROUP_USERID_NAME = "groupUseridName";
    protected static final String PROP_USERID_NAME = "useridName";
    protected static final String PROP_SEARCH_GROUP_USER_BY_USERDN = "searchGroupUserByUserdn";
    protected static final String PROP_DNPATTERN = "dnpattern";
    protected static final String PROP_LDAPSTRINGATTRS = "ldapStringAttributes";
    protected static final String PROP_LDAPBYTEATTRS = "ldapByteAttributes";
    protected static final String PROP_LDAP_BOUND_CONN = "ldapBoundConn";

    // members

    /* ldap configuration sub-store */
    protected LDAPConfig mLdapConfig;

    /* ldap base dn */
    protected String mBaseDN = null;
    protected boolean mGroupsEnable = false;
    protected String mGroups = null; // e.g. "ou=Groups"
    protected String mGroupsBaseDN = null; // in case it's different from mBaseDN
    protected String mGroupObjectClass = null;
    protected String mUserIDName = null; // e.g. "uid"
    protected String mGroupUserIDName = null;  // in case it's different from mUserIDName
    /* whether to search for member=<userDN> or member=<mGroupUserIdName>=<uid> */
    protected boolean mSearchGroupUserByUserdn = true;

    protected boolean mBoundConnEnable = false;
    /* factory of anonymous ldap connections */
    protected LdapConnFactory mConnFactory = null;

    /* the subject DN pattern */
    protected DNPattern mPattern = null;

    /* the list of LDAP attributes with string values to retrieve to
     * save in the auth token including ones from the dn pattern. */
    protected String[] mLdapStringAttrs = null;

    /* the list of LDAP attributes with byte[] values to retrive to save
     * in authtoken. */
    protected String[] mLdapByteAttrs = null;

    /* the combined list of LDAP attriubutes to retrieve*/
    protected String[] mLdapAttrs = null;

    /* the password prompt (tag) for the userdn of a bound connection */
    protected String mTag;

    /* default dn pattern if left blank or not set in the config */
    protected static String DEFAULT_DNPATTERN =
            "E=$attr.mail, CN=$attr.cn, O=$dn.o, C=$dn.c";

    /* Vector of extendedPluginInfo strings */
    protected static Vector<String> mExtendedPluginInfo = null;

    static {
        mExtendedPluginInfo = new Vector<>();
        mExtendedPluginInfo.add(PROP_DNPATTERN + ";string;Template for cert" +
                " Subject Name. ($dn.xxx - get value from user's LDAP " +
                "DN.  $attr.yyy - get value from LDAP attributes in " +
                "user's entry.) Default: " + DEFAULT_DNPATTERN);
        mExtendedPluginInfo.add(PROP_LDAPSTRINGATTRS + ";string;" +
                "Comma-separated list of LDAP attributes to copy from " +
                "the user's LDAP entry into the AuthToken. e.g use " +
                "'mail' to copy user's email address for subjectAltName");
        mExtendedPluginInfo.add(PROP_LDAPBYTEATTRS + ";string;" +
                "Comma-separated list of binary LDAP attributes to copy" +
                " from the user's LDAP entry into the AuthToken");
        mExtendedPluginInfo.add("ldap.ldapconn.host;string,required;" +
                "LDAP host to connect to");
        mExtendedPluginInfo.add("ldap.ldapconn.port;number,required;" +
                "LDAP port number (use 389, or 636 if SSL)");
        mExtendedPluginInfo.add("ldap.ldapconn.secureConn;boolean;" +
                "Use SSL to connect to directory?");
        mExtendedPluginInfo.add("ldap.ldapconn.version;choice(3,2);" +
                "LDAP protocol version");
        mExtendedPluginInfo.add("ldap.basedn;string,required;Base DN to start searching " +
                "under. If your user's DN is 'uid=jsmith, o=company', you " +
                "might want to use 'o=company' here");
        mExtendedPluginInfo.add("ldap.minConns;number;number of connections " +
                "to keep open to directory server. Default 5.");
        mExtendedPluginInfo.add("ldap.maxConns;number;when needed, connection " +
                "pool can grow to this many (multiplexed) connections. Default 1000.");
    }

    /**
     * Default constructor, initialization must follow.
     */
    public DirBasedAuthentication() {
    }

    /**
     * Initializes the UidPwdDirBasedAuthentication auth manager.
     *
     * Takes the following configuration parameters: <br>
     *
     * <pre>
     * 	ldap.basedn             - the ldap base dn.
     * 	ldap.ldapconn.host      - the ldap host.
     * 	ldap.ldapconn.port      - the ldap port
     * 	ldap.ldapconn.secureConn - whether port should be secure
     * 	ldap.minConns           - minimum connections
     * 	ldap.maxConns           - max connections
     * 	dnpattern               - dn pattern.
     * </pre>
     * <p>
     * <i><b>dnpattern</b></i> is a string representing a subject name pattern to formulate from the directory
     * attributes and entry dn. If empty or not set, the ldap entry DN will be used as the certificate subject name.
     * <p>
     * The syntax is
     *
     * <pre>
     *     dnpattern = SubjectNameComp *[ "," SubjectNameComp ]
     *
     *     SubjectNameComponent = DnComp | EntryComp | ConstantComp
     *     DnComp = CertAttr "=" "$dn" "." DnAttr "." Num
     *     EntryComp = CertAttr "=" "$attr" "." EntryAttr "." Num
     *     ConstantComp = CertAttr "=" Constant
     *     DnAttr    =  an attribute in the Ldap entry dn
     *     EntryAttr =  an attribute in the Ldap entry
     *     CertAttr  =  a Component in the Certificate Subject Name
     *                  (multiple AVA in one RDN not supported)
     *     Num       =  the nth value of tha attribute  in the dn or entry.
     *     Constant  =  Constant String, with any accepted ldap string value.
     *
     * </pre>
     * <p>
     * <b>Example:</b>
     *
     * <pre>
     * dnpattern:
     *     E=$attr.mail.1, CN=$attr.cn, OU=$attr.ou.2, O=$dn.o, C=US
     * <br>
     * Ldap entry dn:
     *     UID=joesmith, OU=people, O=Acme.com
     * <br>
     * Ldap attributes:
     *     cn: Joe Smith
     *     sn: Smith
     *     mail: joesmith@acme.com
     *     mail: joesmith@redhat.com
     *     ou: people
     *     ou: IS
     *     <i>etc.</i>
     * </pre>
     * <p>
     * The subject name formulated in the cert will be : <br>
     *
     * <pre>
     *   E=joesmith@acme.com, CN=Joe Smith, OU=Human Resources, O=Acme.com, C=US
     *
     *      E = the first 'mail' ldap attribute value in user's entry - joesmithe@acme.com
     *      CN = the (first) 'cn' ldap attribute value in the user's entry - Joe Smith
     *      OU = the second 'ou' value in the ldap entry - IS
     *      O = the (first) 'o' value in the user's entry DN - "Acme.com"
     *      C = the constant string "US"
     * </pre>
     *
     * @param name The name for this authentication manager instance.
     * @param implName The name of the authentication manager plugin.
     * @param config - The configuration store for this instance.
     * @exception EBaseException If an error occurs during initialization.
     */
    @Override
    public void init(
            AuthenticationConfig authenticationConfig,
            String name, String implName, AuthManagerConfig config)
            throws EBaseException {
        init(authenticationConfig, name, implName, config, true);
    }

    public void init(
            AuthenticationConfig authenticationConfig,
            String name, String implName, AuthManagerConfig config, boolean needBaseDN)
            throws EBaseException {

        logger.info("DirBasedAuthentication: Initializing " + name);

        this.authenticationConfig = authenticationConfig;
        mName = name;
        mImplName = implName;
        mConfig = config;
        String method = "DirBasedAuthentication: init: ";

        EngineConfig cs = engine.getConfig();

        /* initialize ldap server configuration */
        mLdapConfig = mConfig.getLDAPConfig();

        if (needBaseDN) {
            mBaseDN = mLdapConfig.getBaseDN();
            if (mBaseDN == null || mBaseDN.trim().equals(""))
                throw new EPropertyNotFound(CMS.getUserMessage("CMS_BASE_GET_PROPERTY_FAILED", "basedn"));

            mGroupsEnable = mLdapConfig.getBoolean(PROP_GROUPS_ENABLE, false);
            logger.info("DirBasedAuthentication: Groups enable: " + mGroupsEnable);

            mGroupsBaseDN = mLdapConfig.getString(PROP_GROUPS_BASEDN, mBaseDN);
            logger.info("DirBasedAuthentication: Groups base DN: " + mGroupsBaseDN);

            mGroups= mLdapConfig.getString(PROP_GROUPS, "ou=groups");
            logger.info("DirBasedAuthentication: Groups: " + mGroups);

            mGroupObjectClass = mLdapConfig.getString(PROP_GROUP_OBJECT_CLASS, "groupofuniquenames");
            logger.info("DirBasedAuthentication: Group object class: " + mGroupObjectClass);

            mUserIDName = mLdapConfig.getString(PROP_USERID_NAME, "uid");
            logger.info("DirBasedAuthentication: User ID name: " + mUserIDName);

            mSearchGroupUserByUserdn = mLdapConfig.getBoolean(PROP_SEARCH_GROUP_USER_BY_USERDN, true);
            logger.info("DirBasedAuthentication: Search group user by user DN: " + mSearchGroupUserByUserdn);

            mGroupUserIDName = mLdapConfig.getString(PROP_GROUP_USERID_NAME, "cn");
            logger.info("DirBasedAuthentication: Group user ID name: " + mGroupUserIDName);
        }

        PKISocketConfig socketConfig = cs.getSocketConfig();

        mBoundConnEnable = mLdapConfig.getBoolean(PROP_LDAP_BOUND_CONN, false);
        logger.info("DirBasedAuthentication: Bound connection enable: " + mBoundConnEnable);

        if (mBoundConnEnable) {
            LDAPAuthenticationConfig authConfig = mLdapConfig.getAuthenticationConfig();
            mTag = mLdapConfig.getString("bindPWPrompt");
            logger.info("DirBasedAuthentication: Bind password prompt: " + mTag);

            LdapBoundConnFactory connFactory = new LdapBoundConnFactory(mTag);
            connFactory.setCMSEngine(engine);
            connFactory.init(socketConfig, mLdapConfig, engine.getPasswordStore());
            mConnFactory = connFactory;

        } else {
            LdapAnonConnFactory connFactory = new LdapAnonConnFactory("DirBasedAuthentication");
            connFactory.setCMSEngine(engine);
            connFactory.init(socketConfig, mLdapConfig);
            mConnFactory = connFactory;
        }

        /* initialize dn pattern */
        String pattern = mConfig.getString(PROP_DNPATTERN, null);

        if (pattern == null || pattern.length() == 0)
            pattern = DEFAULT_DNPATTERN;

        logger.info("DirBasedAuthentication: DN pattern: " + pattern);

        mPattern = new DNPattern(pattern);
        String[] patternLdapAttrs = mPattern.getLdapAttrs();

        /* initialize ldap string attribute list */
        String ldapStringAttrs = mConfig.getString(PROP_LDAPSTRINGATTRS, null);

        if (ldapStringAttrs == null) {
            mLdapStringAttrs = patternLdapAttrs;

        } else {
            StringTokenizer pAttrs = new StringTokenizer(ldapStringAttrs, ",", false);
            int begin = 0;

            if (patternLdapAttrs != null && patternLdapAttrs.length > 0) {
                mLdapStringAttrs = new String[patternLdapAttrs.length + pAttrs.countTokens()];
                System.arraycopy(patternLdapAttrs, 0, mLdapStringAttrs, 0, patternLdapAttrs.length);
                begin = patternLdapAttrs.length;
            } else {
                mLdapStringAttrs = new String[pAttrs.countTokens()];
            }

            for (int i = begin; i < mLdapStringAttrs.length; i++) {
                mLdapStringAttrs[i] = ((String) pAttrs.nextElement()).trim();
            }
        }

        logger.debug("DirBasedAuthentication: String attributes:");
        for (String attr : mLdapStringAttrs) {
            logger.debug("DirBasedAuthentication: - " + attr);
        }

        /* initialize ldap byte[] attribute list */
        String ldapByteAttrs = mConfig.getString(PROP_LDAPBYTEATTRS, null);

        if (ldapByteAttrs == null) {
            mLdapByteAttrs = new String[0];
        } else {
            StringTokenizer byteAttrs = new StringTokenizer(ldapByteAttrs, ",", false);

            mLdapByteAttrs = new String[byteAttrs.countTokens()];
            for (int j = 0; j < mLdapByteAttrs.length; j++) {
                mLdapByteAttrs[j] = ((String) byteAttrs.nextElement()).trim();
            }
        }

        logger.debug("DirBasedAuthentication: Byte attributes:");
        for (String attr : mLdapByteAttrs) {
            logger.debug("DirBasedAuthentication: - " + attr);
        }

        /* make the combined list */
        mLdapAttrs = new String[mLdapStringAttrs.length + mLdapByteAttrs.length];
        System.arraycopy(mLdapStringAttrs, 0, mLdapAttrs, 0, mLdapStringAttrs.length);
        System.arraycopy(mLdapByteAttrs, 0, mLdapAttrs, mLdapStringAttrs.length, mLdapByteAttrs.length);

        logger.info("DirBasedAuthentication: Initialization complete");
    }

    @Override
    public String getText(Locale locale) {
        return null;
    }

    @Override
    public Enumeration<String> getValueNames() {
        return null;
    }

    @Override
    public IDescriptor getValueDescriptor(Locale locale, String name) {
        return null;
    }

    @Override
    public boolean isValueWriteable(String name) {
        return false;
    }

    @Override
    public boolean isSSLClientRequired() {
        return false;
    }

    /**
     * Authenticates user through LDAP by a set of credentials.
     * Resulting AuthToken a TOKEN_CERTINFO field of a X509CertInfo
     * <p>
     *
     * @param authCred Authentication credentials, CRED_UID and CRED_PWD.
     * @return A AuthToken with a TOKEN_SUBJECT of X500name type.
     * @exception com.netscape.certsrv.authentication.EMissingCredential
     *                If a required authentication credential is missing.
     * @exception com.netscape.certsrv.authentication.EInvalidCredentials
     *                If credentials failed authentication.
     * @exception com.netscape.certsrv.base.EBaseException
     *                If an internal error occurred.
     * @see org.dogtagpki.server.authentication.AuthToken
     */
    @Override
    public AuthToken authenticate(AuthCredentials authCred)
            throws EMissingCredential, EInvalidCredentials, EBaseException {

        String userdn = null;
        LDAPConnection conn = null;
        AuthToken authToken = new AuthToken(this);
        String method = "DirBasedAuthentication: authenticate:";

        logger.debug(method + " begins...mBoundConnEnable=" + mBoundConnEnable);

        EngineConfig cs = engine.getConfig();

        PKISocketConfig socketConfig = cs.getSocketConfig();

        try {
            if (mConnFactory == null) {
                logger.debug(method + " mConnFactory null, getting conn factory");

                if (mBoundConnEnable) {
                    LDAPAuthenticationConfig authConfig = mLdapConfig.getAuthenticationConfig();
                    mTag = authConfig.getString("bindPWPrompt");
                    logger.debug(method + " getting ldap bound conn factory using id= " + mTag);

                    LdapBoundConnFactory connFactory = new LdapBoundConnFactory(mTag);
                    connFactory.setCMSEngine(engine);
                    connFactory.init(socketConfig, mLdapConfig, engine.getPasswordStore());
                    mConnFactory = connFactory;

                } else {
                    LdapAnonConnFactory connFactory = new LdapAnonConnFactory("DirBasedAuthentication");
                    connFactory.setCMSEngine(engine);
                    connFactory.init(socketConfig, mLdapConfig);
                    mConnFactory = connFactory;
                }

                if (mConnFactory != null) {
                    logger.debug(method + " mConnFactory gotten, calling getConn");
                    conn = mConnFactory.getConn();
                }
            } else {
                logger.debug(method + " mConnFactory class name = " + mConnFactory.getClass().getName());
                logger.debug(method + " mConnFactory not null, calling getConn");
                conn = mConnFactory.getConn();
            }

            // authenticate the user and get a user entry.
            logger.debug(method + " before authenticate() call");
            userdn = authenticate(conn, authCred, authToken);
            logger.debug(method + " after authenticate() call");
            authToken.set(USER_DN, userdn);

            // formulate the cert info.
            // set each seperatly since otherwise they won't serialize
            // in the request queue.
            X509CertInfo certInfo = new X509CertInfo();

            formCertInfo(conn, userdn, certInfo, authToken);

            // set subject name.
            try {
                CertificateSubjectName subjectname = (CertificateSubjectName)
                        certInfo.get(X509CertInfo.SUBJECT);

                if (subjectname != null)
                    authToken.set(AuthToken.TOKEN_CERT_SUBJECT,
                            subjectname.toString());
            } // error means it's not set.
            catch (CertificateException e) {
            } catch (IOException e) {
            }

            // set validity if any
            try {
                CertificateValidity validity = (CertificateValidity)
                        certInfo.get(X509CertInfo.VALIDITY);

                if (validity != null) {
                    // the gets throws IOException but only if attribute
                    // not recognized. In these cases they are always.
                    authToken.set(AuthToken.TOKEN_CERT_NOTBEFORE,
                            (Date) validity.get(CertificateValidity.NOT_BEFORE));
                    authToken.set(AuthToken.TOKEN_CERT_NOTAFTER,
                            (Date) validity.get(CertificateValidity.NOT_AFTER));
                }
            } // error means it's not set.
            catch (CertificateException e) {
            } catch (IOException e) {
            }

            // set extensions if any.
            try {
                CertificateExtensions extensions = (CertificateExtensions)
                        certInfo.get(X509CertInfo.EXTENSIONS);

                if (extensions != null)
                    authToken.set(AuthToken.TOKEN_CERT_EXTENSIONS, extensions);
            } // error means it's not set.
            catch (CertificateException e) {
            } catch (IOException e) {
            }

        } finally {
            if (conn != null)
                mConnFactory.returnConn(conn);
        }

        return authToken;
    }

    @Override
    public void populate(AuthToken token, Request request) throws EProfileException {
    }

    /**
     * get the list of required credentials.
     *
     * @return list of required credentials as strings.
     */
    @Override
    public abstract String[] getRequiredCreds();

    /**
     * disconnects the ldap connections
     */
    @Override
    public void shutdown() {
        try {
            if (mConnFactory != null) {
                mConnFactory.reset();
            }
        } catch (ELdapException e) {
            // ignore
            logger.warn("DirBasedAuthentication: " + CMS.getLogMessage("CMS_AUTH_SHUTDOWN_ERROR", e.toString()), e);
        }
    }

    /**
     * Authenticates a user through directory based a set of credentials.
     *
     * @param authCreds The authentication credentials.
     * @return The user's ldap entry dn.
     * @exception EInvalidCredentials If the uid and password are not valid
     * @exception EBaseException If an internal error occurs.
     */
    protected abstract String authenticate(
            LDAPConnection conn, AuthCredentials authCreds, AuthToken token)
            throws EBaseException;

    /**
     * Formulate the cert info.
     *
     * @param conn A LDAP Connection authenticated to user to use.
     * @param userdn The user's dn.
     * @param certinfo A certinfo object to fill.
     * @param token A authentication token to fill.
     * @exception EBaseException If an internal error occurs.
     */
    protected void formCertInfo(LDAPConnection conn,
            String userdn,
            X509CertInfo certinfo,
            AuthToken token)
            throws EBaseException {

        String dn = null;

        // retrieve the attributes.
        try {
            if (conn != null) {
                logger.info("DirBasedAuthentication: Searching for " + userdn);

                String[] attrs = getLdapAttrs();
                if (attrs == null) {
                    logger.info("DirBasedAuthentication: - no attributes found");
                } else {
                    logger.info("DirBasedAuthentication: - attributes:");
                    for (String attr : attrs) {
                        logger.info("DirBasedAuthentication:   - " + attr);
                    }
                }

                LDAPSearchResults results = conn.search(
                        userdn,
                        LDAPv3.SCOPE_BASE,
                        "(objectClass=*)",
                        attrs,
                        false);

                if (!results.hasMoreElements()) {
                    logger.error("DirBasedAuthentication: Unable to find user: " + userdn);
                    throw new EAuthException(CMS.getUserMessage("CMS_AUTHENTICATION_LDAPATTRIBUTES_NOT_FOUND"));
                }

                LDAPEntry entry = results.next();

                // formulate the subject dn
                dn = formSubjectName(entry);
                logger.info("DirBasedAuthentication: DN: " + dn);

                // Put selected values from the entry into the token
                setAuthTokenValues(entry, token);

            } else {
                dn = userdn;
            }

            // add anything else in cert info such as validity, extensions
            // (nothing now)

            // pack the dn into X500name and set subject name.
            if (dn.length() == 0) {
                EBaseException ex = new EAuthException(CMS.getUserMessage("CMS_AUTHENTICATION_EMPTY_DN_FORMED", mName));
                logger.error("DirBasedAuthentication: " + CMS.getLogMessage("CMS_AUTH_NO_DN_ERROR", ex.toString()));
                throw ex;
            }

            X500Name subjectdn = new X500Name(dn);
            certinfo.set(X509CertInfo.SUBJECT, new CertificateSubjectName(subjectdn));

        } catch (LDAPException e) {
            switch (e.getLDAPResultCode()) {
            case LDAPException.SERVER_DOWN:
                logger.error("DirBasedAuthentication: " + CMS.getLogMessage("CMS_AUTH_NO_AUTH_ATTR_ERROR"));
                throw new ELdapException(
                        CMS.getUserMessage("CMS_LDAP_SERVER_UNAVAILABLE", conn.getHost(), "" + conn.getPort()));

            case LDAPException.NO_SUCH_OBJECT:
            case LDAPException.LDAP_PARTIAL_RESULTS:
                logger.error("DirBasedAuthentication: " + CMS.getLogMessage("CMS_AUTH_NO_USER_ENTRY_ERROR", userdn));

                // fall to below.
            default:
                logger.error("DirBasedAuthentication: " + CMS.getLogMessage("LDAP_ERROR", e.toString()));
                throw new ELdapException(
                        CMS.getUserMessage("CMS_LDAP_OTHER_LDAP_EXCEPTION",
                                e.errorCodeToString()));
            }

        } catch (IOException e) {
            logger.error("DirBasedAuthentication: " + CMS.getLogMessage("CMS_AUTH_CREATE_SUBJECT_ERROR", userdn, e.getMessage()), e);
            throw new EFormSubjectDN(CMS.getUserMessage("CMS_AUTHENTICATION_FORM_SUBJECTDN_ERROR"));

        } catch (CertificateException e) {
            logger.error("DirBasedAuthentication: " + CMS.getLogMessage("CMS_AUTH_CREATE_CERTINFO_ERROR", userdn, e.getMessage()));
            throw new EFormSubjectDN(CMS.getUserMessage("CMS_AUTHENTICATION_FORM_SUBJECTDN_ERROR"));
        }
    }

    /**
     * Copy values from the LDAPEntry into the AuthToken. The
     * list of values that should be store this way is given in
     * a the ldapAttributes configuration parameter.
     */
    protected void setAuthTokenValues(LDAPEntry e, AuthToken tok) {
        for (int i = 0; i < mLdapStringAttrs.length; i++)
            setAuthTokenStringValue(mLdapStringAttrs[i], e, tok);
        for (int j = 0; j < mLdapByteAttrs.length; j++)
            setAuthTokenByteValue(mLdapByteAttrs[j], e, tok);
    }

    protected void setAuthTokenStringValue(
            String name, LDAPEntry entry, AuthToken tok) {
        LDAPAttribute values = entry.getAttribute(name);

        if (values == null)
            return;

        Vector<String> v = new Vector<>();
        Enumeration<String> e = values.getStringValues();

        while (e.hasMoreElements()) {
            v.addElement(e.nextElement());
        }

        String a[] = new String[v.size()];

        v.copyInto(a);

        tok.set(name, a);
    }

    protected void setAuthTokenByteValue(
            String name, LDAPEntry entry, AuthToken tok) {
        LDAPAttribute values = entry.getAttribute(name);

        if (values == null)
            return;

        Vector<byte[]> v = new Vector<>();
        Enumeration<byte[]> e = values.getByteValues();

        while (e.hasMoreElements()) {
            v.addElement(e.nextElement());
        }

        byte[][] a = new byte[v.size()][];

        v.copyInto(a);

        tok.set(name, a);
    }

    /**
     * Return a list of LDAP attributes with String values to retrieve.
     * Subclasses can override to return any set of attributes.
     *
     * @return Array of LDAP attributes to retrieve from the directory.
     */
    protected String[] getLdapAttrs() {
        return mLdapAttrs;
    }

    /**
     * Return a list of LDAP attributes with byte[] values to retrieve.
     * Subclasses can override to return any set of attributes.
     *
     * @return Array of LDAP attributes to retrieve from the directory.
     */
    protected String[] getLdapByteAttrs() {
        return mLdapByteAttrs;
    }

    /**
     * Formulate the subject name
     *
     * @param entry The LDAP entry
     * @return The subject name string.
     * @exception EBaseException If an internal error occurs.
     */
    protected String formSubjectName(LDAPEntry entry) throws EAuthException {

        if (mPattern.mPatternString == null) {
            return entry.getDN();
        }

        /*
         if (mTestDNString != null) {
             logger.debug("DirBasedAuthentication: setting DNPattern.mTestDN to " + mPattern.mTestDN);
             mPattern.mTestDN = mTestDNString;
        }
        */

        return mPattern.formDN(entry);
    }

    @Override
    public String[] getExtendedPluginInfo() {
        return Utils.getStringArrayFromVector(mExtendedPluginInfo);
    }

}
