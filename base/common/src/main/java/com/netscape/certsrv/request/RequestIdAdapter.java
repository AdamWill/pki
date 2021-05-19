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
package com.netscape.certsrv.request;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.apache.commons.lang3.StringUtils;

/**
 * The RequestIdAdapter class provides custom marshaling for RequestId.
 *
 * @author Endi S. Dewata
 */
public class RequestIdAdapter extends XmlAdapter<String, RequestId> {

    @Override
    public RequestId unmarshal(String value) throws Exception {
        return StringUtils.isEmpty(value) ? null : new RequestId(value);
    }

    @Override
    public String marshal(RequestId value) throws Exception {
        return value == null ? null : value.toString();
    }
}