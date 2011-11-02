package dk.statsbiblioteket.doms.client.impl.objects;

import dk.statsbiblioteket.doms.central.*;
import dk.statsbiblioteket.doms.client.datastreams.Datastream;
import dk.statsbiblioteket.doms.client.datastreams.ExternalDatastream;
import dk.statsbiblioteket.doms.client.exceptions.NotFoundException;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.impl.datastreams.ExternalDatastreamImpl;
import dk.statsbiblioteket.doms.client.objects.FileObject;

import java.lang.String;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 10/31/11
 * Time: 11:33 AM
 * To change this template use File | Settings | File Templates.
 */
public class FileObjectImpl extends DataObjectImpl implements FileObject{
    public FileObjectImpl(ObjectProfile profile, CentralWebservice api,
                          DigitalObjectFactoryImpl factory) throws ServerOperationFailed {
        super(profile,api,factory);
    }

    @Override
    public URL getFileUrl() throws ServerOperationFailed {
        try {
            Datastream contentsStream = getDatastream("CONTENTS");
            if (contentsStream instanceof ExternalDatastream) {
                ExternalDatastream stream = (ExternalDatastream) contentsStream;
                return new URL(stream.getUrl());
            } else {
                throw new ServerOperationFailed("CONTENTS stream exist, but is internal...");
            }
        } catch (NotFoundException e) {
            return null;
        } catch (MalformedURLException e) {
            throw new ServerOperationFailed("Failed to parse url as url",e);
        }
    }

    @Override
    public void setFileUrl(URL url) throws ServerOperationFailed {
       setFileUrl(url,"", "application/octet-stream");
    }

    @Override
    public void setFileUrl(URL url, String checksum, String formatURI) throws ServerOperationFailed {
        try {
            api.addFileFromPermanentURL(this.getPid(),url.getFile(),checksum,url.toString(),formatURI,"Uploaded file from client");
            if (getFileUrl() == null){
                profile = api.getObjectProfile(this.getPid());
                DatastreamProfile contentDSprofile = null;
                for (DatastreamProfile datastreamProfile : profile.getDatastreams()) {
                    if (datastreamProfile.getId().equals("CONTENT")){
                        contentDSprofile = datastreamProfile;
                        break;
                    }
                }
                datastreams.add(new ExternalDatastreamImpl(contentDSprofile,this,api));
            }
        } catch (InvalidCredentialsException e) {
            throw new ServerOperationFailed(e);
        } catch (InvalidResourceException e) {
            throw new ServerOperationFailed(e);
        } catch (MethodFailedException e) {
            throw new ServerOperationFailed(e);
        }
    }
}