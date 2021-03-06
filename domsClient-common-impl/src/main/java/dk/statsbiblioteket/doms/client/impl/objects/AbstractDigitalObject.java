package dk.statsbiblioteket.doms.client.impl.objects;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import dk.statsbiblioteket.doms.central.Checksum;
import dk.statsbiblioteket.doms.central.DatastreamProfile;
import dk.statsbiblioteket.doms.central.InvalidCredentialsException;
import dk.statsbiblioteket.doms.central.InvalidResourceException;
import dk.statsbiblioteket.doms.central.Link;
import dk.statsbiblioteket.doms.central.MethodFailedException;
import dk.statsbiblioteket.doms.central.ObjectProfile;
import dk.statsbiblioteket.doms.central.Relation;
import dk.statsbiblioteket.doms.client.datastreams.Datastream;
import dk.statsbiblioteket.doms.client.exceptions.NotFoundException;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.exceptions.ValidationFailed;
import dk.statsbiblioteket.doms.client.exceptions.XMLParseException;
import dk.statsbiblioteket.doms.client.impl.datastreams.ExternalDatastreamImpl;
import dk.statsbiblioteket.doms.client.impl.datastreams.InternalDatastreamImpl;
import dk.statsbiblioteket.doms.client.impl.datastreams.SaveableDatastreamImpl;
import dk.statsbiblioteket.doms.client.impl.links.LinkPatternImpl;
import dk.statsbiblioteket.doms.client.impl.methods.MethodImpl;
import dk.statsbiblioteket.doms.client.impl.methods.ParameterImpl;
import dk.statsbiblioteket.doms.client.impl.relations.LiteralRelationImpl;
import dk.statsbiblioteket.doms.client.impl.relations.ObjectRelationImpl;
import dk.statsbiblioteket.doms.client.links.LinkPattern;
import dk.statsbiblioteket.doms.client.methods.Method;
import dk.statsbiblioteket.doms.client.methods.Parameter;
import dk.statsbiblioteket.doms.client.methods.ParameterType;
import dk.statsbiblioteket.doms.client.objects.CollectionObject;
import dk.statsbiblioteket.doms.client.objects.ContentModelObject;
import dk.statsbiblioteket.doms.client.objects.DigitalObject;
import dk.statsbiblioteket.doms.client.objects.DigitalObjectFactory;
import dk.statsbiblioteket.doms.client.relations.LiteralRelation;
import dk.statsbiblioteket.doms.client.relations.ObjectRelation;
import dk.statsbiblioteket.doms.client.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * The common functionality of a digital object is implemented here.
 */
public abstract class AbstractDigitalObject implements DigitalObject {

    protected boolean datastreamsLoaded = false;
    Logger logger = Logger.getLogger(AbstractDigitalObject.class.getName());

    protected ObjectProfile profile;
    protected CentralWebservice api;
    protected DigitalObjectFactory factory;
    protected Set<SaveableDatastreamImpl> deletedDSs, addedDSs;
    protected Set<Datastream> datastreams;
    protected TreeSet<dk.statsbiblioteket.doms.client.relations.Relation> relations;
    protected HashSet<Method> dynamicMethods;
    protected HashSet<Method> staticMethods;
    private String pid;
    private List<ContentModelObject> type;
    private String title;
    private String titleOriginal;
    private Constants.FedoraState state;
    private Constants.FedoraState stateOriginal;
    private Date lastModified;
    private Date created;
    private TreeSet<dk.statsbiblioteket.doms.client.relations.Relation> removedRelations;
    private TreeSet<dk.statsbiblioteket.doms.client.relations.Relation> addedRelations;
    private List<ObjectRelation> inverseRelations;
    private boolean cmloaded = false;
    private boolean relsloaded = false;
    private boolean invrelsloaded = false;
    private boolean profileloaded = false;
    private boolean statePreSaved = false;
    private boolean methodsParsed = false;
    private boolean linksParsed = false;
    private List<LinkPattern> links;


    public AbstractDigitalObject(String pid, CentralWebservice api, DigitalObjectFactory factory) throws
                                                                                                  ServerOperationFailed {

        this.pid = pid;
        this.api = api;
        this.factory = factory;
        type = new ArrayList<ContentModelObject>();
        datastreams = new LinkedHashSet<Datastream>();
        addedDSs = new LinkedHashSet<SaveableDatastreamImpl>();
        deletedDSs = new LinkedHashSet<SaveableDatastreamImpl>();

        relations = new TreeSet<dk.statsbiblioteket.doms.client.relations.Relation>();
        removedRelations = new TreeSet<dk.statsbiblioteket.doms.client.relations.Relation>();
        addedRelations = new TreeSet<dk.statsbiblioteket.doms.client.relations.Relation>();

        inverseRelations = new ArrayList<ObjectRelation>();

    }

    public AbstractDigitalObject(ObjectProfile profile, CentralWebservice api, DigitalObjectFactory factory) throws
                                                                                                             ServerOperationFailed {
        this(profile.getPid(), api, factory);
        this.profile = profile;
        loadProfile();
    }

    @Override
    public String getPid() {
        return pid;
    }

    @Override
    public List<ContentModelObject> getType() throws ServerOperationFailed {
        loadContentModels();
        return Collections.unmodifiableList(type);
    }

    @Override
    public String getTitle() throws ServerOperationFailed {
        loadProfile();
        return title;
    }

    @Override
    public void setTitle(String title) throws ServerOperationFailed {
        loadProfile();
        this.title = title;
    }

    @Override
    public Constants.FedoraState getState() throws ServerOperationFailed {
        loadProfile();
        return state;
    }

    @Override
    public void setState(Constants.FedoraState state) throws ServerOperationFailed {
        loadProfile();
        this.state = state;
    }

    @Override
    public void setState(Constants.FedoraState state, String viewAngle) throws ServerOperationFailed {
        setState(state);
        Set<DigitalObject> children = getChildObjects(viewAngle);
        for (DigitalObject child : children) {
            child.setState(state);
        }
    }


    @Override
    public Date getLastModified() throws ServerOperationFailed {
        loadProfile();
        return lastModified;
    }

    @Override
    public Date getCreated() throws ServerOperationFailed {
        loadProfile();
        return created;
    }

    @Override
    public List<Datastream> getDatastreams() throws ServerOperationFailed {
        loadProfile();
        return Collections.unmodifiableList(new ArrayList<Datastream>(datastreams));
    }

    @Override
    public Datastream getDatastream(String id) throws ServerOperationFailed, NotFoundException {
        loadProfile();
        for (Datastream datastream : datastreams) {
            if (datastream.getId().equals(id)) {
                return datastream;
            }
        }
        throw new NotFoundException("Datastream not found. Id: " + id);
    }

    @Override
    public void addDatastream(Datastream addition) throws ServerOperationFailed {
        loadProfile();
        if (addition instanceof SaveableDatastreamImpl) {
            SaveableDatastreamImpl saveableDatastream = (SaveableDatastreamImpl) addition;
            addedDSs.add(saveableDatastream);
        }
        datastreams.add(addition);
    }

    @Override
    public Datastream addInternalDatastream(String name) throws ServerOperationFailed {
        loadProfile();
        DatastreamProfile dsProfile = new DatastreamProfile();
        dsProfile.setId(name);
        dsProfile.setInternal(true);
        Checksum checksum = new Checksum();
        checksum.setType("MD5");
        dsProfile.setChecksum(checksum);
        dsProfile.setMimeType("text/xml");
        //TODO populate values
        InternalDatastreamImpl newDs = new InternalDatastreamImpl(dsProfile, this, api, true);
        datastreams.add(newDs);
        addedDSs.add(newDs);
        return newDs;
    }


    @Override
    public void removeDatastream(Datastream deleted) throws ServerOperationFailed {
        loadProfile();
        if (deleted instanceof SaveableDatastreamImpl) {
            SaveableDatastreamImpl saveableDatastream = (SaveableDatastreamImpl) deleted;
            deletedDSs.add(saveableDatastream);
        }

        datastreams.remove(deleted);
    }

    @Override
    public List<dk.statsbiblioteket.doms.client.relations.Relation> getRelations() throws ServerOperationFailed {
        loadRelations();
        TreeSet<dk.statsbiblioteket.doms.client.relations.Relation> totalSet
                = new TreeSet<dk.statsbiblioteket.doms.client.relations.Relation>(relations);
        totalSet.addAll(addedRelations);
        totalSet.removeAll(removedRelations);
        return Collections.unmodifiableList(new ArrayList<dk.statsbiblioteket.doms.client.relations.Relation>(totalSet));
    }

    @Override
    public void removeRelation(dk.statsbiblioteket.doms.client.relations.Relation relation) throws
                                                                                            ServerOperationFailed {
        loadRelations();
        if (relation.getSubjectPid().equals(this.getPid())) {
            privateRemoveRelation(relation);
        }
    }

    @Override
    public List<ObjectRelation> getInverseRelations() throws ServerOperationFailed {
        loadInverseRelations();
        return Collections.unmodifiableList(inverseRelations);
    }

    @Override
    public List<ObjectRelation> getInverseRelations(String predicate) throws ServerOperationFailed {
        List<Relation> frelations;
        try {
            frelations = api.getInverseRelationsWithPredicate(pid, predicate);
        } catch (Exception e) {
            throw new ServerOperationFailed("Failed to load inverse relations", e);
        }
        List<ObjectRelation> result = new ArrayList<ObjectRelation>();
        for (dk.statsbiblioteket.doms.central.Relation frelation : frelations) {
            if (frelation.getPredicate().equals(predicate)) {//TODO remove?
                result.add(
                        new ObjectRelationImpl(
                                frelation.getSubject(), frelation.getPredicate(), frelation.getObject(), factory)
                          );
            }
        }
        return result;
    }

    /**
     * Do not call this.
     *
     * @throws ServerOperationFailed
     */
    protected synchronized void loadRelations() throws ServerOperationFailed {
        if (relsloaded) {
            return;
        }

        List<dk.statsbiblioteket.doms.central.Relation> frelations = profile.getRelations();

        for (dk.statsbiblioteket.doms.central.Relation frelation : frelations) {
            if (frelation.isLiteral()) {
                relations.add(
                        new LiteralRelationImpl(
                                this.getPid(), frelation.getPredicate(), frelation.getObject(), factory)
                             );
            } else {
                relations.add(
                        new ObjectRelationImpl(
                                this.getPid(), frelation.getPredicate(), frelation.getObject(), factory)
                             );
            }
        }
        relsloaded = true;
    }


    /**
     * Do not call this.
     *
     * @throws ServerOperationFailed
     */
    protected synchronized void loadInverseRelations() throws ServerOperationFailed {
        if (invrelsloaded) {
            return;
        }

        List<Relation> frelations;
        try {
            frelations = api.getInverseRelations(pid);
        } catch (Exception e) {
            throw new ServerOperationFailed("Failed to load inverse relations", e);
        }

        for (dk.statsbiblioteket.doms.central.Relation frelation : frelations) {
            inverseRelations.add(
                    new ObjectRelationImpl(
                            frelation.getSubject(), frelation.getPredicate(), frelation.getObject(), factory)
                                );
        }
        invrelsloaded = true;
    }


    /**
     * Do not call this.
     *
     * @throws ServerOperationFailed
     */
    protected synchronized void loadContentModels() throws ServerOperationFailed {
        if (cmloaded) {
            return;
        }


        for (String contentModel : profile.getContentmodels()) {
            DigitalObject cm_object = factory.getDigitalObject(contentModel);
            if (cm_object instanceof ContentModelObject) {
                ContentModelObject object = (ContentModelObject) cm_object;
                type.add(object);
            } else {
                throw new ServerOperationFailed(
                        "Object '" + pid + "' has the content model '" + contentModel +
                        "' declared, but this is not a content model"
                );
            }
        }
        cmloaded = true;
    }

    protected synchronized void loadProfile() throws ServerOperationFailed {
        if (profileloaded) {
            return;
        }

        if (profile == null) {
            try {
                profile = api.getObjectProfile(pid);
            } catch (Exception e) {
                throw new ServerOperationFailed("Failed to retrieve Profile", e);
            }
        }

        state = Constants.FedoraState.fromString(profile.getState());
        stateOriginal = state;
        created = new Date(profile.getCreatedDate());
        lastModified = new Date(profile.getModifiedDate());
        title = profile.getTitle();
        titleOriginal = title;

        loadDatastreams();

        profileloaded = true;
    }

    protected synchronized void loadDatastreams() throws ServerOperationFailed {
        if (datastreamsLoaded) {
            return;
        }
        for (DatastreamProfile datastreamProfile : profile.getDatastreams()) {
            if (datastreamProfile.isInternal()) {
                datastreams.add(new InternalDatastreamImpl(datastreamProfile, this, api));
            } else {
                datastreams.add(new ExternalDatastreamImpl(datastreamProfile, this, api));
            }
        }
        datastreamsLoaded = true;
    }

    @Override
    public Set<DigitalObject> getChildObjects(String viewAngle) throws ServerOperationFailed {
        Set<String> viewRelationNames = new HashSet<String>();
        Set<DigitalObject> children = new LinkedHashSet<DigitalObject>();
        for (ContentModelObject contentModelObject : getType()) {
            try {
                List<String> theseRels = contentModelObject.getRelationsWithViewAngle(viewAngle);
                if (theseRels != null) {
                    viewRelationNames.addAll(theseRels);
                }
            } catch (ServerOperationFailed e) {
                //pass quietly
            }
        }
        for (dk.statsbiblioteket.doms.client.relations.Relation rel : getRelations()) {
            if (viewRelationNames.contains(rel.getPredicate())) {
                if (rel instanceof ObjectRelation) {
                    ObjectRelation objectRelation = (ObjectRelation) rel;
                    children.add(objectRelation.getObject());
                }
            }
        }
        return children;
    }


    private void preSaveDatastreams() throws ServerOperationFailed, XMLParseException {
        for (SaveableDatastreamImpl deletedDS : deletedDSs) {
            deletedDS.markAsDeleted();
        }
        for (SaveableDatastreamImpl addedDS : addedDSs) {
            addedDS.create();
        }
        for (Datastream datastream : datastreams) {
            if (datastream instanceof SaveableDatastreamImpl) {
                logger.info("Saving saveable datastream: " + datastream.getId() + " for " + getPid());
                SaveableDatastreamImpl saveableDatastream = (SaveableDatastreamImpl) datastream;
                saveableDatastream.preSave();
            } else {
                logger.info("Not saving unsaveble datastream: " + datastream.getId() + " for " + " it is an instance of " + datastream.getClass().getName());
            }
        }
    }


    private void preSaveState() throws ServerOperationFailed {
        if (state.equals(stateOriginal)) {
            return;
        }
        try {
            List<String> pid_list = new ArrayList<String>(1);
            pid_list.add(pid);
            switch (state) {
                case Active:
                    api.markPublishedObject(pid_list, "Object '" + pid + "' marked as active");
                    break;
                case Deleted:
                    api.deleteObject(pid_list, "Object '" + pid + "' marked as deleted");
                    break;
                case Inactive:
                    api.markInProgressObject(pid_list, "Object '" + pid + "' marked as inactive");
            }
            statePreSaved = true;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message.contains("dk.statsbiblioteket.doms.ecm.fedoravalidatorhook.ValidationFailedException")) {
                int begin = message.indexOf("<validation");
                int end = message.indexOf("</validation>");
                if (end > begin && begin > 0) {
                    end += "</validation>".length();
                    String xmlMessage = message.substring(begin, end);
                    throw new ValidationFailed(xmlMessage, e);
                }
            }
            throw new ServerOperationFailed(e.getMessage(), e);
        }

    }

    private void postSaveState() {
        stateOriginal = state;
        statePreSaved = false;
    }


    private void undoSaveState() throws ServerOperationFailed {
        try {
            if (statePreSaved) {
                List<String> pid_list = new ArrayList<String>(1);
                pid_list.add(pid);
                switch (stateOriginal) {
                    case Active:
                        api.markPublishedObject(pid_list, "Object '" + pid + "' marked as active");
                        break;
                    case Deleted:
                        api.deleteObject(pid_list, "Object '" + pid + "' marked as deleted");
                        break;
                    case Inactive:
                        api.markInProgressObject(pid_list, "Object '" + pid + "' marked as inactive");
                }
            }
            state = stateOriginal;
            statePreSaved = false;


        } catch (Exception e) {
            throw new ServerOperationFailed(e);
        }
    }


    protected void preSave(String viewAngle) throws ServerOperationFailed, XMLParseException {

        List<AbstractDigitalObject> saved = new ArrayList<AbstractDigitalObject>();
        try {
            Set<DigitalObject> children = getChildObjects(viewAngle);
            for (DigitalObject child : children) {
                if (child instanceof AbstractDigitalObject) {
                    AbstractDigitalObject abstractChild = (AbstractDigitalObject) child;
                    abstractChild.preSave(viewAngle);
                }
            }
            if (!getState().equals(Constants.FedoraState.Active)) {
                preSaveState();
                preSaveTitle();
                preSaveDatastreams();
                preSaveRelations();
            } else {
                preSaveDatastreams();
                preSaveRelations();
                preSaveTitle();
                preSaveState();
            }

        } catch (ServerOperationFailed e) {
            for (AbstractDigitalObject digitalObject : saved) {
                digitalObject.undoSave(viewAngle);
            }
            try {
                undoSave(viewAngle);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            throw new ServerOperationFailed(e.getMessage(), e);
        } catch (XMLParseException e) {
            for (AbstractDigitalObject digitalObject : saved) {
                digitalObject.undoSave(viewAngle);
            }
            try {
                undoSave(viewAngle);
            } catch (Exception e2) {
                e2.printStackTrace();//TODO log
            }
            throw new XMLParseException(e.getMessage(), e);
        }


    }

    private void preSaveTitle() throws ServerOperationFailed {
        if (title.equals(titleOriginal)) {
            return;
        }
        try {
            api.setObjectLabel(this.getPid(), title, "Changing the object label");
        } catch (InvalidCredentialsException e) {
            throw new ServerOperationFailed(e);
        } catch (InvalidResourceException e) {
            throw new ServerOperationFailed(e);
        } catch (MethodFailedException e) {
            throw new ServerOperationFailed(e);
        }
    }

    protected void preSaveRelations() throws ServerOperationFailed {
        try {

            for (dk.statsbiblioteket.doms.client.relations.Relation addedRelation : addedRelations) {
                Relation apiRelation = toApiRelation(addedRelation);
                api.addRelation(this.getPid(), apiRelation, "Added a relation from the Doms Client");
            }
            for (dk.statsbiblioteket.doms.client.relations.Relation removedRelation : removedRelations) {
                Relation apiRelation = toApiRelation(removedRelation);
                api.deleteRelation(this.getPid(), apiRelation, "Added a relation from the Doms Client");
            }

        } catch (InvalidCredentialsException e) {
            throw new ServerOperationFailed(e);
        } catch (InvalidResourceException e) {
            throw new ServerOperationFailed(e);
        } catch (MethodFailedException e) {
            throw new ServerOperationFailed(e);
        }

    }


    protected void postSave(String viewAngle) throws ServerOperationFailed {
        Set<DigitalObject> children = getChildObjects(viewAngle);
        for (DigitalObject child : children) {
            if (child instanceof AbstractDigitalObject) {
                AbstractDigitalObject abstractChild = (AbstractDigitalObject) child;
                abstractChild.postSave(viewAngle);
            }
        }
        postSaveDatastreams();
        postSaveRelations();
        postSaveTitle();
        postSaveState();
    }

    private void postSaveTitle() {
        titleOriginal = title;
    }

    private void postSaveRelations() {
        for (dk.statsbiblioteket.doms.client.relations.Relation addedRelation : addedRelations) {
            relations.add(addedRelation);
        }
        addedRelations.clear();
        for (dk.statsbiblioteket.doms.client.relations.Relation removedRelation : removedRelations) {
            relations.remove(removedRelation);
        }
        removedRelations.clear();
    }

    protected void postSaveDatastreams() {
        for (Datastream datastream : datastreams) {
            if (datastream instanceof SaveableDatastreamImpl) {
                SaveableDatastreamImpl saveableDatastream = (SaveableDatastreamImpl) datastream;
                saveableDatastream.postSave();
            }
        }

    }

    protected void undoSave(String viewAngle) throws ServerOperationFailed {
        Set<DigitalObject> children = getChildObjects(viewAngle);
        for (DigitalObject child : children) {
            if (child instanceof AbstractDigitalObject) {
                AbstractDigitalObject abstractChild = (AbstractDigitalObject) child;
                abstractChild.undoSave(viewAngle);
            }
        }
        if (getState().equals(Constants.FedoraState.Active)) {
            undoSaveState();
            undoSaveDatastreams();
            undoSaveRelations();
            undoSaveTitle();
        } else {
            undoSaveDatastreams();
            undoSaveRelations();
            undoSaveTitle();
            undoSaveState();
        }

    }

    private void undoSaveTitle() throws ServerOperationFailed {
        if (title.equals(titleOriginal)) {
            return;
        }
        try {
            api.setObjectLabel(this.getPid(), titleOriginal, "Undoing change of object label");
            titleOriginal = title;
        } catch (InvalidCredentialsException e) {
            throw new ServerOperationFailed(e);
        } catch (InvalidResourceException e) {
            throw new ServerOperationFailed(e);
        } catch (MethodFailedException e) {
            throw new ServerOperationFailed(e);
        }
    }

    private void undoSaveRelations() throws ServerOperationFailed {
        try {
            for (dk.statsbiblioteket.doms.client.relations.Relation removedRelation : removedRelations) {
                Relation apiRelation = toApiRelation(removedRelation);
                api.addRelation(this.getPid(), apiRelation, "Added a relation from the Doms Client");
            }
            for (dk.statsbiblioteket.doms.client.relations.Relation addedRelation : addedRelations) {
                Relation apiRelation = toApiRelation(addedRelation);
                api.deleteRelation(this.getPid(), apiRelation, "Added a relation from the Doms Client");
            }
        } catch (InvalidCredentialsException e) {
            throw new ServerOperationFailed(e);
        } catch (InvalidResourceException e) {
            throw new ServerOperationFailed(e);
        } catch (MethodFailedException e) {
            throw new ServerOperationFailed(e);
        }


    }

    private Relation toApiRelation(dk.statsbiblioteket.doms.client.relations.Relation addedRelation) {
        Relation apiRelation = new Relation();

        if (addedRelation instanceof ObjectRelation) {
            ObjectRelation relation = (ObjectRelation) addedRelation;
            apiRelation.setLiteral(false);
            apiRelation.setObject(Constants.ensurePID(relation.getObjectPid()));
            apiRelation.setSubject(this.getPid());
            apiRelation.setPredicate(relation.getPredicate());
        } else if (addedRelation instanceof LiteralRelation) {
            LiteralRelation relation = (LiteralRelation) addedRelation;
            apiRelation.setLiteral(true);
            apiRelation.setObject(relation.getObject());
            apiRelation.setPredicate(relation.getPredicate());
            apiRelation.setSubject(this.getPid());
        }
        return apiRelation;
    }

    private void undoSaveDatastreams() throws ServerOperationFailed {
        for (Datastream datastream : datastreams) {
            if (datastream instanceof SaveableDatastreamImpl) {
                SaveableDatastreamImpl saveableDatastream = (SaveableDatastreamImpl) datastream;
                saveableDatastream.undoSave();
            }
        }
    }


    public void save(String viewAngle) throws ServerOperationFailed, XMLParseException {
        this.preSave(viewAngle);
        this.postSave(viewAngle);
    }


    public void save() throws ServerOperationFailed, XMLParseException {
        save("UNUSEDVIEWANGLE");
    }

    @Override
    public String toString() {
        return pid;
    }

    @Override
    public ObjectRelation addObjectRelation(String predicate, DigitalObject object) throws ServerOperationFailed {
        ObjectRelation rel = new ObjectRelationImpl(this.getPid(), predicate, object.getPid(), factory);
        privateAddRelation(rel);
        return rel;

    }

    @Override
    public LiteralRelation addLiteralRelation(String predicate, String value) {
        LiteralRelation rel = new LiteralRelationImpl(this.getPid(), predicate, value, factory);
        privateAddRelation(rel);
        return rel;
    }

    @Override
    public Set<CollectionObject> getCollections() throws ServerOperationFailed {
        throw new IllegalAccessError("Missing object");
    }

    @Override
    public void addToCollection(CollectionObject collection) throws ServerOperationFailed {
        collection.addObject(this);
    }

    private void privateAddRelation(dk.statsbiblioteket.doms.client.relations.Relation relation) {
        boolean isOriginal = relations.contains(relation);
        boolean isAlreadyRemoved = removedRelations.contains(relation);
        boolean isAdded = addedRelations.contains(relation);

        if (isAlreadyRemoved) {
            removedRelations.remove(relation);
            return;
        }
        if (!isAdded && !isOriginal) {
            addedRelations.add(relation);
        }
    }

    private void privateRemoveRelation(dk.statsbiblioteket.doms.client.relations.Relation relation) {
        boolean isOriginal = relations.contains(relation);
        boolean isAlreadyRemoved = removedRelations.contains(relation);
        boolean isAdded = addedRelations.contains(relation);
        if (!(isOriginal || isAdded)) {//is this anywhere?
            return;//if not present, return
        }

        if (isAdded) {
            addedRelations.remove(relation);
            return;
        }
        if (!isAlreadyRemoved && isOriginal) {
            removedRelations.add(relation);
        }

    }


    protected synchronized void parseMethods() throws ServerOperationFailed {
        if (methodsParsed) {
            return;
        }
        List<dk.statsbiblioteket.doms.central.Method> methodsSoap;
        try {
            methodsSoap = api.getMethods(this.getPid());
        } catch (Exception e) {
            throw new ServerOperationFailed("Failed to parse Methods", e);
        }
        dynamicMethods = new LinkedHashSet<dk.statsbiblioteket.doms.client.methods.Method>();
        staticMethods = new LinkedHashSet<dk.statsbiblioteket.doms.client.methods.Method>();
        for (dk.statsbiblioteket.doms.central.Method soapmethod : methodsSoap) {
            Set<dk.statsbiblioteket.doms.client.methods.Parameter> myparameters = new LinkedHashSet<Parameter>();
            for (dk.statsbiblioteket.doms.central.Parameter soapparameter : soapmethod.getParameters().getParameter()) {
                String parameterTypeString = soapparameter.getType();

                dk.statsbiblioteket.doms.client.methods.Parameter myparameter = new ParameterImpl(
                        soapparameter.getName(),
                        ParameterType.valueOf(parameterTypeString),
                        "",
                        soapparameter.isRequired(),
                        soapparameter.isRepeatable(),
                        soapparameter.getConfig());
                myparameters.add(myparameter);
            }
            MethodImpl myMethod = new MethodImpl(api, this, soapmethod.getName(), myparameters);
            if (soapmethod.getType().equals("dynamic")) {
                dynamicMethods.add(myMethod);
            }
            if (soapmethod.getType().equals("static")) {
                staticMethods.add(myMethod);
            }

        }
        methodsParsed = true;
    }


    @Override
    public Set<Method> getMethods() throws ServerOperationFailed {
        parseMethods();
        return Collections.unmodifiableSet(dynamicMethods);
    }

    @Override
    public synchronized List<LinkPattern> getLinkPatterns() throws ServerOperationFailed {
        parseLinks();
        return Collections.unmodifiableList(links);
    }

    private synchronized void parseLinks() throws ServerOperationFailed {
        if (linksParsed) {
            return;
        }

        links = new ArrayList<LinkPattern>();
        List<Link> linksSoap;
        try {
            //Date here?
            linksSoap = api.getObjectLinks(this.getPid(), -1);

        } catch (Exception e) {
            throw new ServerOperationFailed("Failed to parse Object Links", e);
        }
        for (Link linkSoap : linksSoap) {
            LinkPattern linkPattern = new LinkPatternImpl(
                    linkSoap.getName(), linkSoap.getDescription(), linkSoap.getValue());
            links.add(linkPattern);
        }
        linksParsed = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractDigitalObject)) {
            return false;
        }

        AbstractDigitalObject that = (AbstractDigitalObject) o;

        if (!pid.equals(that.pid)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return pid.hashCode();
    }
}
