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
package com.sun.tools.ws.processor.modeler.annotation;


import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.io.*;

import javax.xml.namespace.QName;
import com.sun.xml.ws.modeler.RuntimeModeler;
import com.sun.tools.ws.processor.model.Parameter;
import com.sun.tools.ws.processor.model.Port;
import com.sun.tools.ws.processor.model.Service;
import com.sun.tools.ws.processor.model.java.JavaInterface;
import com.sun.tools.ws.processor.model.java.JavaSimpleType;
import com.sun.tools.ws.processor.model.java.JavaType;
import com.sun.tools.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.ws.processor.modeler.annotation.AnnotationProcessorContext.SEIContext;
import com.sun.tools.ws.util.ClassNameInfo;
import com.sun.tools.ws.wsdl.document.soap.SOAPStyle;
import com.sun.tools.ws.wsdl.document.soap.SOAPUse;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;

import javax.jws.*;
import javax.jws.soap.*;
import javax.jws.soap.SOAPBinding.ParameterStyle;

import com.sun.tools.xjc.api.*;
import com.sun.tools.ws.processor.modeler.annotation.AnnotationProcessorContext;
import com.sun.tools.ws.processor.modeler.annotation.ModelBuilder;
import com.sun.tools.ws.processor.modeler.annotation.WebServiceConstants;

/**
 *
 * @author  WS Development Team
 */
public abstract class WebServiceVisitor extends SimpleDeclarationVisitor implements WebServiceConstants {
    protected ModelBuilder builder;
    protected String wsdlNamespace;
    protected String typeNamespace;
    protected Stack<SOAPBinding> soapBindingStack;
    protected SOAPBinding typeDeclSOAPBinding;
    protected SOAPUse soapUse = SOAPUse.LITERAL;
    protected SOAPStyle soapStyle = SOAPStyle.DOCUMENT;
    protected boolean wrapped = true;
    protected HandlerChain hChain;
    protected SOAPMessageHandlers soapHandlers;
    protected Port port;
    protected String serviceImplName;
    protected String endpointInterfaceName;
    protected AnnotationProcessorContext context;
    protected SEIContext seiContext;
    protected boolean processingSEI = false;
    protected String serviceName;
    protected String packageName;
    protected String portName;
    protected boolean endpointReferencesInterface = false;
    protected boolean hasWebMethods = false;
    protected JavaSimpleTypeCreator simpleTypeCreator;
    protected TypeDeclaration typeDecl;
    protected Set<String> processedMethods;
    protected boolean pushedSOAPBinding = false;
    protected static final String ANNOTATION_ELEMENT_ERROR = "webserviceap.endpointinteface.plus.element";



    public WebServiceVisitor(ModelBuilder builder, AnnotationProcessorContext context) {
        this.builder = builder;
        this.context = context;
        this.simpleTypeCreator = new JavaSimpleTypeCreator();
        soapBindingStack = new Stack<SOAPBinding>();
        processedMethods = new HashSet<String>();
    }

    public void visitInterfaceDeclaration(InterfaceDeclaration d) {
        WebService webService = (WebService)d.getAnnotation(WebService.class);
        if (!shouldProcessWebService(webService, d))
            return;
        if (builder.checkAndSetProcessed(d))
            return;
        typeDecl = d;
        if (endpointInterfaceName != null && !endpointInterfaceName.equals(d.getQualifiedName())) {
            builder.onError(d.getPosition(), "webserviceap.endpointinterfaces.do.not.match", new Object[]
                {endpointInterfaceName, d.getQualifiedName()});
        }
        verifySEIAnnotations(webService, d);
        endpointInterfaceName = d.getQualifiedName();
        processingSEI = true;
        preProcessWebService(webService, d);
        processWebService(webService, d);
        postProcessWebService(webService, d);
    }

    public void visitClassDeclaration(ClassDeclaration d) {
        WebService webService = d.getAnnotation(WebService.class);
        if (!shouldProcessWebService(webService, d))
            return;
        if (builder.checkAndSetProcessed(d))
            return;
        typeDeclSOAPBinding = d.getAnnotation(SOAPBinding.class);
        typeDecl = d;
        if (serviceImplName == null)
            serviceImplName = d.getQualifiedName();
        String endpointInterfaceName = webService != null ? webService.endpointInterface() : null;
        if (endpointInterfaceName != null && endpointInterfaceName.length() > 0) {
            SourcePosition pos = pos = d.getPosition();
            checkForInvalidImplAnnotation(d, SOAPBinding.class);
            if (webService.name().length() > 0)
                 annotationError(pos, ANNOTATION_ELEMENT_ERROR,"name");
//            if (webService.targetNamespace().length() > 0)
//                 annotationError(pos, ANNOTATION_ELEMENT_ERROR, "targetNamespace");
            if (webService.wsdlLocation().length() > 0)
                annotationError(pos, ANNOTATION_ELEMENT_ERROR, "wsdlLocation");
            endpointReferencesInterface = true;
            verifyImplAnnotations(d);
            inspectEndpointInterface(endpointInterfaceName, d);
            serviceImplName = null;
            return;
        }
        processingSEI = false;
        preProcessWebService(webService, d);
        processWebService(webService, d);
        serviceImplName = null;
        postProcessWebService(webService, d);
        serviceImplName = null;
    }
    
    protected void verifySEIAnnotations(WebService webService, InterfaceDeclaration d) {
        if (webService.endpointInterface().length() > 0) {
            builder.onError(d.getPosition(), "webservicefactory.endpointinterface.on.interface",
                new Object[] {d.getQualifiedName(), webService.endpointInterface()});
        }
        if (webService.serviceName().length() > 0) {
            builder.onError(d.getPosition(), "webserviceap.invalid.sei.annotation.element",
                new Object[] {"serviceName", d.getQualifiedName()});
        }
        if (webService.portName().length() > 0) {
            builder.onError(d.getPosition(), "webserviceap.invalid.sei.annotation.element",
                new Object[] {"portName", d.getQualifiedName()});
        }
    }    
    
    protected void verifyImplAnnotations(ClassDeclaration d) {
        for (MethodDeclaration method : d.getMethods()) {
            checkForInvalidImplAnnotation(method, WebMethod.class);
            checkForInvalidImplAnnotation(method, Oneway.class);
            checkForInvalidImplAnnotation(method, WebResult.class);
            for (ParameterDeclaration param : method.getParameters()) {
                checkForInvalidImplAnnotation(param, WebParam.class);
            }
        }            
    }
    
    protected void checkForInvalidSEIAnnotation(InterfaceDeclaration d, Class annotationClass) {
        Object annotation = d.getAnnotation(annotationClass);
        if (annotation != null) {
            SourcePosition pos = d.getPosition();
            annotationError(pos, "webserviceap.invalid.sei.annotation", 
                    new Object[] {annotationClass.getName(), d.getQualifiedName()});                    
        }
    }
    
    protected void checkForInvalidImplAnnotation(Declaration d, Class annotationClass) {
        Object annotation = d.getAnnotation(annotationClass);
        if (annotation != null) {
            SourcePosition pos = d.getPosition();
            annotationError(pos, "webserviceap.endpointinteface.plus.annotation", 
                    annotationClass.getName());                    
        }
    }
    
    protected void annotationError(SourcePosition pos, String key, String element) {
        annotationError(pos, key, new Object[] {element});        
    }

    protected void annotationError(SourcePosition pos, String key, Object[] args) {
        builder.onError(pos, key, args);        
    }
    
    
    protected void preProcessWebService(WebService webService, TypeDeclaration d) {
        seiContext = context.getSEIContext(d);
        String targetNamespace = null;
        if (webService != null)
            targetNamespace = webService.targetNamespace();
        if (targetNamespace == null || targetNamespace.length() == 0)
            targetNamespace = getNamespace(d.getPackage(), d.getQualifiedName());
        seiContext.setNamespaceURI(targetNamespace);
        if (serviceImplName == null)
            serviceImplName = seiContext.getSEIImplName();
        if (serviceImplName != null) {
            seiContext.setSEIImplName(serviceImplName);
            context.addSEIContext(serviceImplName, seiContext);
        }
        portName = ClassNameInfo.getName(
                                d.getSimpleName().replace(
                                SIGC_INNERCLASS,
                                SIGC_UNDERSCORE));;
        packageName = d.getPackage().getQualifiedName();
        portName = webService != null && webService.name() != null && webService.name().length() >0 ?
                   webService.name() : portName;
        serviceName = ClassNameInfo.getName(d.getQualifiedName())+SERVICE;
        serviceName = webService != null && webService.serviceName() != null &&
                      webService.serviceName().length() > 0 ?
                        webService.serviceName() : serviceName;
        wsdlNamespace = seiContext.getNamespaceURI();
        typeNamespace = wsdlNamespace;

        SOAPBinding soapBinding = d.getAnnotation(SOAPBinding.class);
        if (soapBinding != null) {
            pushedSOAPBinding = pushSOAPBinding(soapBinding, d, d);
        }/* else {
            pushSOAPBinding(new MySOAPBinding(), d);
        }*/
    }

    public static boolean sameStyle(SOAPBinding.Style style, SOAPStyle soapStyle) {
        if (style.equals(SOAPBinding.Style.DOCUMENT) &&
            soapStyle.equals(SOAPStyle.DOCUMENT))
            return true;
        if (style.equals(SOAPBinding.Style.RPC) &&
            soapStyle.equals(SOAPStyle.RPC))
            return true;
        return false;
    }
    
    protected boolean pushSOAPBinding(SOAPBinding soapBinding, Declaration bindingDecl, 
            TypeDeclaration classDecl) {
        boolean changed = false;
        if (!sameStyle(soapBinding.style(), soapStyle)) {
            changed = true;
            if (pushedSOAPBinding)
                builder.onError(bindingDecl.getPosition(), "webserviceap.mixed.binding.style",
                                 new Object[] {classDecl.getQualifiedName()});
        }
        if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
            soapStyle = SOAPStyle.RPC;
            wrapped = true;
            if (soapBinding.parameterStyle().equals(ParameterStyle.BARE)) {
                builder.onError(bindingDecl.getPosition(), "webserviceap.rpc.literal.must.not.be.bare",
                                 new Object[] {classDecl.getQualifiedName()});
            }

        } else {
            soapStyle = SOAPStyle.DOCUMENT;
            if (wrapped != soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED)) {
                wrapped = soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED);
                changed = true;
            }
        }
        if (soapBinding.use().equals(SOAPBinding.Use.ENCODED)) {
           builder.onError(bindingDecl.getPosition(), "webserviceap.rpc.encoded.not.supported",
                    new Object[] {classDecl.getQualifiedName()});
        }
        if (changed || soapBindingStack.empty()) {
            soapBindingStack.push(soapBinding);
        }
        return changed;
    }


    protected SOAPBinding popSOAPBinding() {
        if (pushedSOAPBinding)
            soapBindingStack.pop();
        SOAPBinding soapBinding = null;
        if (!soapBindingStack.empty()) {
            soapBinding = soapBindingStack.peek();
            if (soapBinding.style().equals(SOAPBinding.Style.RPC)) {
                soapStyle = SOAPStyle.RPC;
                wrapped = true;
            } else {
                soapStyle = SOAPStyle.DOCUMENT;
                wrapped = soapBinding.parameterStyle().equals(ParameterStyle.WRAPPED);
            }
        }
        return soapBinding;
    }
 
    protected String getNamespace(PackageDeclaration packageDecl, String className) {
        return RuntimeModeler.getNamespace(packageDecl.getQualifiedName(), className);
    }

    abstract protected boolean shouldProcessWebService(WebService webService, InterfaceDeclaration intf);

    abstract protected boolean shouldProcessWebService(WebService webService, ClassDeclaration decl);

    abstract protected void processWebService(WebService webService, TypeDeclaration d);

    protected void postProcessWebService(WebService webService, TypeDeclaration d) {
        hasWebMethods = d instanceof ClassDeclaration ? hasWebMethods((ClassDeclaration)d) : false;
        processMethods(d);
        popSOAPBinding();
    }

    protected boolean hasWebMethods(ClassDeclaration d) {
        if (d.getQualifiedName().equals(JAVA_LANG_OBJECT))
            return false;
        WebMethod webMethod;
        for (MethodDeclaration method : d.getMethods()) {
            webMethod = method.getAnnotation(WebMethod.class);
            if (webMethod != null) {                
                if (webMethod.exclude()) {
                    if (webMethod.operationName().length() > 0)
                        builder.onError(method.getPosition(), "webserviceap.invalid.webmethod.element.with.exclude",
                            new Object[] {"operationName", d.getQualifiedName(), method.toString()});
                    if (webMethod.action().length() > 0)
                        builder.onError(method.getPosition(), "webserviceap.invalid.webmethod.element.with.exclude",
                            new Object[] {"action", d.getQualifiedName(), method.toString()});
                } else {
                    return true;
                }
            }
        }
        return hasWebMethods(d.getSuperclass().getDeclaration());
    }

    protected void processMethods(TypeDeclaration d) {
        builder.log("ProcessedMethods: "+d);
        if (d.getQualifiedName().equals(JAVA_LANG_OBJECT))
            return;
        if (d instanceof InterfaceDeclaration ||
               ((ClassDeclaration)d).getAnnotation(WebService.class) != null) {
            // Super classes must have @WebService annotations to pick up their methods
            for (MethodDeclaration methodDecl : d.getMethods()) {
                methodDecl.accept((DeclarationVisitor)this);
            }
        }
        if (d instanceof InterfaceDeclaration) {
            for (InterfaceType superType : d.getSuperinterfaces())
                processMethods(superType.getDeclaration());
        } else {
            ClassDeclaration classDecl = (ClassDeclaration)d;
            processMethods(classDecl.getSuperclass().getDeclaration());
        }
    }

    private void inspectEndpointInterface(String endpointInterfaceName, ClassDeclaration d) {
        TypeDeclaration intTypeDecl = null;
        for (InterfaceType interfaceType : d.getSuperinterfaces()) {
            if (endpointInterfaceName.equals(interfaceType.toString())) {
                intTypeDecl = interfaceType.getDeclaration();
                seiContext = context.getSEIContext(intTypeDecl.getQualifiedName());
                assert(seiContext != null);
                seiContext.setImplementsSEI(true);
                break;
            }
        }
        if (intTypeDecl == null) {
            intTypeDecl = builder.getTypeDeclaration(endpointInterfaceName);
        }
        if (intTypeDecl == null)
            builder.onError("webserviceap.endpointinterface.class.not.found",
                            new Object[] {endpointInterfaceName});
        else                            
            intTypeDecl.accept((DeclarationVisitor)this);
    }

    public void visitMethodDeclaration(MethodDeclaration method) {
        if (processedMethod(method))
            return;
        WebMethod webMethod = method.getAnnotation(WebMethod.class);
        if (shouldProcessMethod(method, webMethod))
            processMethod(method, webMethod);
    }
    
    protected boolean processedMethod(MethodDeclaration method) {
        String id = method.toString();
        if (processedMethods.contains(id)) 
            return true;
        processedMethods.add(id);
        return false;
    }
    
    abstract protected boolean shouldProcessMethod(MethodDeclaration method, WebMethod webMethod);
    abstract protected void processMethod(MethodDeclaration method, WebMethod webMethod);


    protected boolean isLegalImplementation(ClassDeclaration classDecl) {
        Collection<Modifier> modifiers = classDecl.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC) ||
            modifiers.contains(Modifier.FINAL) ||
            modifiers.contains(Modifier.ABSTRACT))
            return false;
        boolean hasDefaultConstructor = false;
        for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
            if (constructor.getModifiers().contains(Modifier.PUBLIC) &&
                constructor.getParameters().size() == 0) {
                    hasDefaultConstructor = true;
                    break;
            }

        }
        if (!methodsAreLegal(classDecl))
            return false;

        return hasDefaultConstructor;
    }

    protected boolean hasLegalSEI(ClassDeclaration classDecl) {
        for (InterfaceType interfaceType : classDecl.getSuperinterfaces()) {
            if (interfaceType.getDeclaration().getQualifiedName().equals(REMOTE_CLASSNAME)) {
                if (isLegalSEI(interfaceType.getDeclaration()))
                    return true;
            } else if (isLegalSEI(interfaceType.getDeclaration())) {
                return true;
            }
        }
        return false;
    }

    protected boolean isLegalSEI(InterfaceDeclaration intf) {
        for (FieldDeclaration field : intf.getFields()) {
            if (field.getConstantValue() != null) {
                builder.onError("webserviceap.sei.cannot.contain.constant.values",
                                new Object[] {intf.getQualifiedName(), field.getSimpleName()});
                return false;
            }
        }
        if (!methodsAreLegal(intf))
            return false;
        return true;
    }

    protected boolean methodsAreLegal(TypeDeclaration typeDecl) {
        for (MethodDeclaration method : typeDecl.getMethods()) {
            if (!isLegalMethod(method, typeDecl))
                return false;
        }
        return true;
    }

    protected boolean isLegalMethod(MethodDeclaration method,TypeDeclaration typeDecl) {
        if (!isLegalType(method.getReturnType())) {
            builder.onError("webserviceap.method.return.type.cannot.implement.remote",
                             new Object[] {typeDecl.getQualifiedName(),
                                           method.getSimpleName(),
                                           method.getReturnType()});
        }
        for (ParameterDeclaration parameter : method.getParameters()) {
            if (!isLegalType(parameter.getType()))
                builder.onError("webserviceap.method.parameter.types.cannot.implement.remote",
                                 new Object[] {typeDecl.getQualifiedName(),
                                               method.getSimpleName(),
                                               parameter.getSimpleName(),
                                               parameter.getType().toString()});
        }
        return true;
    }

    protected boolean isLegalType(TypeMirror type) {
        if (!(type instanceof DeclaredType))
            return true;
        return !builder.isRemote(((DeclaredType)type).getDeclaration());
    }

    public void addSchemaElements(MethodDeclaration method, boolean isDocLitWrapped) {
        addReturnSchemaElement(method, isDocLitWrapped);
        boolean hasInParam = false;
        for (ParameterDeclaration param : method.getParameters()) {
            hasInParam |= addParamSchemaElement(param, method, isDocLitWrapped);
        }
        if (!hasInParam && soapStyle.equals(SOAPStyle.DOCUMENT) && !isDocLitWrapped) {
            QName paramQName = new QName(wsdlNamespace, method.getSimpleName());
            seiContext.addSchemaElement(paramQName, null);
        }
    }

    public void addReturnSchemaElement(MethodDeclaration method, boolean isDocLitWrapped) {
        TypeMirror returnType = method.getReturnType();
        WebResult webResult = method.getAnnotation(WebResult.class);
        String responseName = builder.getResponseName(method.getSimpleName());
        String responseNamespace = wsdlNamespace;
        boolean isResultHeader = false;
        if (webResult != null) {
            responseName = webResult.name().length() > 0 ? webResult.name() : responseName;
            responseNamespace = webResult.targetNamespace().length() > 0 ? webResult.targetNamespace() : responseNamespace;
            isResultHeader = webResult.header();
        }
        QName typeName = new QName(responseNamespace, responseName);
        if (!(returnType instanceof VoidType) &&
            (!isDocLitWrapped || isResultHeader)) {
            Reference ref = seiContext.addReference(method);
            if (!soapStyle.equals(SOAPStyle.RPC))
                seiContext.addSchemaElement(typeName, ref);
        }
    }

    public boolean addParamSchemaElement(ParameterDeclaration param, MethodDeclaration method, boolean isDocLitWrappped) {
        boolean isInParam = false;
        WebParam webParam = param.getAnnotation(WebParam.class);
        String paramName = param.getSimpleName();
        String paramNamespace = wsdlNamespace;
        TypeMirror paramType = param.getType();
        TypeMirror holderType = builder.getHolderValueType(paramType);
        boolean isHeader = false;
        if (soapStyle.equals(SOAPStyle.DOCUMENT) && !wrapped) {
            paramName = method.getSimpleName();
        }
        if (webParam != null) {
            paramName = webParam.name() != null && webParam.name().length() > 0 ? webParam.name() : paramName;
            isHeader = webParam.header();
            paramNamespace = webParam.targetNamespace().length() > 0 ? webParam.targetNamespace() : paramNamespace;
        }
        if (holderType != null) {
            paramType = holderType;
        }
        if (isHeader || soapStyle.equals(SOAPStyle.DOCUMENT)) {
            if (isHeader || !isDocLitWrappped) {
                QName paramQName = new QName(paramNamespace, paramName);
                Reference ref = seiContext.addReference(paramType, param);
                seiContext.addSchemaElement(paramQName, ref);
            }
        } else
            seiContext.addReference(paramType, param);
        if (!isHeader && (holderType == null ||
            (webParam == null || !webParam.mode().equals(WebParam.Mode.OUT)))) {
            isInParam = true;
        }
        return isInParam;
    }

    protected static class MySOAPBinding implements SOAPBinding {
        public Style style() {return SOAPBinding.Style.DOCUMENT;}
        public Use use() {return SOAPBinding.Use.LITERAL; }
        public ParameterStyle parameterStyle() { return SOAPBinding.ParameterStyle.WRAPPED;}
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return SOAPBinding.class;
        }
    }
}

