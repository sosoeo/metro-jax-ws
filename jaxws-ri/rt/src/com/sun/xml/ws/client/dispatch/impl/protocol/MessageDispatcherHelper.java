/*
 * $Id: MessageDispatcherHelper.java,v 1.7 2005-09-10 19:47:32 kohsuke Exp $
 */

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
package com.sun.xml.ws.client.dispatch.impl.protocol;

import com.sun.pept.ept.MessageInfo;
import com.sun.pept.presentation.MessageStruct;
import com.sun.pept.protocol.MessageDispatcher;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.client.BindingProviderProperties;
import com.sun.xml.ws.client.ContextMap;
import com.sun.xml.ws.handler.HandlerChainCaller;
import com.sun.xml.ws.protocol.soap.client.SOAPMessageDispatcher;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;
import java.util.logging.Logger;

/**
 * @author WS Development Team
 */
public class MessageDispatcherHelper extends SOAPMessageDispatcher
    implements BindingProviderProperties {

    private static final Logger logger =
        Logger.getLogger(new StringBuffer().append(com.sun.xml.ws.util.Constants.LoggingDomain).append(".client.dispatch").toString());

    public MessageDispatcherHelper() {
        super();
    }

    @Override
    protected void setResponseType(Throwable e, MessageInfo messageInfo) {
        if (e instanceof RuntimeException) {
            //leave for now- fix later
            if (e instanceof WebServiceException)
                messageInfo.setResponseType(MessageStruct.CHECKED_EXCEPTION_RESPONSE);
            else
                messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
        } else {
            messageInfo.setResponseType(MessageStruct.CHECKED_EXCEPTION_RESPONSE);
        }
    }

    @Override
    protected HandlerChainCaller getHandlerChainCaller(MessageInfo messageInfo) {
        ContextMap context = (ContextMap)
            messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        BindingProvider provider = (BindingProvider)
            context.get(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY);
        BindingImpl binding = (BindingImpl) provider.getBinding();
        return binding.getHandlerChainCaller();
    }

}

