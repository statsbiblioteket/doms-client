package dk.statsbiblioteket.doms.client;

/**
 * Created by IntelliJ IDEA.
 * User: eab
 * Date: 9/8/11
 * Time: 11:06 AM
 * To change this template use File | Settings | File Templates.
 */
public interface Relation {
    DigitalObject getObject();

    String getPredicate();
}
