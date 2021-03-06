package dk.statsbiblioteket.doms.guiclient;

import dk.statsbiblioteket.doms.central.User;
import dk.statsbiblioteket.doms.client.exceptions.ServerOperationFailed;
import dk.statsbiblioteket.doms.client.impl.AbstractDomsClient;
import dk.statsbiblioteket.doms.client.utils.Constants;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: abr
 * Date: 9/5/11
 * Time: 1:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class GuiClientImpl extends AbstractDomsClient implements GuiClient {


    public GuiClientImpl(URL url, String username, String password) {
        super(url, username, password);

    }


    public SearchResultList search(String query, int offset, int pageLength) throws ServerOperationFailed {
        try {
            dk.statsbiblioteket.doms.central.SearchResultList searchResultList = domsAPI.findObjects(
                    query, offset, pageLength);

            List<SearchResult> cresults = new ArrayList<SearchResult>();

            for (dk.statsbiblioteket.doms.central.SearchResult wresult : searchResultList.getSearchResult()) {
                String state = wresult.getState();
                Constants.FedoraState resultState;
                if (state != null && !state.isEmpty()) {
                    resultState = Constants.FedoraState.fromString(state);
                } else {
                    resultState = null;
                }
                SearchResult cresult = new SearchResult(wresult.getPid(),
                                                        wresult.getType(),
                                                        wresult.getSource(),
                                                        wresult.getTitle(),
                                                        wresult.getTime(),
                                                        wresult.getDescription(),
                                                        resultState,
                                                        new Date(wresult.getModifiedDate()),
                                                        new Date(wresult.getCreatedDate()),
                                                        getFactory());
                cresults.add(cresult);
            }
            return new SearchResultList(cresults, searchResultList.getHitCount());
            //        } catch (dk.statsbiblioteket.doms.central.InvalidCredentialsException invalidCredentials){
            //            throw new InvalidCredentialsException("Authorization Failed", invalidCredentials);
        } catch (Exception exception) {
            throw new ServerOperationFailed(
                    "Failed searching", exception);
        }
    }

    @Override
    public String getPasswordForUser(String username, List<String> roles) throws ServerOperationFailed {
        try {
            User user = domsAPI.createTempAdminUser(username, roles);
            return user.getPassword();
        } catch (Exception e) {
            throw new ServerOperationFailed(
                    "Failed searching", e);
        }
    }

}
