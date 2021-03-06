package dk.statsbiblioteket.doms.client.impl.sdo;


import dk.statsbiblioteket.doms.client.exceptions.XMLParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;

public class XmlSchemaWithResolver extends AbstractXMLObject implements EntityResolver {

    static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

    private Node docNode;
    private Document document;


    public XmlSchemaWithResolver() {
        document = null;
        docNode = null;
    }

    public Node getDocNode() {
        return docNode;
    }

    public NodeList getElementsByTagName(String tagName) {
        return document.getElementsByTagName(tagName);
    }

    public Node getNodeById(String elementId) {
        if (document != null) {
            return (Node) document.getElementById(elementId);
        } else {
            return null;
        }
    }

    /**
     * This is used to parse a schema embedded in a foxml:datastream in a ContentModel.
     *
     * @param is
     *
     * @return
     */
    public boolean load(InputStream is) throws XMLParseException {
        boolean success = false;
        docNode = null;
        String errMsg = "";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        try {

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(this);
            builder.setEntityResolver(this);

            document = null;
            try {

                document = builder.parse(new InputSource(is));
                success = true;
            } catch (Exception e) {
                throw new XMLParseException(
                        "Parse failed in XMLDocument. " + e.getMessage());
            }
            docNode = document.getDocumentElement();


        } catch (ParserConfigurationException spe) {
            spe.printStackTrace();
            errMsg += "ParserConfigurationException";
            errMsg += "Exception message: " + spe.toString();
            throw new XMLParseException(errMsg);
        }
        return success;
    }

    /*
    /**
     * This Load method expects a complete XML document.
     * @param docPath
     * @return
     * @throws XMLWriteException
     *//*
    public boolean Load(String docPath) throws XMLWriteException {
		boolean success = false;
		docNode = null;
		String errMsg = "";

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);

		*//*try
        {
			factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
			factory.setValidating(true);
		} 
		catch (IllegalArgumentException x) 
		{
			  // Happens if the parser does not support JAXP 1.2
			  System.out.println("Cannot validate against schema. Parser does not support JAXP 1.2");
		} *//*

		try {

			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(this);
			builder.setEntityResolver(this);

			if (docPath == null) {
				docPath = "";
			} else {
				docPath = Util.getContentModelPath() + docPath;
			}
			File file = new File(docPath);
			
			if (file.exists())
			{
				document = null;
				FileInputStream fis = new FileInputStream(file);
	
				try {
					document = builder.parse(new InputSource(fis));
					success = true;
				} catch (Exception e) {
					throw new XMLWriteException("Parse failed in XMLDocument. "
							+ e.getMessage());
				}
				Element docElm = document.getDocumentElement(); //This is the <digitalObject> element
	
				if (docElm.getNodeType() != Node.ELEMENT_NODE) {
					throw new XMLWriteException(
							"The root element in the XML document is not the correct type..");
				}
	
				if (docElm.getNodeName().equals("foxml:digitalObject")) {
					docNode = (Node) docElm;
				}
			}
			else
			{
				//System.out.println("XMLDocument.load. File not found. docPath = " + docPath);
			}
		} catch (ParserConfigurationException spe) {
			spe.printStackTrace();
			errMsg += "ParserConfigurationException";
			errMsg += docPath;
			errMsg += "Exception message: " + spe.toString();
			throw new XMLWriteException(errMsg);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			errMsg += "IOException";
			errMsg += docPath;
			errMsg += "Exception message: " + ioe.toString();
			throw new XMLWriteException(errMsg);
		}
		return success;
	}
*/
    public InputSource resolveEntity(String publicID, String systemID) {
		/*System.out.println("resolveEntity is called. publicID = " + publicID
				+ ". systemID= " + systemID);
				*/

        //TODO this must be able to resolve the schema locations used in Doms
        return null;
    }
}
