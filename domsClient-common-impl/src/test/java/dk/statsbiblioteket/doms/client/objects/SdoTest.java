package dk.statsbiblioteket.doms.client.objects;

import dk.statsbiblioteket.doms.client.datastreams.Datastream;
import dk.statsbiblioteket.doms.client.datastreams.DatastreamDeclaration;
import dk.statsbiblioteket.doms.client.exceptions.NotFoundException;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.exceptions.XMLParseException;
import dk.statsbiblioteket.doms.client.impl.sdo.SDOParsedXmlDocumentImpl;
import dk.statsbiblioteket.doms.client.impl.sdo.SDOParsedXmlElementImpl;
import dk.statsbiblioteket.doms.client.objects.stubs.DatastreamDeclarationStub;
import dk.statsbiblioteket.doms.client.objects.stubs.DatastreamStub;
import dk.statsbiblioteket.doms.client.objects.stubs.ModsTestHelper;
import dk.statsbiblioteket.doms.client.sdo.SDOParsedXmlElement;
import dk.statsbiblioteket.util.Strings;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.XPathSelector;
import junit.framework.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tuscany.sdo.api.SDOUtil;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Validator;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 */
public class SdoTest  {

    private Log log = LogFactory.getLog(SdoTest.class);


    /**
     * Tests that parsing works correctly when a fixed-value attribute is absent - it remains absent.
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     */
    @Test
    public void testSdoFixedValueAbsent() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                       return SdoUtils.getStringFromFileOnClasspath("MODS35_SIMPLE.xsd");
                    }
                };
            }
        };
        ModsTestHelper modsTestHelper = new ModsTestHelper();
        final String modsDatastreamContent = modsTestHelper.getModsSimpleString();
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String xmlFinal = sdodoc.dumpToString();
        Document finalDocument = DOM.stringToDOM(xmlFinal, true);
        XPathSelector MODS_XPATH_SELECTOR = DOM.createXPathSelector("mods", "http://www.loc.gov/mods/v3");
        String invalidAttributeValue = MODS_XPATH_SELECTOR.selectString(finalDocument, "mods:modsDefinition/mods:part/mods:extent/@foobarFixedValueIsBar");

        final String sdoDocString = SdoUtils.parseDoc(sdodoc);
        String output = sdoDocString + "\n" + xmlFinal;
        assertEquals(output, "", invalidAttributeValue);
        assertTrue(output, sdoDocString.matches("(?s)^.*'Title'.*adresseavisen.*inputfield.*$"));
        assertFalse(output, sdoDocString.matches("(?s)^.*'Supplied':\\s'yes'.*$"));
        assertFalse(output, xmlFinal.contains("foobarFixedValueIsBar"));
        assertFalse(output, xmlFinal.contains("foobarFixedValueIsFoo"));

       XMLUnit.setIgnoreWhitespace(true);
       Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
       assertTrue(sdoDocString + "\n" + modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
       log.info(output);
    }

    /**
     * Test creation of new elements in MODS35-like trees.
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     * @throws SAXException
     */
     @Test
    public void testSettingElements() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                       return SdoUtils.getStringFromFileOnClasspath("MODS35_SIMPLE.xsd");
                    }
                };
            }
        };
        ModsTestHelper modsTestHelper = new ModsTestHelper();
        final String modsDatastreamContent = modsTestHelper.getModsSimpleString();
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);

        System.out.println(SdoUtils.parseDoc(sdodoc));

        SDOParsedXmlElementImpl detailElement = (SDOParsedXmlElementImpl) sdodoc.getRootSDOParsedXmlElement().getChildren().get(0).getChildren().get(0);
        SDOParsedXmlElementImpl newDetailElement = (SDOParsedXmlElementImpl) detailElement.create();
        SDOParsedXmlElementImpl detailTypeElement = (SDOParsedXmlElementImpl) newDetailElement.getChildren().get(4);
        assertEquals("Type", detailTypeElement.getLabel());
        detailTypeElement.setValue("yourenotmytype");

        sdodoc.getRootSDOParsedXmlElement().getChildren().get(1).create();
        String xmlFinal = sdodoc.dumpToString();
        Document finalDocument = DOM.stringToDOM(xmlFinal, true);

        final String sdoDocString = SdoUtils.parseDoc(sdodoc);
        String output = sdoDocString + "\n" + xmlFinal;
        assertTrue(output, xmlFinal.contains("yourenotmytype"));
         assertTrue(output, xmlFinal.contains("fooable\">foo"));


       log.info(output);
    }


    /**
     * Tests that parsing works correctly when a fixed-value attribute is present
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     */
    @Test
    public void testSdoFixedValuePresent() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS35_SIMPLE.xsd");
                    }
                };
            }
        };
        ModsTestHelper modsTestHelper = new ModsTestHelper();
        modsTestHelper.setAdditionalAttributeString("foobarFixedValueIsBar=\"bar\"");
        final String modsDatastreamContent = modsTestHelper.getModsSimpleString();
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String xmlFinal = sdodoc.dumpToString();
        String sdodocString = SdoUtils.parseDoc(sdodoc);
        Document finalDocument = DOM.stringToDOM(xmlFinal, true);
        XPathSelector MODS_XPATH_SELECTOR = DOM.createXPathSelector("mods", "http://www.loc.gov/mods/v3");
        String invalidAttributeValue = MODS_XPATH_SELECTOR.selectString(finalDocument, "mods:mods/mods:part/mods:extent/@foobarFixedValueIsBar");
        assertEquals(xmlFinal, "bar", invalidAttributeValue);
        assertTrue(xmlFinal, xmlFinal.contains("foobarFixedValueIsBar"));
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(sdodocString + "\n" + modsDatastreamContent + "\n" + xmlFinal, diff.similar());
        log.info(sdodocString + "\n" + xmlFinal);
    }

    /**
     * Tests that parsing works correctly when a fixed-value attribute is present but empty.
     * NOTE: As it doesn't seem to be straightforward to get this to work consistently, we take
     * the easier road and say that empty attributes are always removed.
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     */
    //@Test
    public void testSdoFixedValueEmpty() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS35_SIMPLE.xsd");
                    }
                };
            }
        };
        ModsTestHelper modsTestHelper = new ModsTestHelper();
        modsTestHelper.setAdditionalAttributeString("foobarFixedValueIsBar=\"\"");
        final String modsDatastreamContent = modsTestHelper.getModsSimpleString();
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String xmlFinal = sdodoc.dumpToString();
        String sdodocString = SdoUtils.parseDoc(sdodoc);
        Document finalDocument = DOM.stringToDOM(xmlFinal, true);
        XPathSelector MODS_XPATH_SELECTOR = DOM.createXPathSelector("mods", "http://www.loc.gov/mods/v3");
        String invalidAttributeValue = MODS_XPATH_SELECTOR.selectString(finalDocument, "mods:modsDefinition/mods:part/mods:extent/@foobarFixedValueIsBar");
        assertEquals(xmlFinal, "", invalidAttributeValue);
        assertTrue(sdodocString + "\n" + modsDatastreamContent + "\n" + xmlFinal, xmlFinal.contains("foobarFixedValueIsBar"));
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(sdodocString + "\n" + modsDatastreamContent + "\n" + xmlFinal, diff.similar());
        log.info(sdodocString + "\n" + xmlFinal);
    }

    /**
     * A leaf element with no attributes is presented as an input field. This example is based on the Title field
     * in Mods 3.1.
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     */
    @Test
    public void testLeafWithNoAttributes() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS31_SIMPLE.xsd");
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader().getResourceAsStream("MODS31_SIMPLE.xml"));
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String doc = SdoUtils.parseDoc(sdodoc);
        String xmlFinal = sdodoc.dumpToString();
        assertTrue(doc, doc.matches("(?s)^.*'Title':.*adresseavisen.*inputfield.*$"));
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(doc + "\n" + modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
        log.info(doc + "\n" + xmlFinal);
    }

    /**
     * Test that we can create a sibling element for an element of abstract type. The tests creates two new elements:
     * one complex, one leaf.
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     * @throws SAXException
     */
    @Test
    public void testCreateAbstractElement() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS31_SIMPLE.xsd");
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader().getResourceAsStream("MODS31_SIMPLE.xml"));
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String originalSdodocString = SdoUtils.parseDoc(sdodoc);
        SDOParsedXmlElement titleInfoElement =  sdodoc.getRootSDOParsedXmlElement().getChildren().get(0);
        assertEquals("Expected a titleinfo titleInfoElement here.", "Titleinfo", titleInfoElement.getLabel());
        SDOParsedXmlElement newElement =  titleInfoElement.create();

        //Create new Title element. This doesn't work yet.
        titleInfoElement.getChildren().get(0).create();

        String newSdodocString = SdoUtils.parseDoc(sdodoc);
        System.out.printf(SdoUtils.parseDoc(sdodoc));
        int oldCount = countMatches(originalSdodocString, "Titleinfo");
        int newCount = countMatches(newSdodocString, "Titleinfo");
        assertEquals("Should have one extra TitleInfo element.", oldCount+1, newCount);
        oldCount = countMatches(originalSdodocString, "'Title'");
        newCount = countMatches(newSdodocString, "'Title'");
        assertEquals("Should have two extra Title elements (one in an existing TitleInfo, one in the new TitleInfo).", oldCount+2, newCount);
        String xmlFinal = sdodoc.dumpToString();
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
    }

    /**
     * Very basic test that just creating an empty element has no effect on the final document.
      * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     * @throws SAXException
     */
    @Test
    public void testJustCreateAbstractElement() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS31_SIMPLE.xsd");
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader().getResourceAsStream("MODS31_SIMPLE.xml"));
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        sdodoc.getRootSDOParsedXmlElement().getChildren().get(0).create();
        String xmlFinal = sdodoc.dumpToString();
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(SdoUtils.parseDoc(sdodoc) + "\n" + modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
    }


    @Test
       public void testCreateAndSaveAbstractElement() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
           final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
               public Datastream getSchema() {
                   return new DatastreamStub() {
                       @Override
                       public String getContents() throws ServerOperationFailed {
                           return SdoUtils.getStringFromFileOnClasspath("MODS31_SIMPLE.xsd");
                       }
                   };
               }
           };
           final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader().getResourceAsStream("MODS31_SIMPLE.xml"));
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String originalSdodocString = SdoUtils.parseDoc(sdodoc);
        SDOParsedXmlElement titleInfoElement =  sdodoc.getRootSDOParsedXmlElement().getChildren().get(0);
        assertEquals("Expected a titleinfo titleInfoElement here.", "Titleinfo", titleInfoElement.getLabel());
        SDOParsedXmlElementImpl newElement =  (SDOParsedXmlElementImpl) titleInfoElement.create();
        newElement.getChildren().get(0).setValue("thenewtitle2");
        SDOParsedXmlElementImpl newTitleElement = (SDOParsedXmlElementImpl) titleInfoElement.getChildren().get(0).create();
        newTitleElement.setValue("thenewtitle1");
        //newTitleElement.submit(newTitleElement.getHelperContext());
        //newElement.submit(newElement.getHelperContext());

        final String sdodocString = SdoUtils.parseDoc(sdodoc);
        String xmlFinal = sdodoc.dumpToString();
        assertTrue(sdodocString + "\n" + xmlFinal, sdodocString.contains("thenewtitle1"));
        assertTrue(sdodocString + "\n" +  xmlFinal, xmlFinal.contains("thenewtitle1"));
        assertTrue(sdodocString + "\n" + xmlFinal, sdodocString.contains("thenewtitle2"));
        assertTrue(sdodocString + "\n" +  xmlFinal, xmlFinal.contains("thenewtitle2"));
        assertFalse(sdodocString + "\n" + xmlFinal, xmlFinal.contains("\"\""));
    }

    @Test
    public void testCreateAndSaveNestedElement() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS31_SIMPLE.xsd");
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader().getResourceAsStream("MODS31_SIMPLE.xml"));
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String originalSdodocString = SdoUtils.parseDoc(sdodoc);
        SDOParsedXmlElement titleInfoElement =  sdodoc.getRootSDOParsedXmlElement().getChildren().get(0);
        assertEquals("Expected a titleinfo titleInfoElement here.", "Titleinfo", titleInfoElement.getLabel());
        SDOParsedXmlElementImpl newTitleInfoElement =  (SDOParsedXmlElementImpl) titleInfoElement.create();
        newTitleInfoElement.getChildren().get(0).setValue("thenewtitle");
 
        final String sdodocString = SdoUtils.parseDoc(sdodoc);
        String xmlFinal = sdodoc.dumpToString();
        assertTrue(sdodocString + "\n" + xmlFinal, sdodocString.contains("thenewtitle"));
        assertTrue(sdodocString + "\n" + xmlFinal, xmlFinal.contains("thenewtitle"));
    }


    /**
     * If the type of a leaf cannot be determined then it must simply be omitted. This example is based on Mods 3.1
     * where the type of a Title element is not specified.
     * @throws ServerOperationFailed
     * @throws NotFoundException
     * @throws IOException
     * @throws XMLParseException
     */
    @Test
    public void testAbstractLeaf() throws ServerOperationFailed, NotFoundException, IOException, XMLParseException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return SdoUtils.getStringFromFileOnClasspath("MODS31_SIMPLE.xsd");
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader().getResourceAsStream("MODS31_EVEN_SIMPLER.xml"));
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String doc = SdoUtils.parseDoc(sdodoc);
        String xmlFinal = sdodoc.dumpToString();
        assertFalse(doc, doc.matches("(?s)^.*'Title':.*inputfield.*$"));
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(doc + "\n" + modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
        log.info(doc + "\n" + xmlFinal);
    }

    /**
     * Tests the case like the Title element in Mods 3.5 where the leaf element also has attributes so is represented
     * in the sdo tree with a set of sdo properties which include a 'Value' element. This also tests that a schema
     * with recursion does not lead to an infinite-depth tree / stack-overflow.
     * @throws XMLParseException
     * @throws ServerOperationFailed
     */
    @Test
    public void testLeafWithAttributes() throws XMLParseException, ServerOperationFailed, IOException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                       return SdoUtils.getStringFromFileOnClasspath("MODS35_SIMPLE.xsd");
                    }
                };
            }
        };
        ModsTestHelper modsTestHelper = new ModsTestHelper();
        final String modsDatastreamContent = modsTestHelper.getModsSimpleString();
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String doc = SdoUtils.parseDoc(sdodoc);
        String xmlFinal = sdodoc.dumpToString();
        assertTrue(doc, doc.matches("(?s).*'Title'[\\s+()]*\\n\\s*'Value'.*"));
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(doc + "\n" + modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
        log.info(doc + "\n" + xmlFinal);
    }

    /**
     * Test with the real MODS 3.5 schema and a realistic metadata document. The test simply confirms that the XML
     * returned by the sdo processing is equivalent to that put in.
     * @throws XMLParseException
     * @throws ServerOperationFailed
     * @throws IOException
     * @throws SAXException
     */
    @Test
    public void testRealisticSchema() throws XMLParseException, ServerOperationFailed, IOException, SAXException {
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                       return SdoUtils.getStringFromFileOnClasspath("MODS.xsd");
                    }
                };
            }
        };
        ModsTestHelper modsTestHelper = new ModsTestHelper();
        final String modsDatastreamContent = modsTestHelper.getModsString();
        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String xmlFinal = sdodoc.dumpToString();
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        assertTrue(SdoUtils.parseDoc(sdodoc) + "\n" + modsDatastreamContent + "\n" + xmlFinal,  diff.similar());
    }


    /**
     * Test the we can handle and display the value of accessCondition, which is a mixed element.
     * @throws IOException
     * @throws XMLParseException
     * @throws ServerOperationFailed
     * @throws SAXException
     */
    @Test
    public void testMissingAccessConditionValue() throws IOException, XMLParseException, ServerOperationFailed, SAXException {
        final String schemaString = SdoUtils.getStringFromFileOnClasspath("MODS35_ACCESSCONDITION.xsd");
        final String modsFilePath = "authority/simple/aktuelt5-Aktuelt.xml";
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return schemaString;
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(modsFilePath));

        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };
        Validator validator = new Validator(modsDatastreamContent);
        validator.useXMLSchema(true);
        validator.setJAXP12SchemaSource(new ByteArrayInputStream(schemaString.getBytes()));
        assertTrue(validator.toString(),validator.isValid());
        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        String xmlFinal = sdodoc.dumpToString();
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        Diff diff = XMLUnit.compareXML(modsDatastreamContent, xmlFinal);
        final String sdodocString = SdoUtils.parseDoc(sdodoc);
        assertTrue(sdodocString, sdodocString.contains("ressource"));
        System.out.println(sdodocString);
        assertTrue("SDO-DOC:\n" + sdodocString + "Original Content:\n" + modsDatastreamContent + "Final Content:\n" + xmlFinal, diff.similar());
    }

    /**
     * Test that we can also change the value of accessCondition content.
     * @throws IOException
     * @throws XMLParseException
     * @throws ServerOperationFailed
     * @throws SAXException
     */
    @Test
    public void testSetAccessConditionValue() throws IOException, XMLParseException, ServerOperationFailed, SAXException {
        final String schemaString = SdoUtils.getStringFromFileOnClasspath("MODS35_ACCESSCONDITION.xsd");
        final String modsFilePath = "authority/simple/aktuelt5-Aktuelt.xml";
        final DatastreamDeclaration modsSchemaDatastreamDeclaration = new DatastreamDeclarationStub() {
            public Datastream getSchema() {
                return new DatastreamStub() {
                    @Override
                    public String getContents() throws ServerOperationFailed {
                        return schemaString;
                    }
                };
            }
        };
        final String modsDatastreamContent = Strings.flush(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(modsFilePath));

        final Datastream modsDatastream = new DatastreamStub() {
            @Override
            public String getContents() throws ServerOperationFailed {
                return modsDatastreamContent;
            }
        };

        SDOParsedXmlDocumentImpl sdodoc = new SDOParsedXmlDocumentImpl(
                modsSchemaDatastreamDeclaration, modsDatastream);
        List<SDOParsedXmlElement> descendants = getAllDescendants(sdodoc.getRootSDOParsedXmlElement());
        for (SDOParsedXmlElement descendant: descendants) {
            if (descendant.getStringValue() != null && descendant.getStringValue().contains("ressource")) {
                descendant.setValue("abcba");
            }
        }
        final String sdodocString = SdoUtils.parseDoc(sdodoc);
        assertTrue(sdodocString, sdodocString.contains("abcba"));
        String xmlFinal = sdodoc.dumpToString();
        assertTrue(xmlFinal, xmlFinal.contains("abcba"));
        System.out.println(xmlFinal);
    }


    public List<SDOParsedXmlElement> getAllDescendants(SDOParsedXmlElement root) {
        List<SDOParsedXmlElement> children = root.getChildren();
        List<SDOParsedXmlElement> descendants = new ArrayList<SDOParsedXmlElement>(children);
        for (SDOParsedXmlElement child: children) {
            descendants.addAll(getAllDescendants(child));
        }
        return descendants;
    }

}
