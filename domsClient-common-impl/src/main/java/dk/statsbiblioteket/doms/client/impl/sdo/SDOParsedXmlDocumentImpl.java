package dk.statsbiblioteket.doms.client.impl.sdo;

import commonj.sdo.DataObject;
import commonj.sdo.Property;
import commonj.sdo.Sequence;
import commonj.sdo.Type;
import commonj.sdo.helper.HelperContext;
import commonj.sdo.helper.XMLDocument;
import commonj.sdo.helper.XMLHelper;
import commonj.sdo.helper.XSDHelper;
import dk.statsbiblioteket.doms.central.InvalidResourceException;
import dk.statsbiblioteket.doms.central.MethodFailedException;
import dk.statsbiblioteket.doms.client.datastreams.Datastream;
import dk.statsbiblioteket.doms.client.datastreams.DatastreamDeclaration;
import dk.statsbiblioteket.doms.client.datastreams.InternalDatastream;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.exceptions.XMLParseException;
import dk.statsbiblioteket.doms.client.impl.util.CycleDetector;
import dk.statsbiblioteket.doms.client.sdo.SDOParsedXmlDocument;
import dk.statsbiblioteket.doms.client.sdo.SDOParsedXmlElement;
import dk.statsbiblioteket.util.xml.DOM;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tuscany.sdo.api.SDOUtil;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;


/**
 * A SDOParsedXmlDocument represents an xml document (datastream) that has been parsed into the "SDO"
 * structure by "reversing" the associated schema
 */
public class SDOParsedXmlDocumentImpl implements SDOParsedXmlDocument {
    private Log log = LogFactory.getLog(SDOParsedXmlDocumentImpl.class);

    private List<Property> visitedProperties;
    private XMLDocument sdoXmlDocument = null;
    private SDOParsedXmlElementImpl rootSDOParsedXmlElement = null;
    private boolean isValid = false;
    private boolean isAbstract = false;

    private HelperContext sdoContext;

    private Datastream datastream;

    //The SDO types based on the XML Schema. Build in the ContentModel. The DataObjects use these types when parsing an
    //XML document that is an instance of the XML Schema.
    private List<Type> sdoTypes = new ArrayList<Type>();

    /**
     * Parse a datastream from its datastream declaration
     *
     * @param datastreamDeclaration the declaration of the datastream, containing the reference to the xml schema
     * @param datastream            the datastream, containing xml data
     *
     * @throws ServerOperationFailed if something could not be retrieved from the server
     * @throws XMLParseException     if some xml could not be parsed
     */
    public SDOParsedXmlDocumentImpl(DatastreamDeclaration datastreamDeclaration, Datastream datastream) throws
                                                                                                        ServerOperationFailed,
                                                                                                        XMLParseException {
        this.visitedProperties = new ArrayList<Property>() ;
        if (sdoContext == null) {
            sdoContext = SDOUtil.createHelperContext(true);
        }
        loadDatastreamDeclaration(datastreamDeclaration);
        loadDatastream(datastream);
        this.datastream = datastream;
    }

    /**
     * @return the rootSDOParsedXmlElement
     */
    @Override
    public SDOParsedXmlElementImpl getRootSDOParsedXmlElement() {
        return rootSDOParsedXmlElement;
    }

    /**
     * @return the isValid
     */
    @Override
    public boolean isValid() {
        return isValid;
    }

    /**
     * @return the isAbstract
     */
    @Override
    public boolean isAbstract() {
        return isAbstract;
    }

    /**
     * @return the sdoContext
     */
    public HelperContext getSdoContext() {
        return sdoContext;
    }

    /**
     * Load the datastream declaration. This just involves getting the schema and calling loadSchema
     *
     * @param datastreamDeclaration the declaration of the datastream, containing the reference to the xml schema
     *
     * @throws ServerOperationFailed if getting the schema content failed
     * @throws XMLParseException     if the schema could not be parsed
     */
    private void loadDatastreamDeclaration(DatastreamDeclaration datastreamDeclaration) throws
                                                                                        ServerOperationFailed,
                                                                                        XMLParseException {
        if (datastreamDeclaration.getSchema() != null) {
            String is2 = datastreamDeclaration.getSchema().getContents();
            loadSchema(is2);
        }
    }

    /**
     * Load the schema
     *
     * @param schemaString the schema as a string
     *
     * @throws XMLParseException if the schema could not be parsed
     */
    private void loadSchema(String schemaString) throws XMLParseException {
        //Since we do not have an instance of the XML Schema we do not know the type of the root element.
        //So we first read the schema manually to find out the targetNamespace.
        String targetNamespace = null;
        XmlSchemaWithResolver doc = new XmlSchemaWithResolver();
        if (doc.load(new ByteArrayInputStream(schemaString.getBytes()))) {
            if (doc.getDocNode() != null) {
                targetNamespace = getSchemaTargetNamespace(doc.getDocNode());
            }
        }
        //Define types based on the XML Schema
        try {
            sdoTypes = defineTypes(doc);
        } catch (TransformerException e) {
            throw new XMLParseException("Failed to parse the types", e);
        }
        if (sdoTypes != null) {
            Property rootProperty = getRootProperty(
                    sdoTypes, null, targetNamespace, getXsdHelper());
            Type rootType = rootProperty.getType();
            isAbstract = rootType.isAbstract();
            isValid = !isAbstract;
        }
    }


    /**
     * Load the datastream
     *
     * @param datastream the datastream, containing xml data
     *
     * @throws ServerOperationFailed if communication with doms failed
     */
    private void loadDatastream(Datastream datastream) throws ServerOperationFailed {

        //Load the XML document
        XMLDocument datastreamXmlDocument = getXmlHelper().load(datastream.getContents());

        //Try getting the root data object from the datastream
        DataObject rootDataObject = datastreamXmlDocument.getRootObject();
        Type rootType = rootDataObject.getType();
        String targetNamespace = rootType.getURI();
        isAbstract = rootType.isAbstract();
        Property rootProperty = getRootProperty(sdoTypes, rootType, targetNamespace, getXsdHelper());

        //If that did not work (ie. the datastream is empty or something), take the first allowed element from the sdoTypes
        if (rootProperty == null) {
            for (Type sdoType : sdoTypes) {
                if (sdoType.getName().equals("DocumentRoot")) {
                    targetNamespace = sdoType.getURI();
                    //First element declaration in the schema
                    rootProperty = (Property) sdoType.getDeclaredProperties().get(0);
                    rootDataObject = sdoContext.getDataFactory().create(targetNamespace, rootProperty.getName());
                }
            }
        }

        //If this still did not work, fail
        if (rootProperty == null) {
            throw new ServerOperationFailed(
                    "Could not get a valid SDO root DataObject for Datastream: '" + datastream.getId() + "'.");
        }

        //Create the result sdoXmlDocument
        sdoXmlDocument = getXmlHelper().createDocument(
                rootDataObject, targetNamespace, rootProperty.getName());


        //Create the root element
        rootSDOParsedXmlElement = new SDOParsedXmlElementImpl(this, null, rootDataObject, rootProperty);
        rootSDOParsedXmlElement.setLabel(rootProperty.getName());

        //Then handle each of the sub elements to the root element
        List containedProps = rootProperty.getType().getDeclaredProperties();
        for (Object tmp : containedProps) {
            if (tmp instanceof Property) {
                Property property = (Property) tmp;
                handleProperty(getRootSDOParsedXmlElement(), rootDataObject, property);
            }
        }
        getRootSDOParsedXmlElement().reorderRecursively();

    }



    private XMLHelper getXmlHelper() {
        return sdoContext.getXMLHelper();
    }

    /**
     * Traverse the SDO tree and remove any elements which are empty and which were not present in the original document.
     * @return the String representation of the pruned XML document.
     * @throws XMLParseException
     */
    @Override
    public String dumpToString() throws XMLParseException {
        if ((getRootSDOParsedXmlElement() != null) && (sdoXmlDocument != null) && (sdoContext != null)) {
            Writer writer = new StringWriter();
            getRootSDOParsedXmlElement().submit(sdoContext);

            //Before we serialize the xml document we make a copy of the xml document and deletes empty data
            //objects from the copy before serializing the copy.
            DataObject rootCopy = sdoContext.getCopyHelper().copy(sdoXmlDocument.getRootObject());
            Type rootType = rootCopy.getType();
            Property rootProperty = getRootProperty(sdoTypes, rootType, rootType.getURI(), getXsdHelper());


            SdoDataObjectRemovalUtil utils = new SdoDataObjectRemovalUtil();
            utils.handleDataObject(sdoContext, null, rootCopy);
            utils.doDelete();


            try {
                XMLDocument docCopy = getXmlHelper().createDocument(
                                        rootCopy, rootType.getURI(), rootProperty.getName() );
                getXmlHelper().save(docCopy, writer, null);
                writer.flush();
            } catch (IOException e) {
                throw new RuntimeException("String writer failed to write...", e);
            }
            log.debug("Created writable output:\n" + writer.toString());
            return writer.toString();
        }
        return null;
    }

    @Override
    public void saveToDatastream() throws XMLParseException {
        if (datastream instanceof InternalDatastream) {
            InternalDatastream internalDatastream = (InternalDatastream) datastream;
            internalDatastream.setContents(dumpToString());
        } else {
            throw new IllegalAccessError("You should not attempt to save SDO to an external datastream");
        }
    }

    @Override
    public Datastream getDatastream() {
        return datastream;
    }


    /**
     * This method parses the given compositeSchema to determine the sdo type list of the
     * schema reference. It assumed a structure somewhat like
     * <datastream object="pid" value="DS_SCHEMA"/>
     *
     * @param compositeSchema
     *
     * @return
     * @throws IOException
     * @throws MethodFailedException
     * @throws InvalidResourceException
     */
    private List<Type> defineTypes(XmlSchemaWithResolver compositeSchema) throws TransformerException {
        List<Type> types;

        types = getXsdHelper().define(DOM.domToString(compositeSchema.getDocNode(), true));

        return types;
    }

    private XSDHelper getXsdHelper() {
        return sdoContext.getXSDHelper();
    }

    /**
     * Recursively handle the properties (elements) from the xml schema
     *
     * @param currentElement    the output element element (target)
     * @param currentDataObject the current element (from the source xml)
     * @param childProperty     the property, which is the type in the xml schema
     */
    private void handleProperty(final SDOParsedXmlElement currentElement, final DataObject currentDataObject,
                                final Property childProperty) {
        visitedProperties.add(childProperty);
        if (isSimpleType(childProperty)) {
            handleSimpleType(currentElement, currentDataObject, childProperty);
        } else {
            handleComplexType(currentElement, currentDataObject, childProperty);
        }
    }

    /**
     * The childProperty denotes a simple type, so create leaves
     *
     * @param currentElement    the current element
     * @param currentDataObject the current data object
     * @param childProperty     the current property
     */
    private void handleSimpleType(SDOParsedXmlElement currentElement, DataObject currentDataObject,
                                  Property childProperty) {
        List<String> childDataObjects = getChildValues(currentDataObject, childProperty);
        if (childDataObjects.isEmpty()) {
            addLeaf(currentElement, currentDataObject, childProperty, null, 0);
        } else {
            for (int i = 0; i < childDataObjects.size(); i++) {
                addLeaf(currentElement, currentDataObject, childProperty, childDataObjects.get(i), i);
            }
        }
    }

    /**
     * This is one of the more troublesome part of the code. Given a current element and its corresponding DataObject,
     * it recursively builds the subtree corresponding to a particular child property.
     * @param currentElement
     * @param currentDataObject
     * @param childProperty
     */
    private void handleComplexType(SDOParsedXmlElement currentElement, DataObject currentDataObject,
                                   Property childProperty) {
        boolean isCycling = (new CycleDetector()).isCycling(visitedProperties.toArray(), 0);
        if (isCycling) {
            //TODO by annotating the currentElement with a nesting depth we could actually make
            //the allowed nesting parametrisable per element type.
            return;
        }

        // Get the already-existing child object(s) with this property (if any)
        List<DataObject> childDataObjects = getChildObjects(currentDataObject, childProperty);
        //The declared type of the child property
        final Type currentPropertyType = childProperty.getType();

        //If there are no existing child objects with this property then create one. This is not possible
        //if the declared type is abstract, because how could we know what type of object to create?
        if (childDataObjects.isEmpty()) {
            if (!currentPropertyType.isAbstract()) {
                childDataObjects.add(currentDataObject.createDataObject(childProperty));
            } else {
               log.warn("Could not add child object for abstract property: object, property = \'"
                       + currentDataObject.toString() + "\',"
                       + "\'" + childProperty.toString() + "\'"
               );
            }
        }

        //We now need to determine whether the child object we have found or created corresponds to
        //a leaf in the tree. Do this by checking if it has any grandchild properties.
        List<Property> grandChildProperties = currentPropertyType.getProperties();





        //Do some logging here for debugging purposes
        doLogging(currentElement, childProperty, childDataObjects, grandChildProperties);

        if (grandChildProperties.isEmpty()) {//no grand children so add all children as leafs here
            for (DataObject childDataObject : childDataObjects) {
                handleLeafElement(currentElement, currentDataObject, childProperty, childDataObject);
            }
        } else {//if there is grand child property, this is a node in the tree, not a leaf

            for (DataObject childDataObject : childDataObjects) {
                //so we create the childElement node
                SDOParsedXmlElement childElement = new SDOParsedXmlElementImpl(
                        this, currentElement, childDataObject, childProperty);
                currentElement.add(childElement);

                if (getXsdHelper().isMixed(childProperty.getType())) {
                //if (childProperty.getType().isSequenced()) {
                    Sequence sequence = childDataObject.getSequence();
                    for (int i=0; i < sequence.size(); i++) {
                        Object o = sequence.getValue(i);
                        if (sequence.getProperty(i) != null) {
                            SDOParsedXmlElement childSeqElement = new SDOParsedXmlElementImpl(this, childElement, childDataObject, sequence.getProperty(i));
                            childElement.add(childSeqElement);
                            if (o instanceof String) {
                                childSeqElement.setValue(o);
                            }
                        } else {
                            if (o instanceof String) {
                                SDOParsedXmlElement childSeqElement = new SDOParsedXmlElementImpl(this, childElement, childDataObject, null);
                                childElement.add(childSeqElement);
                                childSeqElement.setValue(o);
                                childSeqElement.setLabel("value");
                            }
                        }
                    }
                }

                int i = 0;
                //childDataObject.getInstanceProperties();
                for (Property grandChildProperty : grandChildProperties) { //we iterate on the sub types
                    i++;
                    if (isAttribute(grandChildProperty)) { //if they are attributes, create a leaf
                        final Object grandChildValue = childDataObject.get(grandChildProperty);
                        addLeaf(
                                childElement,
                                childDataObject,
                                grandChildProperty, grandChildValue,
                                i);
                    }  else {
                        //otherwise evaluate them recursively
                            handleProperty(childElement, childDataObject, grandChildProperty);
                    }
                }
            }
        }
    }

    private void doLogging(SDOParsedXmlElement currentElement, Property childProperty, List<DataObject> childDataObjects, List<Property> grandChildProperties) {
        log.debug(
                "Current element: " + currentElement.getLabel() + "|" + currentElement.hashCode()
                        + "|Child property: " + childProperty.getName() + "|" + childProperty.hashCode()
        );
        for (DataObject childDataObject:childDataObjects) {
            log.debug("|Child object: " + childDataObject.getType().getName());
        }
        for (Property grandChildProperty: grandChildProperties) {
            log.debug("|Grandchild Property: " + grandChildProperty.getName());
        }
    }

    /**
     * (im)perfect check to verify that the current property is a simple type
     *
     * @param currentProperty
     *
     * @return
     */
    private boolean isSimpleType(Property currentProperty) {
        return !(currentProperty.isContainment() && !currentProperty.getType().isDataType());
    }

    /**
     * Gets or creates the appropriate child objects to this dataobject and childProperty
     * The data object have relation (childProperty) to a child object. There can be either 0, 1 or many
     * child objects per childProperty.
     * If there is 1 or many, we return a list of these.
     * If there is 0, we create a new child object, as we will have to have an empty field to fill out in the gui
     *
     * @param dataObject the dataobject having the child objects
     * @param childProperty   the childProperty on the current element
     *
     * @return the list of child objects
     */
    private List<DataObject> getChildObjects(DataObject dataObject, Property childProperty) {
        List<DataObject> childObjects = new ArrayList<DataObject>();

        //We are checking if the datastream xml have the value set or not
        if (dataObject.isSet(childProperty)) { // the childProperty is set

            if (childProperty.isMany()) {//is the childProperty many valued
                List<DataObject> values = dataObject.getList(childProperty); //get the values for the childProperty
                if (values != null) {
                    for (DataObject value : values) {
                        childObjects.add(value);
                    }
                }
            } else {
                childObjects.add(dataObject.getDataObject(childProperty));
            }
        }
        return childObjects;
    }

    /**
     * Gets or creates the appropriate child objects to this dataobject and childProperty. This method is appropriate
     * for simple types, where the child objects are all strings.
     * The data object have relation (childProperty) to a child object. There can be either 0, 1 or many
     * child objects per childProperty.
     * If there is 1 or many, we return a list of these.
     * If there is 0, we create a new child object, as we will have to have an empty field to fill out in the gui
     *
     * @param dataObject    the dataobject having the child objects
     * @param childProperty the childProperty on the current element
     *
     * @return the list of child objects
     */
    private List<String> getChildValues(DataObject dataObject, Property childProperty) {
        List<String> childObjects = new ArrayList<String>();

        //We are checking if the datastream xml have the value set or not
        if (dataObject.isSet(childProperty)) { // the childProperty is set

            if (childProperty.isMany()) {//is the childProperty many valued
                List<String> values = dataObject.getList(childProperty); //get the values for the childProperty
                if (values != null) {
                    for (String value : values) {
                        childObjects.add(value);
                    }
                }
            } else {
                childObjects.add(dataObject.get(childProperty).toString());
            }
        }
        return childObjects;
    }


    /**
     * Handle leaf elements.
     *
     * @param currentElement
     * @param currentDataObject
     * @param currentProperty the child property we are handling
     * @param childObject
     */
    private void handleLeafElement(SDOParsedXmlElement currentElement, DataObject currentDataObject,
                                   Property currentProperty, DataObject childObject) {
        Object value;
        //This next code seems to be in the wrong place. mixed/sequence data doesn't have to be in a leaf element,
        if (currentProperty.getType().isSequenced()) { // and is of sequenced type
            if (getXsdHelper().isMixed(currentProperty.getType())) { //if the type is mixed, the order matters
                //addLeaf(currentElement, childObject, currentProperty, null, 0);
                handleLeafSequence(currentElement, currentProperty, childObject);
            } else {
                // TODO Not mixed, what to do? Currently throws away value, I think?
                value = null;//currentDataObject.get(currentProperty);
                addLeaf(currentElement, childObject, currentProperty, value, 0);
            }
        } else if (currentProperty.getType().isAbstract()) { // and is of sequenced type or abstract
            handleLeafSequence(currentElement, currentProperty, childObject);
        } else {  // The property is not sequenced
            value = currentDataObject.get(currentProperty);
            addLeaf(currentElement, childObject, currentProperty, value, 0);
        }
    }

    private void handleLeafSequence(SDOParsedXmlElement currentElement, Property currentProperty,
                                    DataObject childObject) {
        Object value;
        Sequence seq = childObject.getSequence();
        if (seq != null) {
            for (int i = 0; i < seq.size(); i++) {
                Property p = seq.getProperty(i);
                if (p == null) {
                    value = seq.getValue(i);
                    addLeaf(currentElement, childObject, currentProperty, value, i);
                } else {
                    value = seq.getValue(i);
                    addLeaf(currentElement, childObject, p, value, i);
                }
            }
        }
    }

    /**
     * Attempt to check if the property denote an xml element or an xml attribute
     * @param childProperty the property, which is the type in the xml schema
     * @return true if the property denote an attribute
     */
    private boolean isAttribute(Property childProperty) {
        if (getXsdHelper().isAttribute(childProperty)) {
            return true;
        }
        return false;
    }

    /**
     * Create a new leaf and add it to the parent
     *
     * @param currentElement    the element to attach the leaf to
     * @param currentDataObject the dataobject corresponding to the leaf
     * @param currentProperty   the currentProperty. ie. the xml schema type of the leaf
     * @param value             the value of the leaf
     * @param sequenceIndex     the sequence index of the leaf, if required
     */
    private void addLeaf(SDOParsedXmlElement currentElement, final DataObject currentDataObject,
                         final Property currentProperty, Object value, int sequenceIndex) {

        SDOParsedXmlElementImpl newLeaf = new SDOParsedXmlElementImpl(
                this, currentElement, currentDataObject, currentProperty);
        if (value != null &&
                (currentDataObject.isSet(currentProperty) || currentDataObject.toString().contains("xml.type:text"))) {
            final String newLeafValue = value.toString();
            newLeaf.setValue(newLeafValue);
            newLeaf.setOriginallySet(true);
            if (newLeafValue.isEmpty()) {
                newLeaf.setOriginallySetNonEmpty(false);
            } else {
                newLeaf.setOriginallySetNonEmpty(true);
            }
            decorateParents(newLeaf);
        } else {
            newLeaf.setOriginallySet(false);
            newLeaf.setOriginallySetNonEmpty(false);
        }
        newLeaf.setLabel(currentProperty.getName());
        newLeaf.setIndex(sequenceIndex);
        currentElement.add(newLeaf);
        try {
            List<String> valueEnum = (List<String>) SDOUtil.getEnumerationFacet(currentProperty.getType());
            newLeaf.setValueEnum(valueEnum);
        } catch (NullPointerException e) {
            //There doesn't seem to be any way to check for the existence of an enumerationFacet
            //other than trying to get it and catching the NullPointerException if it isn't there.
        }
    }

    private void decorateParents(SDOParsedXmlElementImpl newLeaf) {
        SDOParsedXmlElement parent = newLeaf.getParent();
        while (parent != null) {
            parent.setIsHasNonEmptyDescendant(true);
            parent = parent.getParent();
        }
    }

    /**
     * Get the target namespace of the schema
     *
     * @param schema the schema as a DOM
     *
     * @return the target namespace or null if none defined
     */
    private String getSchemaTargetNamespace(Node schema) {
        NamedNodeMap attrs = schema.getAttributes();
        if (attrs != null) {
            Node attr = attrs.getNamedItem("targetNamespace");
            if (attr != null) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    /**
     * Find the rood property of the document
     *
     * @param types           the types contained in the schema
     * @param requiredType    the type of the root property, or null
     * @param targetNamespace the target namespace of the root property
     * @param xsdHelper       the xsd helper
     *
     * @return the root property
     */
    private Property getRootProperty(List<Type> types, Type requiredType, String targetNamespace, XSDHelper xsdHelper) {
        List<Type> relevantTypes = new ArrayList<Type>();
        //Collect all types below the "DocumentRoot" type as "relevantTypes"
        for (Type type : types) {
            if (type.getName().equals("DocumentRoot")) {
                if ((targetNamespace == null) || (type.getURI().equals(targetNamespace))) {
                    collectTypes(type, relevantTypes);
                }
            }
        }

        Property rootProperty = null;

        //Of all the relevant types, select the first that:
        //a: is an element
        //b; match required type (if required type is not null)
        //Return this
        for (Type type : relevantTypes) {
            if (type.getName().equals("DocumentRoot")) {
                for (Property property : (List<Property>) type.getProperties()) {
                    if (xsdHelper.isElement(property)) {
                        if (requiredType == null || property.getType().getName().equals(requiredType.getName())) {
                            rootProperty = property;
                            break;
                        }
                    }
                }
                break;
            }
        }

        return rootProperty;
    }

    /**
     * Recursively flattens the tree of types below type. Type have a list of properties, and each of these
     * have types. This tree is recursive
     *
     * @param type      the starting type
     * @param collected the types collected so far
     */
    private void collectTypes(Type type, List<Type> collected) {
        if (!collected.contains(type)) {
            collected.add(type);
            for (Property property : (List<Property>) type.getProperties()) {
                collectTypes(property.getType(), collected);
            }
        }
    }


}
