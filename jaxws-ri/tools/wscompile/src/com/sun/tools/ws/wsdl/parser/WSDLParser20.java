/*
 * $Id: WSDLParser20.java,v 1.5 2005-09-10 19:50:13 kohsuke Exp $
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
package com.sun.tools.ws.wsdl.parser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.tools.ws.processor.config.ModelInfo;
import com.sun.tools.ws.processor.config.WSDLModelInfo;
import com.sun.tools.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.ws.util.exception.LocalizableExceptionAdapter;
import com.sun.tools.ws.util.xml.NullEntityResolver;
import com.sun.tools.ws.wsdl.document.Definitions;
import com.sun.tools.ws.wsdl.document.Types;
import com.sun.tools.ws.wsdl.document.WSDLConstants;
import com.sun.tools.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.ws.wsdl.framework.ParseException;
import com.sun.tools.ws.wsdl.framework.ParserContext;
import com.sun.tools.ws.util.xml.XmlUtil;


/**
 * @author Vivek Pandey
 *
 */
public class WSDLParser20 extends WSDLParser {
    private WSDLModelInfo modelInfo;

    public WSDLParser20(WSDLModelInfo modelInfo){
        this();
        this.modelInfo = modelInfo;
    }

    /**
     *
     */
    public WSDLParser20() {
        super();
        register(new JAXWSBindingExtensionHandler());
        register(new SOAP12ExtensionHandler());
    }

    /* (non-Javadoc)
     * @see WSDLParser#parseDefinitions(ParserContext, org.xml.sax.InputSource, java.lang.String)
     */
    protected Definitions parseDefinitions(ParserContext context,
            InputSource source, String expectedTargetNamespaceURI) {
        context.pushWSDLLocation();
        context.setWSDLLocation(source.getSystemId());
        buildDocumentFromWSDL(context, source, expectedTargetNamespaceURI);
        Document root = wsdlDocuments.get(source.getSystemId());
        if(root == null){
            System.out.println("Error: WSDL Docuemnt root cant be null!");
        }

        //Internalizer.transform takes Set of jaxws:bindings elements, this is to allow multiple external
        //bindings to be transformed.
        new Internalizer().transform(modelInfo.getJAXWSBindings(), wsdlDocuments,
                (ProcessorEnvironment)modelInfo.getParent().getEnvironment());

        //print the wsdl
//        try{
//            dump(System.out);
//        }catch(IOException e){
//            e.printStackTrace();
//        }

        Definitions definitions = parseDefinitionsNoImport(context, root, expectedTargetNamespaceURI);
        processImports(context, source, definitions);
        context.popWSDLLocation();
        return definitions;
    }



    /* (non-Javadoc)
     * @see WSDLParser#processImports(ParserContext, org.xml.sax.InputSource, Definitions)
     */
    protected void processImports(ParserContext context, InputSource source, Definitions definitions) {
        for(String location : imports){
            if (!context.getDocument().isImportedDocument(location)){
                Definitions importedDefinitions = parseDefinitionsNoImport(context,
                        wsdlDocuments.get(location), location);
                if(importedDefinitions == null)
                    continue;
                context.getDocument().addImportedEntity(importedDefinitions);
                context.getDocument().addImportedDocument(location);
            }
        }
    }
    /**
     * @param context
     * @param source
     * @param expectedTargetNamespaceURI
     * @return
     */
    private void buildDocumentFromWSDL(ParserContext context, InputSource source, String expectedTargetNamespaceURI) {
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            builderFactory.setValidating(false);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                public void error(SAXParseException e)
                    throws SAXParseException {
                    throw e;
                }

                public void fatalError(SAXParseException e)
                    throws SAXParseException {
                    throw e;
                }

                public void warning(SAXParseException err)
                    throws SAXParseException {
                    // do nothing
                }
            });
            builder.setEntityResolver(new NullEntityResolver());

            try {
                Document document = builder.parse(source);
                wsdlDocuments.put(source.getSystemId(), document);
                Element e = document.getDocumentElement();
                Util.verifyTagNSRootElement(e, WSDLConstants.QNAME_DEFINITIONS);
                String name = XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAME);

                String _targetNamespaceURI =
                    XmlUtil.getAttributeOrNull(e, Constants.ATTR_TARGET_NAMESPACE);

                if (expectedTargetNamespaceURI != null
                    && !expectedTargetNamespaceURI.equals(_targetNamespaceURI)){
                    //TODO: throw an exception???
                }

                for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
                    Element e2 = Util.nextElement(iter);
                    if (e2 == null)
                        break;
                    
                    //check to see if it has imports
                    if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_IMPORT)){
                        String namespace = Util.getRequiredAttribute(e2, Constants.ATTR_NAMESPACE);
                        String location = Util.getRequiredAttribute(e2, Constants.ATTR_LOCATION);
                        location = getAdjustedLocation(source, location);
                        if(location != null && !location.equals("")){
                            if(!imports.contains(location)){
                                imports.add(location);
                                buildDocumentFromWSDL(context, new InputSource(location), namespace);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (source.getSystemId() != null) {
                    throw new ParseException(
                        "parsing.ioExceptionWithSystemId",
                        source.getSystemId(),
                        new LocalizableExceptionAdapter(e));
                } else {
                    throw new ParseException(
                        "parsing.ioException",
                        new LocalizableExceptionAdapter(e));
                }
            } catch (SAXException e) {
                if (source.getSystemId() != null) {
                    throw new ParseException(
                        "parsing.saxExceptionWithSystemId",
                        source.getSystemId(),
                        new LocalizableExceptionAdapter(e));
                } else {
                    throw new ParseException(
                        "parsing.saxException",
                        new LocalizableExceptionAdapter(e));
                }
            }
        } catch (ParserConfigurationException e) {
            throw new ParseException(
                "parsing.parserConfigException",
                new LocalizableExceptionAdapter(e));
        } catch (FactoryConfigurationError e) {
            throw new ParseException(
                "parsing.factoryConfigException",
                new LocalizableExceptionAdapter(e));
        }
    }

    /**
     * @param source
     * @param location
     * @return
     */
    private String getAdjustedLocation(InputSource source, String location) {
        return source.getSystemId() == null
            ? location
            : Util.processSystemIdWithBase(
                source.getSystemId(),
                location);
    }

    /**
     * Dumps the contents of the forest to the specified stream.
     *
     * This is a debug method. As such, error handling is sloppy.
     */
    public void dump( OutputStream out ) throws IOException {
        try {
            // create identity transformer
            Transformer it = TransformerFactory.newInstance().newTransformer();

            for( Iterator itr=wsdlDocuments.entrySet().iterator(); itr.hasNext(); ) {
                Map.Entry e = (Map.Entry)itr.next();

                out.write( ("---<< "+e.getKey()+"\n").getBytes() );

                it.transform( new DOMSource((Document)e.getValue()), new StreamResult(out) );

                out.write( "\n\n\n".getBytes() );
            }
        } catch( TransformerException e ) {
            e.printStackTrace();
        }
    }

    //all the wsdl:import system Ids
    private final Set<String> imports = new HashSet<String>();

    //Map which holds wsdl Document(s) for a given SystemId
    private final Map<String, Document> wsdlDocuments = new HashMap<String, Document>();

}
