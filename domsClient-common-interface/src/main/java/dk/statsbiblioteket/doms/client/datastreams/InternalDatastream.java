package dk.statsbiblioteket.doms.client.datastreams;


/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 9/15/11
 * Time: 3:27 PM
 * To change this template use File | Settings | File Templates.
 */
public interface InternalDatastream extends Datastream {

    public void setContents(String contents);

    /**
        Use setContents instead
        @see #setContents(String)
        @deprecated
     */
    @Deprecated()
    public void replace(String contents);
}
