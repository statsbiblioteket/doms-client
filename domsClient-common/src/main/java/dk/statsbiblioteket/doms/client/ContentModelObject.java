package dk.statsbiblioteket.doms.client;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import dk.statsbiblioteket.doms.central.ObjectProfile;

/**
 * Created by IntelliJ IDEA.
 * User: eab
 * Date: 9/8/11
 * Time: 1:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class ContentModelObject extends AbstractDigitalObject {


    public ContentModelObject(ObjectProfile profile, CentralWebservice api, DigitalObjectFactory factory) {
        super(profile, api, factory);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
