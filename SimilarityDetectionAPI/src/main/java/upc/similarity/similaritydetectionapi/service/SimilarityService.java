package upc.similarity.similaritydetectionapi.service;

import upc.similarity.similaritydetectionapi.entity.Dependency;
import upc.similarity.similaritydetectionapi.entity.input_output.JsonProject;
import upc.similarity.similaritydetectionapi.entity.input_output.Requirements;
import upc.similarity.similaritydetectionapi.entity.input_output.Result_id;
import upc.similarity.similaritydetectionapi.exception.*;

import java.util.List;

public interface SimilarityService {

    public Result_id buildModel(String url, String organization, boolean compare, Requirements input) throws InternalErrorException, BadRequestException, NotFoundException;

    public Result_id simReqReq(String url, String organization, String req1, String req2) throws BadRequestException, InternalErrorException, NotFoundException;

    public Result_id simReqProject(String url, String organization, double threshold, int max_number, String req, String project, JsonProject input) throws BadRequestException, InternalErrorException, NotFoundException;

    public Result_id simProject(String url, String organization, double threshold, int max_number, String project, JsonProject input) throws BadRequestException, InternalErrorException, NotFoundException;

    public void clearDB() throws InternalErrorException, BadRequestException, NotFoundException;
}