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
package com.sun.xml.ws.wsdl.writer.document;

import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;
import com.sun.xml.ws.wsdl.writer.document.Documented;

/**
 *
 * @author WS Development Team
 */
@XmlElement("operation")
public interface Operation
    extends TypedXmlWriter, Documented
{

/*
    @XmlElement("notification-operation")
    public NotificationOperation notificationOperation();

    @XmlElement("solicit-response-operation")
    public SolicitResponseOperation solicitResponseOperation();

    @XmlElement("request-response-operation")
    public RequestResponseOperation requestResponseOperation();

    @XmlElement("one-way-operation")
    public OneWayOperation oneWayOperation();
*/
    @XmlElement
    public ParamType input();

    @XmlElement
    public ParamType output();

    @XmlElement
    public FaultType fault();

    @XmlAttribute
    public com.sun.xml.ws.wsdl.writer.document.Operation name(String value);

    @XmlAttribute
    public com.sun.xml.ws.wsdl.writer.document.Operation parameterOrder(String value);
}
