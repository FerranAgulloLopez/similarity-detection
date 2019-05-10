package upc.similarity.semilarapi.dao;

import upc.similarity.semilarapi.entity.Model;
import upc.similarity.semilarapi.exception.NotFinishedException;
import upc.similarity.semilarapi.exception.NotFoundException;

import java.sql.SQLException;

public interface modelDAO {

    public void saveModel(String organization, Model model) throws SQLException;

    public Model getModel(String organization) throws SQLException, NotFoundException;

    public void saveResponse(String organizationId, String responseId) throws SQLException;

    public void saveResponsePage(String organizationId, String responseId, int page, String jsonResponse) throws SQLException;

    public void finishComputation(String organizationId, String responseId) throws SQLException;

    public String getResponsePage(String organizationId, String responseId) throws SQLException, NotFoundException, NotFinishedException;

    public void clearOrganizationResponses(String organization) throws SQLException, NotFoundException;

    public void createDatabase() throws SQLException;


}