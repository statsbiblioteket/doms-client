package dk.statsbiblioteket.doms.client.relations;

import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.objects.DigitalObject;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 9/15/11
 * Time: 3:15 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ObjectRelation extends Relation {
    DigitalObject getObject() throws ServerOperationFailed;

    String getObjectPid();

}
