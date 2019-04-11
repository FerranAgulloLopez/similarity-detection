package upc.similarity.similaritydetectionapi.adapter;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import upc.similarity.similaritydetectionapi.entity.Dependency;
import upc.similarity.similaritydetectionapi.entity.Requirement;
import upc.similarity.similaritydetectionapi.entity.input_output.Requirements;
import upc.similarity.similaritydetectionapi.exception.*;

import java.io.IOException;
import java.util.List;

public class SemilarAdapter extends ComponentAdapter{

    private static final String URL = "http://localhost:9405/upc/Semilar/";

    @Override
    public List<Dependency> simReqReq(String organization, String req1, String req2) throws InternalErrorException, BadRequestException {

        String response = connection_component(URL + "SimReqReq?organization=" + organization + "&req1=" + req1 + "&req2=" + req2,null);
        JSONArray array = new JSONArray(response);
        return JSON_to_list_dependencies(array);
    }

    @Override
    public List<Dependency> simReqProject(String organization, String req, List<String> reqs) throws InternalErrorException, BadRequestException {

        JSONArray json_to_send = new JSONArray();
        for (String aux: reqs) json_to_send.put(aux);

        String response = connection_component(URL + "SimReqProject?organization=" + organization + "&req=" + req, json_to_send);
        JSONArray array = new JSONArray(response);
        return JSON_to_list_dependencies(array);
    }

    @Override
    public List<Dependency> simProject(String organization, List<String> reqs) throws InternalErrorException, BadRequestException {

        JSONArray json_to_send = new JSONArray();
        for (String aux: reqs) json_to_send.put(aux);

        String response = connection_component(URL + "SimProject?organization=" + organization, json_to_send);
        JSONArray array = new JSONArray(response);
        return JSON_to_list_dependencies(array);
    }

    public void buildModel(String organization, boolean compare, List<Requirement> requirements) throws InternalErrorException, BadRequestException {

        JSONArray reqs_json = list_requirements_to_JSON(requirements);

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(URL + "BuildModel?compare=" + compare + "&organization=" + organization);
        httppost.setEntity(new StringEntity(reqs_json.toString(), ContentType.APPLICATION_JSON));

        int httpStatus = 200;
        String json_response = "";

        //Execute and get the response.
        try {
            HttpResponse response = httpclient.execute(httppost);
            httpStatus = response.getStatusLine().getStatusCode();
            json_response = EntityUtils.toString(response.getEntity());

        } catch (IOException e) {
            throw_component_exception(e,"Error conecting with the component");
        }

        if (httpStatus != 200) check_excepcions(httpStatus,json_response);
    }

    public void clearDB() throws InternalErrorException, BadRequestException {
        //TODO move this part to abstract class

        HttpClient httpclient = HttpClients.createDefault();
        HttpDelete httpdelete = new HttpDelete(URL + "Clear");

        int httpStatus;
        String res;

        //Execute and get the response.
        try {
            HttpResponse response = httpclient.execute(httpdelete);
            res = EntityUtils.toString(response.getEntity());
            httpStatus = response.getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new InternalErrorException("Semilar Exception:" + e.getMessage());
        }
        check_excepcions(httpStatus,res);
    }

    @Override
    protected void throw_component_exception(Exception e, String message) throws InternalErrorException {
        throw new InternalErrorException("Semilar Exception:" + message + ". " + e.getMessage());
    }

    protected void check_excepcions(int status, String resJSON) throws InternalErrorException, BadRequestException {

        if (status != 200) {
            JSONObject result = new JSONObject(resJSON);
            String message = result.getString("message");
            switch (status) {
                case 412: throw new BadRequestException(message);
                case 411: throw new InternalErrorException("Database error: " + message);
                default: throw new InternalErrorException("Semilar component is not working");
            }
        }
    }
}