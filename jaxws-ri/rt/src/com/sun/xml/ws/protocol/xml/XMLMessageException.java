/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */

package com.sun.xml.ws.protocol.xml;

import com.sun.xml.ws.util.exception.JAXWSExceptionBase;
import com.sun.xml.ws.util.localization.Localizable;

/**
 * @author WS Development Team
 */
public class XMLMessageException extends JAXWSExceptionBase {

    public XMLMessageException(String key) {
        super(key);
    }

    public XMLMessageException(String key, String arg) {
        super(key, arg);
    }

    public XMLMessageException(String key, Object[] args) {
        super(key, args);
    }

    public XMLMessageException(String key, Localizable arg) {
        super(key, arg);
    }

    public XMLMessageException(Localizable arg) {
        super("server.rt.err", arg);
    }

    public String getResourceBundleName() {
        return "com.sun.xml.ws.resources.xmlmessage";
    }

}
