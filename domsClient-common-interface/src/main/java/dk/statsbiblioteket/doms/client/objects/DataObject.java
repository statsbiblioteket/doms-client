package dk.statsbiblioteket.doms.client.objects;

import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 9/15/11
 * Time: 3:25 PM
 * To change this template use File | Settings | File Templates.
 */
public interface DataObject extends DigitalObject {

    public String getContentmodelTitle() throws ServerOperationFailed;


}
