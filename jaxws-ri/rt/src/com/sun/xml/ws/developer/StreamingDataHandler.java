/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.xml.ws.developer;

import org.jvnet.mimepull.MIMEPart;

import javax.activation.DataSource;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.File;

/**
 * DataHandler that can be used to access attachments efficiently.
 * Applications can use the additional methods and decide how to
 * access the attachment data.
 *
 * @author Jitendra Kotamraju
 */
public class StreamingDataHandler extends org.jvnet.staxex.StreamingDataHandler {

    public StreamingDataHandler(MIMEPart part) {
        super(new StreamingDataSource(part));
    }

    /**
     * Gives attachment data only one time. The attachment data
     * can be streamed directly without writing to the disk in
     * some circumstances. For example, if there is only one
     * attachment, attachment data can be read in a streaming
     * fashion. Also, this will reduce memory footprint of
     * the applications.
     *
     * <p>
     * Take advantage of this if the applications need the attachment
     * data only once.
     *
     * @return the content of the attachment
     * @throws IOException if any i/o error
     */
    public InputStream readOnce() throws IOException {
        StreamingDataSource ds = (StreamingDataSource)this.getDataSource();
        return ds.readOnce();

        // TODO: should we capture runtime exception and convert to IOException
    }

    /**
     * Moves the attachment data to a given file.
     *
     * @param file for attachment data
     */
    public void moveTo(File file) {
        StreamingDataSource ds = (StreamingDataSource)this.getDataSource();
        ds.moveTo(file);
    }

    private static final class StreamingDataSource implements DataSource {
        private final MIMEPart part;

        StreamingDataSource(MIMEPart part) {
            this.part = part;
        }

        public InputStream getInputStream() throws IOException {
            return part.read();             //readOnce() ??
        }

        InputStream readOnce() throws IOException {
            try {
                return part.readOnce();
            } catch(Exception e) {
                throw new MyIOException(e);
            }
        }

        void moveTo(File file) {
            part.moveTo(file);
        }

        public OutputStream getOutputStream() throws IOException {
            return null;
        }

        public String getContentType() {
            return part.getContentType();
        }

        public String getName() {
            return "";
        }
    }

    private static final class MyIOException extends IOException {
        private final Exception linkedException;

        MyIOException(Exception linkedException) {
            this.linkedException = linkedException;
        }

        @Override
        public Throwable getCause() {
            return linkedException;
        }
    }


}
