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
package com.netscape.cms.profile.def;

//ldap java sdk
import java.io.IOException;
import java.util.Locale;
import java.util.StringTokenizer;

import org.dogtagpki.server.ca.CAEngine;
import org.dogtagpki.server.ca.CAEngineConfig;
import org.mozilla.jss.netscape.security.x509.CertificateSubjectName;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CertInfo;

import com.netscape.certsrv.profile.EProfileException;
import com.netscape.certsrv.property.Descriptor;
import com.netscape.certsrv.property.EPropertyException;
import com.netscape.certsrv.property.IDescriptor;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ConfigStore;
import com.netscape.cmscore.ldapconn.LDAPConfig;
import com.netscape.cmscore.ldapconn.LdapAnonConnFactory;
import com.netscape.cmscore.ldapconn.PKISocketConfig;
import com.netscape.cmscore.request.Request;
import com.netscape.cmsutil.ldap.LDAPUtil;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPEntry;
import netscape.ldap.LDAPSearchResults;
import netscape.ldap.LDAPv3;

/**
 * This class implements an enrollment default policy
 * that populates server-side configurable subject name
 * into the certificate template.
 *
 * @version $Revision$, $Date$
 */
public class nsTokenUserKeySubjectNameDefault extends EnrollDefault {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(nsTokenUserKeySubjectNameDefault.class);

    public static final String PROP_LDAP = "ldap";
    public static final String PROP_PARAMS = "params";
    public static final String CONFIG_DNPATTERN = "dnpattern";
    public static final String CONFIG_LDAP_ENABLE = "ldap.enable";
    public static final String CONFIG_LDAP_SEARCH_NAME = "ldap.searchName";
    public static final String CONFIG_LDAP_STRING_ATTRS = "ldapStringAttributes";
    public static final String CONFIG_LDAP_HOST = "ldap.ldapconn.host";
    public static final String CONFIG_LDAP_PORT = "ldap.ldapconn.port";
    public static final String CONFIG_LDAP_SEC_CONN = "ldap.ldapconn.secureConn";
    public static final String CONFIG_LDAP_VER = "ldap.ldapconn.Version";
    public static final String CONFIG_LDAP_BASEDN = "ldap.basedn";
    public static final String CONFIG_LDAP_MIN_CONN = "ldap.minConns";
    public static final String CONFIG_LDAP_MAX_CONN = "ldap.maxConns";

    public static final String VAL_NAME = "name";

    public static final String CONFIG_LDAP_VERS =
            "2,3";

    /* default dn pattern if left blank or not set in the config */
    protected static String DEFAULT_DNPATTERN =
            "CN=$request.uid$, E=$request.mail$";

    /* ldap configuration sub-store */
    boolean mldapInitialized = false;
    boolean mldapEnabled = false;
    protected ConfigStore mInstConfig;
    protected LDAPConfig mLdapConfig;
    protected ConfigStore mParamsConfig;

    /* ldap base dn */
    protected String mBaseDN = null;

    protected LdapAnonConnFactory mConnFactory;

    /* the list of LDAP attributes with string values to retrieve to
     * form the subject dn. */
    protected String[] mLdapStringAttrs = null;

    public nsTokenUserKeySubjectNameDefault() {
        super();
        addConfigName(CONFIG_DNPATTERN);
        addConfigName(CONFIG_LDAP_ENABLE);
        addConfigName(CONFIG_LDAP_SEARCH_NAME);
        addConfigName(CONFIG_LDAP_STRING_ATTRS);
        addConfigName(CONFIG_LDAP_HOST);
        addConfigName(CONFIG_LDAP_PORT);
        addConfigName(CONFIG_LDAP_SEC_CONN);
        addConfigName(CONFIG_LDAP_VER);
        addConfigName(CONFIG_LDAP_BASEDN);
        addConfigName(CONFIG_LDAP_MIN_CONN);
        addConfigName(CONFIG_LDAP_MAX_CONN);

        addValueName(CONFIG_DNPATTERN);
        addValueName(CONFIG_LDAP_ENABLE);
        addValueName(CONFIG_LDAP_SEARCH_NAME);
        addValueName(CONFIG_LDAP_STRING_ATTRS);
        addValueName(CONFIG_LDAP_HOST);
        addValueName(CONFIG_LDAP_PORT);
        addValueName(CONFIG_LDAP_SEC_CONN);
        addValueName(CONFIG_LDAP_VER);
        addValueName(CONFIG_LDAP_BASEDN);
        addValueName(CONFIG_LDAP_MIN_CONN);
        addValueName(CONFIG_LDAP_MAX_CONN);
    }

    @Override
    public void init(CAEngineConfig engineConfig, ConfigStore config) throws EProfileException {
        super.init(engineConfig, config);
        mInstConfig = config;
    }

    @Override
    public IDescriptor getConfigDescriptor(Locale locale, String name) {
        logger.debug("nsTokenUserKeySubjectNameDefault: in getConfigDescriptor, name=" + name);
        if (name.equals(CONFIG_DNPATTERN)) {
            return new Descriptor(IDescriptor.STRING,
                    null, null, CMS.getUserMessage(locale,
                            "CMS_PROFILE_SUBJECT_NAME"));
        } else if (name.equals(CONFIG_LDAP_STRING_ATTRS)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_STRING_ATTRS"));
        } else if (name.equals(CONFIG_LDAP_ENABLE)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_ENABLE"));
        } else if (name.equals(CONFIG_LDAP_SEARCH_NAME)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_SEARCH_NAME"));
        } else if (name.equals(CONFIG_LDAP_HOST)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_HOST_NAME"));
        } else if (name.equals(CONFIG_LDAP_PORT)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_PORT_NUMBER"));
        } else if (name.equals(CONFIG_LDAP_SEC_CONN)) {
            return new Descriptor(IDescriptor.BOOLEAN,
                    null,
                    "false",
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_SECURE_CONN"));
        } else if (name.equals(CONFIG_LDAP_VER)) {
            return new Descriptor(IDescriptor.CHOICE, CONFIG_LDAP_VERS,
                    "3",
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_VERSION"));
        } else if (name.equals(CONFIG_LDAP_BASEDN)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_BASEDN"));
        } else if (name.equals(CONFIG_LDAP_MIN_CONN)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_MIN_CONN"));
        } else if (name.equals(CONFIG_LDAP_MAX_CONN)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_TOKENKEY_LDAP_MAX_CONN"));
        } else {
            return null;
        }
    }

    @Override
    public IDescriptor getValueDescriptor(Locale locale, String name) {
        logger.debug("nsTokenUserKeySubjectNameDefault: in getValueDescriptor name=" + name);

        if (name.equals(VAL_NAME)) {
            return new Descriptor(IDescriptor.STRING,
                    null,
                    null,
                    CMS.getUserMessage(locale,
                            "CMS_PROFILE_SUBJECT_NAME"));
        } else {
            return null;
        }
    }

    @Override
    public void setValue(String name, Locale locale,
            X509CertInfo info, String value)
            throws EPropertyException {

        logger.debug("nsTokenUserKeySubjectNameDefault: in setValue, value=" + value);

        if (name == null) {
            throw new EPropertyException(CMS.getUserMessage(
                        locale, "CMS_INVALID_PROPERTY", name));
        }
        if (name.equals(VAL_NAME)) {
            X500Name x500name = null;

            try {
                x500name = new X500Name(value);
            } catch (IOException e) {
                logger.warn("nsTokenUserKeySubjectNameDefault: setValue " + e.getMessage(), e);
                // failed to build x500 name
            }
            logger.debug("nsTokenUserKeySubjectNameDefault: setValue name=" + x500name);
            try {
                info.set(X509CertInfo.SUBJECT,
                        new CertificateSubjectName(x500name));
            } catch (Exception e) {
                // failed to insert subject name
                logger.error("nsTokenUserKeySubjectNameDefault: setValue " + e.getMessage(), e);
                throw new EPropertyException(CMS.getUserMessage(
                            locale, "CMS_INVALID_PROPERTY", name));
            }
        } else {
            throw new EPropertyException(CMS.getUserMessage(
                        locale, "CMS_INVALID_PROPERTY", name));
        }
    }

    @Override
    public String getValue(String name, Locale locale,
            X509CertInfo info)
            throws EPropertyException {
        logger.debug("nsTokenUserKeySubjectNameDefault: in getValue, name=" + name);
        if (name == null) {
            throw new EPropertyException(CMS.getUserMessage(
                        locale, "CMS_INVALID_PROPERTY", name));
        }
        if (name.equals(VAL_NAME)) {
            CertificateSubjectName sn = null;

            try {
                logger.debug("nsTokenUserKeySubjectNameDefault: getValue info=" + info);
                sn = (CertificateSubjectName)
                        info.get(X509CertInfo.SUBJECT);
                logger.debug("nsTokenUserKeySubjectNameDefault: getValue name=" + sn);
                return sn.toString();
            } catch (Exception e) {
                // nothing
                logger.warn("nsTokenUserKeySubjectNameDefault: getValue " + e.getMessage(), e);

            }
            throw new EPropertyException(CMS.getUserMessage(
                        locale, "CMS_INVALID_PROPERTY", name));
        } else {
            throw new EPropertyException(CMS.getUserMessage(
                        locale, "CMS_INVALID_PROPERTY", name));
        }
    }

    @Override
    public String getText(Locale locale) {
        logger.debug("nsTokenUserKeySubjectNameDefault: in getText");
        return CMS.getUserMessage(locale, "CMS_PROFILE_SUBJECT_NAME",
                getConfig(CONFIG_DNPATTERN));
    }

    public void ldapInit()
            throws EProfileException {
        if (mldapInitialized == true)
            return;

        logger.debug("nsTokenUserKeySubjectNameDefault: ldapInit(): begin");

        CAEngine engine = CAEngine.getInstance();
        CAEngineConfig cs = engine.getConfig();

        PKISocketConfig socketConfig = cs.getSocketConfig();

        try {
            // cfu - XXX do more error handling here later
            /* initialize ldap server configuration */
            mParamsConfig = mInstConfig.getSubStore(PROP_PARAMS, ConfigStore.class);
            mLdapConfig = mParamsConfig.getSubStore(PROP_LDAP, LDAPConfig.class);
            mldapEnabled = mParamsConfig.getBoolean(CONFIG_LDAP_ENABLE,
                    false);
            if (mldapEnabled == false)
                return;

            mBaseDN = mParamsConfig.getString(CONFIG_LDAP_BASEDN, null);

            mConnFactory = new LdapAnonConnFactory("nsTokenUserKeySubjectNameDefault");
            mConnFactory.setCMSEngine(engine);
            mConnFactory.init(socketConfig, mLdapConfig);

            /* initialize dn pattern */
            String pattern = mParamsConfig.getString(CONFIG_DNPATTERN, null);

            if (pattern == null || pattern.length() == 0)
                pattern = DEFAULT_DNPATTERN;

            /* initialize ldap string attribute list */
            String ldapStringAttrs = mParamsConfig.getString(CONFIG_LDAP_STRING_ATTRS, null);

            if ((ldapStringAttrs != null) && (ldapStringAttrs.length() != 0)) {
                StringTokenizer pAttrs =
                        new StringTokenizer(ldapStringAttrs, ",", false);

                mLdapStringAttrs = new String[pAttrs.countTokens()];

                for (int i = 0; i < mLdapStringAttrs.length; i++) {
                    mLdapStringAttrs[i] = ((String) pAttrs.nextElement()).trim();
                }
            }
            logger.debug("nsTokenUserKeySubjectNameDefault: ldapInit(): done");
            mldapInitialized = true;
        } catch (Exception e) {
            logger.error("nsTokenUserKeySubjectNameDefault: ldapInit(): " + e.getMessage(), e);
            // throw EProfileException...
            throw new EProfileException("ldap init failure: " + e.toString());
        }
    }

    /**
     * Populates the request with this policy default.
     */
    @Override
    public void populate(Request request, X509CertInfo info)
            throws EProfileException {
        X500Name name = null;
        logger.debug("nsTokenUserKeySubjectNameDefault: in populate");
        ldapInit();
        try {
            // cfu - this goes to ldap
            String subjectName = getSubjectName(request);
            logger.debug("subjectName=" + subjectName);
            if (subjectName == null || subjectName.equals(""))
                return;

            name = new X500Name(subjectName);
        } catch (IOException e) {
            // failed to build x500 name
            logger.warn("nsTokenUserKeySubjectNameDefault: populate " + e.getMessage(), e);
        }
        if (name == null) {
            // failed to build x500 name
        }
        try {
            info.set(X509CertInfo.SUBJECT,
                    new CertificateSubjectName(name));
        } catch (Exception e) {
            // failed to insert subject name
            logger.warn("nsTokenUserKeySubjectNameDefault: populate " + e.getMessage(), e);
        }
    }

    private String getSubjectName(Request request)
            throws EProfileException, IOException {

        logger.debug("nsTokenUserKeySubjectNameDefault: in getSubjectName");

        String pattern = getConfig(CONFIG_DNPATTERN);
        if (pattern == null || pattern.equals("")) {
            pattern = " ";
        }
        String sbjname = "";

        if (mldapInitialized == false) {
            if (request != null) {
                logger.debug("pattern = " + pattern);
                sbjname = mapPattern(request, pattern);
                logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): subject name mapping done");
            }
            return sbjname;
        }

        // ldap is initialized, do more substitution
        String searchName = getConfig(CONFIG_LDAP_SEARCH_NAME);
        if (searchName == null || searchName.equals("")) {
            searchName = "uid";
        }

        LDAPConnection conn = null;
        String userdn = null;
        // get DN from ldap to fill request
        try {
            if (mConnFactory == null) {
                conn = null;
                logger.error("nsTokenUserKeySubjectNameDefault: getSubjectName(): no LDAP connection");
                throw new EProfileException("no LDAP connection");
            } else {
                conn = mConnFactory.getConn();
                if (conn == null) {
                    logger.error("nsTokenUserKeySubjectNameDefault::getSubjectName() - " +
                               "no LDAP connection");
                    throw new EProfileException("no LDAP connection");
                }
                logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): got LDAP connection");
            }
            // retrieve the attributes
            // get user dn.
            logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): about to search with basedn = " + mBaseDN);
            LDAPSearchResults res = conn.search(mBaseDN,
                    LDAPv3.SCOPE_SUB, "(" + searchName + "=" + request.getExtDataInString("uid") + ")", null, false);

            if (res.hasMoreElements()) {
                LDAPEntry entry = res.next();

                userdn = entry.getDN();
            } else {// put into property file later - cfu
                logger.error("nsTokenUserKeySubjectNameDefault: getSubjectName(): " + searchName + " does not exist");
                throw new EProfileException("id does not exist");
            }
            logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): retrieved entry for "
                    + searchName + " = " + request.getExtDataInString("uid"));

            LDAPEntry entry = null;
            logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): about to search with "
                    + mLdapStringAttrs.length + " attributes");
            LDAPSearchResults results =
                    conn.search(userdn, LDAPv3.SCOPE_BASE, "objectclass=*",
                            mLdapStringAttrs, false);

            if (!results.hasMoreElements()) {
                logger.error("nsTokenUserKeySubjectNameDefault: getSubjectName(): no attributes");
                throw new EProfileException("no ldap attributes found");
            }
            entry = results.next();
            // set attrs into request
            for (int i = 0; i < mLdapStringAttrs.length; i++) {
                LDAPAttribute la =
                        entry.getAttribute(mLdapStringAttrs[i]);
                if (la != null) {
                    String[] sla = la.getStringValueArray();
                    logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): got attribute: "
                            + mLdapStringAttrs[i] +
                            "=" + LDAPUtil.escapeRDNValue(sla[0]));
                    request.setExtData(mLdapStringAttrs[i], LDAPUtil.escapeRDNValue(sla[0]));
                }
            }
            logger.debug("pattern = " + pattern);
            sbjname = mapPattern(request, pattern);
            logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): subject name mapping done");
            logger.debug("nsTokenUserKeySubjectNameDefault: getSubjectName(): attributes set in request");

        } catch (Exception e) {
            logger.error("nsTokenUserKeySubjectNameDefault: getSubjectName(): " + e.getMessage(), e);
            throw new EProfileException("getSubjectName() failure: " + e.toString());
        } finally {
            try {
                if (conn != null)
                    mConnFactory.returnConn(conn);
            } catch (Exception e) {
                throw new EProfileException(
                        "nsTokenUserKeySubjectNameDefault: getSubjectName(): connection return failure");
            }
        }
        return sbjname;

    }
}
