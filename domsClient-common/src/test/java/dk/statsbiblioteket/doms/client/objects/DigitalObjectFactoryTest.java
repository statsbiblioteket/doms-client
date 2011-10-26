package dk.statsbiblioteket.doms.client.objects;

import dk.statsbiblioteket.doms.client.datastreams.Datastream;
import dk.statsbiblioteket.doms.client.datastreams.DatastreamDeclaration;
import dk.statsbiblioteket.doms.client.datastreams.DatastreamModel;
import dk.statsbiblioteket.doms.client.datastreams.InternalDatastream;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.relations.ObjectRelation;
import dk.statsbiblioteket.doms.client.relations.Relation;
import dk.statsbiblioteket.doms.client.sdo.SDOParsedXmlDocument;
import dk.statsbiblioteket.doms.client.sdo.SDOParsedXmlElement;
import dk.statsbiblioteket.doms.client.utils.Constants;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.*;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 9/15/11
 * Time: 1:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class DigitalObjectFactoryTest extends TestBase{


    public DigitalObjectFactoryTest() throws MalformedURLException {
        super();
    }

    @org.junit.Test
    public void testLoadTime() throws ServerOperationFailed {
        long before = System.currentTimeMillis();
        DigitalObject object = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        long after = System.currentTimeMillis();
        System.out.println("Time to load one object="+(after-before)+"ms");

        before = System.currentTimeMillis();
        DigitalObject object2 = factory.getDigitalObject("uuid:26e3048b-824d-476b-8b1f-671d7906e28a");
        after = System.currentTimeMillis();
        System.out.println("Time to load next object="+(after-before)+"ms");
        long load1 = after - before;


        before = System.currentTimeMillis();
        List<Relation> relations = object.getRelations();
        after = System.currentTimeMillis();
        System.out.println("Time to resolve relations for object1="+(after-before)+"ms, as there was "+relations.size()+" relations");

        before = System.currentTimeMillis();
        List<Relation> relations2 = object2.getRelations();
        after = System.currentTimeMillis();
        System.out.println("Time to resolve relations for object2="+(after-before)+"ms, as there was "+relations2.size()+" relations");
        load1 += after-before;
        load1 /= 2;

        before = System.currentTimeMillis();
        List<ObjectRelation> invrelations = object.getInverseRelations();
        after = System.currentTimeMillis();
        System.out.println("Time to resolve inverrelations object="+(after-before)+"ms, as there was "+invrelations.size()+" relations");

        System.out.println("Time to load one object is about "+load1+"ms when all dependencies are already loaded");

        before = System.currentTimeMillis();
        int sum = 0;
        for (Datastream datastream : object.getDatastreams()) {
            String contents = datastream.getContents();
            sum += contents.length();
        }
        after = System.currentTimeMillis();
        System.out.println("Time to resolve all datastreams in object1="+(after-before)+"ms, as there was "+object.getDatastreams().size()+" datastreams");

    }


    @org.junit.Test
    public void testGetDigitalObject1() throws Exception {
        DigitalObject cmdoms = factory.getDigitalObject("doms:ContentModel_DOMS");
        assertEquals(cmdoms.getState(), Constants.FedoraState.Active);
        assertTrue(cmdoms instanceof ContentModelObject);
    }

    @Test
    public void testViewRelations() throws Exception {
        DigitalObject cmdoms = factory.getDigitalObject("doms:ContentModel_Program");
        assertTrue(cmdoms instanceof ContentModelObject);
        if (cmdoms instanceof ContentModelObject) {
            ContentModelObject cmo = (ContentModelObject) cmdoms;
            assertNotNull(cmo.getRelationsWithViewAngle("SummaVisible"));
        }
    }

    @Test
    public void testInverseRelations() throws Exception {
        DigitalObject cmdoms = factory.getDigitalObject("doms:ContentModel_Program");
        assertTrue(cmdoms instanceof ContentModelObject);
        List<ObjectRelation> inverseRelations = cmdoms.getInverseRelations();
        for (ObjectRelation inverseRelation : inverseRelations) {
            assertEquals(inverseRelation.getObjectPid(),cmdoms.getPid());
            assertNotNull(inverseRelation.getSubjectPid());
        }
    }


    @Test
    public void testDatastreamModel() throws Exception {
        ContentModelObject cmProgram = (ContentModelObject)
                factory.getDigitalObject("doms:ContentModel_Program");
        assertTrue(cmProgram instanceof ContentModelObject);
        if (cmProgram instanceof ContentModelObject){
            DatastreamModel dsModel = cmProgram.getDsModel();
            assertTrue(dsModel.getDatastreamDeclarations().size() > 0);
            assertNotNull(dsModel.getMimeType());
            assertNotNull(dsModel.getFormatURI());
            DatastreamDeclaration dsDcl = dsModel.getDatastreamDeclarations().get(0);
        }
    }

    @Test
    public void testDatastreamModel2() throws Exception {
        DigitalObject template =
                factory.getDigitalObject("doms:Template_Program");
        Set<DatastreamDeclaration> declarations = template.getDatastream("PBCORE").getDeclarations();
        assertTrue(declarations.size() > 0);
        for (DatastreamDeclaration declaration : declarations) {
            assertEquals(declaration.getName(),"PBCORE");
            assertTrue(declaration.getPresentation() == Constants.GuiRepresentation.editable);
            Datastream pbcoreSchema = declaration.getSchema();
            if (pbcoreSchema != null){
                assertNotNull(pbcoreSchema.getContents());
            } else {
                fail();
            }
        }
    }

    @org.junit.Test
    public void testGetDigitalObject2() throws Exception {
        DigitalObject cmdoms = factory.getDigitalObject("doms:Root_Collection");
        assertEquals(cmdoms.getState(), Constants.FedoraState.Active);
        List<ObjectRelation> inverseRels = cmdoms.getInverseRelations();
        assertNotNull(inverseRels);
        assertTrue(inverseRels.size() > 3);

    }

    @org.junit.Test
    public void testSaveState() throws Exception {

        int i = 0;


        //Load the object, and assert that everything is Active
        DigitalObject object = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        Set<DigitalObject> children = object.getChildObjects("SummaVisible");
        assertTrue(object.getState() == Constants.FedoraState.Active);
        for (DigitalObject child : children) {
            assertTrue(child.getState()== Constants.FedoraState.Active);
            i++;
        }
        assertTrue(i>0);
        i = 0;

        //Set the object and all subobjects to Inactive
        object.setState(Constants.FedoraState.Inactive, "SummaVisible");
        object.save("SummaVisible");
        for (DigitalObject child : children) {
            assertTrue(child.getState()== Constants.FedoraState.Inactive);
            i++;
        }
        assertTrue(i>0);
        i = 0;


        //To be sure that nothing is in cache, make a new factory
        setUp();

        //Load the object, and check that everything is now in Inactive
        DigitalObject object2 = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        assertTrue(object2.getState() == Constants.FedoraState.Inactive);
        Set<DigitalObject> children2 = object2.getChildObjects("SummaVisible");
        for (DigitalObject child : children2) {
            assertTrue(child.getState()== Constants.FedoraState.Inactive);
            i++;
        }
        assertTrue(i>0);
        i = 0;

        //Then set everything to Active again
        object2.setState(Constants.FedoraState.Active, "SummaVisible");
        object2.save("SummaVisible");
        assertTrue(object2.getState() == Constants.FedoraState.Active);
        for (DigitalObject child : children2) {
            assertTrue(child.getState()== Constants.FedoraState.Active);
            i++;
        }
        assertTrue(i>0);
        i = 0;

        setUp();

        DigitalObject object3 = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        Set<DigitalObject> children3 = object3.getChildObjects("SummaVisible");
        for (DigitalObject child : children3) {
            assertTrue(child.getState()== Constants.FedoraState.Active);
            i++;
        }
        assertTrue(i>0);
        i = 0;

    }

    @Test
    public void testMostSpecificCM() throws ServerOperationFailed {
        DigitalObject object = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        if (object instanceof DataObject) {
            DataObject dataObject = (DataObject) object;
            String cmTitle = dataObject.getContentmodelTitle();
            assertEquals(cmTitle,"Radio/TV Program");

        }   else {
            fail();
        }

    }


    private void changeField(SDOParsedXmlElement doc, String field, String newvalue){
        ArrayList<SDOParsedXmlElement> children = doc.getChildren();
        for (SDOParsedXmlElement child : children) {
            if (child.isLeaf()){
                if (child.getLabel().equals(field)){
                    child.setValue(newvalue);
                }
            } else {
                changeField(child, field, newvalue);
            }
        }
    }

    @org.junit.Test
    public void testSaveDatastream() throws Exception {




        //Load the object, and assert that everything is Active
        DigitalObject object = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        Datastream datastream = object.getDatastream("PBCORE");
        SDOParsedXmlDocument doc = datastream.getSDOParsedDocument();
        object.setState(Constants.FedoraState.Inactive);
        object.save();
        String originaldoc = doc.dumpToString();

        changeField(doc.getRootSDOParsedXmlElement(),"test", "testvalue");

        String unchangeddoc = doc.dumpToString();

        assertEquals(originaldoc,unchangeddoc);

        changeField(doc.getRootSDOParsedXmlElement(),"Subject", "test of change: "+Math.random());

        String changeddoc = doc.dumpToString();

        assertNotSame(originaldoc, unchangeddoc);


        if (datastream instanceof InternalDatastream) {
            InternalDatastream internalDatastream = (InternalDatastream) datastream;
            internalDatastream.replace(doc.dumpToString());
        }
        object.save();

        setUp();
        DigitalObject object2 = factory.getDigitalObject("uuid:f8f1b607-1394-418a-a90e-e65d1b4bf91f");
        SDOParsedXmlDocument doc2 = object2.getDatastream("PBCORE").getSDOParsedDocument();
        String rereaddoc = doc2.dumpToString();
        assertEquals(changeddoc,rereaddoc);
    }

}
