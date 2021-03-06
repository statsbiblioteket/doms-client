package dk.statsbiblioteket.doms.client.impl.objects;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import dk.statsbiblioteket.doms.central.ObjectProfile;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.objects.ContentModelObject;
import dk.statsbiblioteket.doms.client.objects.DataObject;
import dk.statsbiblioteket.doms.client.objects.DigitalObjectFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data objects are the objects that actually holds the data in DOMS. TODO implement
 */
public class DataObjectImpl extends AbstractDigitalObject implements DataObject {


    private String contentModelTitle;

    public DataObjectImpl(ObjectProfile profile, CentralWebservice api, DigitalObjectFactory factory) throws
                                                                                                      ServerOperationFailed {
        super(profile, api, factory);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public synchronized String getContentmodelTitle() throws ServerOperationFailed {

        if (contentModelTitle != null) {
            return contentModelTitle;
        }
        List<ContentModelObject> tmp = getType();
        List<ContentModelObject> contentModels = new ArrayList<ContentModelObject>();
        for (ContentModelObject contentModel : tmp) {
            if (!contentModel.getPid().equals("fedora-system:FedoraObject-3.0")) {
                contentModels.add(contentModel);
            }
        }
        Map<ContentModelObject, Integer> extendsCount = new HashMap<ContentModelObject, Integer>();

        for (ContentModelObject contentModel : contentModels) {
            extendsCount.put(contentModel, 0);
        }
        for (ContentModelObject contentModel : contentModels) {
            Set<ContentModelObject> childModels = contentModel.getDescendants();
            for (ContentModelObject childModel : childModels) {
                if (contentModels.contains(childModel)) {
                    Integer extendscounter = extendsCount.get(childModel);
                    extendsCount.put(contentModel, extendscounter + 1);
                }
            }
        }

        int bestCount = Integer.MAX_VALUE;
        ContentModelObject bestCM = null;
        for (ContentModelObject contentModelObject : extendsCount.keySet()) {
            Integer extendscounter = extendsCount.get(contentModelObject);
            if (extendscounter <= bestCount) {
                bestCount = extendscounter;
                bestCM = contentModelObject;
            }
        }

        contentModelTitle = bestCM.getTitle();
        return contentModelTitle;
    }


}
