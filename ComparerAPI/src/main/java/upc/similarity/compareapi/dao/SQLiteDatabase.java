package upc.similarity.compareapi.dao;

import org.json.JSONArray;
import org.json.JSONObject;
import upc.similarity.compareapi.config.Constants;
import upc.similarity.compareapi.config.Control;
import upc.similarity.compareapi.entity.Dependency;
import upc.similarity.compareapi.entity.Execution;
import upc.similarity.compareapi.entity.Model;
import upc.similarity.compareapi.entity.Organization;
import upc.similarity.compareapi.exception.InternalErrorException;
import upc.similarity.compareapi.exception.NotFinishedException;
import upc.similarity.compareapi.exception.NotFoundException;
import upc.similarity.compareapi.util.Time;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class SQLiteDatabase implements DatabaseModel {

    private Lock mainDbLock = new ReentrantLock(true);
    private static String dbMainName = "main";
    private static String dbPath = "data/";
    private static String driversName = "jdbc:sqlite:";

    //is public to be accessible by tests
    public void getAccessToMainDb() throws InternalErrorException {
        int sleepTime = Constants.getInstance().getSleepTime();
        try {
            if (!mainDbLock.tryLock(sleepTime, TimeUnit.SECONDS)) throw new InternalErrorException("The main database is lock, another thread is using it");
        } catch (InterruptedException e) {
            Control.getInstance().showErrorMessage(e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    //is public to be accessible by tests
    public void releaseAccessToMainDb() {
        mainDbLock.unlock();
    }

    public static void setDbPath(String dbPath) {
        SQLiteDatabase.dbPath = dbPath;
    }

    private String buildDbUrl(String organization) {
        return driversName + dbPath + buildFileName(organization);
    }

    private String buildFileName(String organization) {
        return organization + ".db";
    }

    private void configureOrganizationDatabase(String organization) throws SQLException {
        String sql = "PRAGMA journal_mode=WAL;";
        try (Connection conn = getConnection(organization);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean correct = true;
            if (rs.next()) {
                String response = rs.getString(1);
                if (!response.equals("wal")) correct = false;
            } else correct = false;
            if (!correct) throw  new SQLException("Error when setting wal-mode");
        }
    }

    private void deleteDataFiles(String text) throws IOException, InternalErrorException {
        Path dirPath = Paths.get(dbPath);
        class Control {
            private volatile boolean error = false;
        }
        final Control control = new Control();
        try (Stream<Path> walk = Files.walk(dirPath)) {
            walk.map(Path::toFile)
                    .forEach(file -> {
                                if (!file.isDirectory() && file.getName().contains(text)) {
                                    if(!file.delete()) control.error = true;
                                }
                            }
                    );
        }
        if (control.error) throw new InternalErrorException("Error while deleting a file");
    }

    private void createOrganizationFiles(String organization) throws IOException, InternalErrorException {
        File file = new File(dbPath + buildFileName(organization));
        if(!file.createNewFile()) throw new InternalErrorException("Error while creating a new file");
    }

    private void insertNewOrganization(String organization) throws SQLException, IOException, InternalErrorException {

        if (!existsOrganization(organization)) {
            createOrganizationFiles(organization);
            configureOrganizationDatabase(organization);
            try (Connection conn = getConnection(organization)) {
                createOrganizationTables(conn);
            }
            insertOrganization(organization);
        }
    }

    private Connection getConnection(String organization) throws SQLException {
        return DriverManager.getConnection(buildDbUrl(organization));
    }

    public SQLiteDatabase() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Override
    public boolean existsOrganization(String organizationId) throws SQLException {

        boolean result = true;
        try (Connection conn = getConnection(dbMainName);
             PreparedStatement ps = conn.prepareStatement("SELECT* FROM organizations WHERE id = ?")) {
            ps.setString(1, organizationId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) result = false;
            }
        }

        return result;
    }

    @Override
    public void clearOrganization(String organizationId) throws NotFoundException, SQLException, InternalErrorException, IOException {
        if (!existsOrganization(organizationId)) throw new NotFoundException("The organization " + organizationId + " does not exist");
        deleteOrganization(organizationId);
        deleteDataFiles(buildFileName(organizationId));
    }

    @Override
    public void clearDatabase() throws IOException, InternalErrorException, SQLException {
        resetMainDatabase();
    }

    private void resetMainDatabase() throws IOException, InternalErrorException, SQLException {

        getAccessToMainDb();

        try {
            deleteDataFiles(".db");
            createOrganizationFiles(dbMainName);

            String sql1 = "CREATE TABLE organizations (\n"
                    + "	id varchar PRIMARY KEY\n"
                    + ");";

            String sql2 = "CREATE TABLE responses (\n"
                    + "	organizationId varchar, \n"
                    + " responseId varchar, \n"
                    + " actualPage integer, \n"
                    + " maxPages integer, \n"
                    + " finished integer, \n"
                    + " startTime long, \n"
                    + " finalTIme long, \n"
                    + " methodName varchar, \n"
                    + " PRIMARY KEY(organizationId, responseId)"
                    + ");";

            String sql3 = "CREATE TABLE responsePages (\n"
                    + "	organizationId varchar, \n"
                    + " responseId varchar, \n"
                    + " page integer, \n"
                    + " jsonResponse text, \n"
                    + " FOREIGN KEY(organizationId, responseId) REFERENCES responses(organizationId, responseId), \n"
                    + " PRIMARY KEY(organizationId, responseId, page)"
                    + ");";

            try (Connection conn = getConnection(dbMainName);
                 Statement stmt = conn.createStatement()) {
                conn.setAutoCommit(false);
                stmt.execute(sql1);
                stmt.execute(sql2);
                stmt.execute(sql3);
                conn.commit();
            }
            configureOrganizationDatabase(dbMainName);
        } finally {
            releaseAccessToMainDb();
        }
    }

    @Override
    public void saveModel(String organization, Model model, List<Dependency> dependencies) throws IOException, InternalErrorException, SQLException {

        insertNewOrganization(organization);

        try (Connection conn = getConnection(organization)) {
            conn.setAutoCommit(false);
            clearOrganizationTables(conn);
            clearClusterTables(conn);
            saveOrganizationInfo(organization, model.getThreshold(), model.isCompare(), model.hasClusters(), model.getLastClusterId(), conn);
            saveDocs(model.getDocs(), conn);
            saveCorpusFrequency(model.getCorpusFrequency(), conn);
            if (model.hasClusters()) {
                saveClusters(model.getClusters(), conn);
                saveDependencies(dependencies, conn, false);
            }
            conn.commit();
        }
    }

    @Override
    public void updateClustersAndDependencies(String organization, Model model, List<Dependency> dependencies, boolean useDepsAuxiliaryTable) throws SQLException {

        try (Connection conn = getConnection(organization)) {
            conn.setAutoCommit(false);
            clearClusterTables(conn);
            if (model.hasClusters()) {
                updateOrganizationClustersInfo(organization,model.getLastClusterId(),conn);
                saveClusters(model.getClusters(), conn);
                if (!useDepsAuxiliaryTable) saveDependencies(dependencies, conn, false);
                else insertAuxiliaryDepsTable(conn);
            }
            conn.commit();
        }
    }

    @Override
    public Organization getOrganizationInfo(String organizationId) throws NotFoundException, SQLException {

        if (!existsOrganization(organizationId)) throw new NotFoundException("The organization with id " + organizationId + " does not exist");
        List<Execution> currentExecutions = new ArrayList<>();
        List<Execution> pendingResponses = new ArrayList<>();
        OrganizationInfo organizationInfo = null;
        try(Connection conn = getConnection(organizationId)) {
            organizationInfo = getOrganizationInfo(organizationId, conn);
        }
        try(Connection conn = getConnection(dbMainName)) {
            String sql = "SELECT responseId, maxPages, finished, startTime, finalTime, methodName FROM responses WHERE organizationId = ?";
            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, organizationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String responseId = rs.getString("responseId");
                        boolean finished = rs.getBoolean("finished");
                        long startTime = rs.getLong("startTime");
                        long finalTime = rs.getLong("finalTime");
                        String methodName = rs.getString("methodName");
                        Integer maxPages = null;
                        Execution execution = null;
                        if (finished) {
                            maxPages = rs.getInt("maxPages");
                            execution = new Execution(responseId, methodName, maxPages, startTime, finalTime);
                        } else {
                            execution = new Execution(responseId, methodName, maxPages, startTime, null);
                        }
                        if (finished) pendingResponses.add(execution);
                        else currentExecutions.add(execution);
                    }
                }
            }
        }
        return new Organization(organizationId, organizationInfo.threshold, organizationInfo.compare, organizationInfo.hasClusters, currentExecutions, pendingResponses);
    }

    @Override
    public Model getModel(String organization, boolean withFrequency) throws NotFoundException, SQLException {

        if (!existsOrganization(organization)) throw new NotFoundException("The organization with id " + organization + " does not exist");

        Map<String, Map<String, Double>> docs = null;
        Map<String, Integer> corpusFrequency = null;
        Map<Integer, List<String>> clusters = null;

        double threshold;
        boolean compare;
        boolean hasClusters;
        int lastClusterId;

        try (Connection conn = getConnection(organization)) {
            conn.setAutoCommit(false);
            OrganizationInfo aux = getOrganizationInfo(organization,conn);
            threshold = aux.threshold;
            compare = aux.compare;
            hasClusters = aux.hasClusters;
            lastClusterId = aux.lastClusterId;
            docs = loadDocs(conn);
            if (withFrequency) corpusFrequency = loadCorpusFrequency(conn);
            if (hasClusters) {
                clusters = loadClusters(conn);
            }
            conn.commit();
        }

        return (hasClusters) ? new Model(docs, corpusFrequency, threshold, compare, lastClusterId, clusters) : new Model(docs, corpusFrequency, threshold, compare);
    }

    @Override
    public void saveResponse(String organizationId, String responseId, String methodName) throws SQLException, InternalErrorException {
        String sql = "INSERT INTO responses(organizationId, responseId, actualPage, maxPages, finished, startTime, finalTime, methodName) VALUES (?,?,?,?,?,?,?,?)";

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,organizationId);
            ps.setString(2,responseId);
            ps.setInt(3,0);
            ps.setInt(4,0);
            ps.setInt(5,0);
            ps.setLong(6, getCurrentTime());
            ps.setLong(7, getCurrentTime());
            ps.setString(8, methodName);
            ps.execute();
        } finally {
            releaseAccessToMainDb();
        }
    }

    @Override
    public void saveResponsePage(String organizationId, String responseId, String jsonResponse) throws SQLException, NotFoundException, InternalErrorException {
        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName)) {
            conn.setAutoCommit(false);
            int page = getTotalPages(organizationId, responseId, conn);
            insertResponsePage(organizationId,responseId,page,jsonResponse,conn);
            conn.commit();
        } finally {
            releaseAccessToMainDb();
        }
    }

    @Override
    public void saveExceptionAndFinishComputation(String organizationId, String responseId, String jsonResponse) throws SQLException, InternalErrorException {

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName)) {
            conn.setAutoCommit(false);
            deleteAllResponsePages(organizationId, responseId, conn);
            insertResponsePage(organizationId, responseId, 0, jsonResponse, conn);
            String sql = "UPDATE responses SET finished = ?, finalTime = ? WHERE organizationId = ? AND responseId = ?";
            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1,1);
                ps.setLong(2, getCurrentTime());
                ps.setString(3,organizationId);
                ps.setString(4,responseId);
                ps.executeUpdate();
            }
            conn.commit();
        } finally {
            releaseAccessToMainDb();
        }
    }

    @Override
    public String getResponsePage(String organizationId, String responseId) throws SQLException, NotFoundException, NotFinishedException {
        String sql = "SELECT actualPage, maxPages, finished FROM responses WHERE organizationId = ? AND responseId = ?";

        String result = null;

        try (Connection conn = getConnection(dbMainName)) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, organizationId);
                ps.setString(2, responseId);

                int actualPage;
                int maxPages;
                int finished;
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        actualPage = rs.getInt("actualPage");
                        maxPages = rs.getInt("maxPages");
                        finished = rs.getInt("finished");
                    } else throw new NotFoundException("The organization " + organizationId + " has not a response with id " + responseId);
                }

                if (finished == 0) throw new NotFinishedException("The computation is not finished yet");
                else {
                    if (actualPage == maxPages) {
                        deleteResponse(organizationId, responseId, conn);
                        result = "{}";
                    } else {
                        result = getResponsePage(organizationId, responseId, actualPage, conn);
                    }
                }
            }
            conn.commit();
        }
        return result;
    }

    @Override
    public void createDepsAuxiliaryTable(String organizationId) throws SQLException {

        String sql1 = "DROP TABLE IF EXISTS aux_dependencies;";

        String sql2 = "CREATE TABLE aux_dependencies (\n"
                + "	fromid varchar, \n"
                + " toid varchar, \n"
                + " status varchar, \n"
                + " score double, \n"
                + " clusterId integer, \n"
                + " PRIMARY KEY(fromid, toid)"
                + ");";

        String sql3 = "INSERT INTO aux_dependencies SELECT * FROM dependencies;";

        try (Connection conn = getConnection(organizationId);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.execute(sql1);
            stmt.execute(sql2);
            stmt.execute(sql3);
            conn.commit();
        }

    }

    @Override
    public void saveDependencyOrReplace(String organizationId, Dependency dependency, boolean useAuxiliaryTable) throws SQLException {
        String sql = "INSERT OR REPLACE INTO dependencies(fromid, toid, status, score, clusterId) VALUES (?,?,?,?,?)";
        if (useAuxiliaryTable) sql = "INSERT OR REPLACE INTO aux_dependencies(fromid, toid, status, score, clusterId) VALUES (?,?,?,?,?)";
        try (Connection conn = getConnection(organizationId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dependency.getFromid());
            ps.setString(2, dependency.getToid());
            ps.setString(3, dependency.getStatus());
            ps.setDouble(4, dependency.getDependencyScore());
            ps.setInt(5, dependency.getClusterId());
            ps.execute();
        }
    }

    @Override
    public void saveDependencies(String organizationId, List<Dependency> dependencies, boolean useAuxiliaryTable) throws SQLException {
        try (Connection conn = getConnection(organizationId)) {
            conn.setAutoCommit(false);
            saveDependencies(dependencies, conn, useAuxiliaryTable);
            conn.commit();
        }
    }

    @Override
    public Dependency getDependency(String fromid, String toid, String organizationId, boolean useAuxiliaryTables) throws SQLException, NotFoundException {

        Dependency result = null;

        try (Connection conn = getConnection(organizationId)) {

            String sql = "SELECT status, clusterId, score FROM dependencies WHERE (fromid = ? AND toid = ?) OR (fromid = ? AND toid = ?)";
            if (useAuxiliaryTables) sql = "SELECT status, clusterId, score FROM aux_dependencies WHERE (fromid = ? AND toid = ?) OR (fromid = ? AND toid = ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fromid);
                ps.setString(2, toid);
                ps.setString(3, toid);
                ps.setString(4, fromid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString(1);
                        int clusterId = rs.getInt(2);
                        double score = rs.getDouble(3);
                        result = new Dependency(fromid, toid, status, score, clusterId);
                    } else throw new NotFoundException("The dependency between " + fromid + " and " + toid + " does not exist ");
                }
            }
        }

        return result;
    }

    @Override
    public List<Dependency> getDependencies(String organizationId) throws SQLException {
        List<Dependency> result = new ArrayList<>();

        try (Connection conn = getConnection(organizationId)) {
            result = loadDependencies(conn);
        }
        return result;
    }

    @Override
    public List<Dependency> getClusterDependencies(String organizationId, int clusterId, boolean useAuxiliaryTable) throws SQLException {

        List<Dependency> result = new ArrayList<>();

        try (Connection conn = getConnection(organizationId)) {

            String sql = "SELECT fromid, toid FROM dependencies WHERE clusterId = ? AND status = ?";
            if (useAuxiliaryTable) sql = "SELECT fromid, toid FROM aux_dependencies WHERE clusterId = ? AND status = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, clusterId);
                ps.setString(2, "accepted");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fromid = rs.getString("fromid");
                        String toid = rs.getString("toid");
                        result.add(new Dependency(fromid,toid));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<Dependency> getNotInDependencies(String organizationId, Set<String> dependencies, boolean useAuxiliaryTable) throws SQLException {
        List<Dependency> result = new ArrayList<>();

        Set<String> repeated = new HashSet<>();

        try (Connection conn = getConnection(organizationId)) {

            String sql = "SELECT fromid, toid FROM dependencies WHERE status = ?";
            if (useAuxiliaryTable) sql = "SELECT fromid, toid FROM aux_dependencies WHERE status = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "accepted");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fromid = rs.getString("fromid");
                        String toid = rs.getString("toid");
                        if (!dependencies.contains(fromid+toid) && !repeated.contains(fromid+toid)) {
                            result.add(new Dependency(fromid, toid, "rejected", 0, -1));
                            repeated.add(fromid+toid);
                            repeated.add(toid+fromid);
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void deleteProposedClusterDependencies(String organizationId, int clusterId, boolean useAuxiliaryTable) throws SQLException {
        String sql = "DELETE FROM dependencies WHERE clusterId = ? AND status = ?";
        if (useAuxiliaryTable) sql = "DELETE FROM aux_dependencies WHERE clusterId = ? AND status = ?";
        try (Connection conn = getConnection(organizationId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clusterId);
            ps.setString(2, "proposed");
            ps.executeUpdate();
        }
    }

    @Override
    public List<Dependency> getRejectedDependencies(String organizationId, boolean useAuxiliaryTable) throws SQLException {
        List<Dependency> result = new ArrayList<>();

        try (Connection conn = getConnection(organizationId)) {

            String sql = "SELECT fromid, toid FROM dependencies WHERE status = ?";
            if (useAuxiliaryTable) sql = "SELECT fromid, toid FROM aux_dependencies WHERE status = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "rejected");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fromid = rs.getString("fromid");
                        String toid = rs.getString("toid");
                        result.add(new Dependency(fromid,toid));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<Dependency> getReqDependencies(String organizationId, String requirementId, String status, boolean useAuxiliaryTable) throws SQLException {
        List<Dependency> result = new ArrayList<>();

        try (Connection conn = getConnection(organizationId)) {

            String sql = "SELECT toid, score FROM dependencies WHERE fromid = ? AND status = ?";
            if (useAuxiliaryTable) sql = "SELECT toid, score FROM aux_dependencies WHERE fromid = ? AND status = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, requirementId);
                ps.setString(2, status);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String toid = rs.getString("toid");
                        double score = rs.getDouble("score");
                        result.add(new Dependency(requirementId,toid,status,score,-1));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void updateDependencyStatus(String organizationId, String fromid, String toid, String newStatus, int newClusterId, boolean useAuxiliaryTable) throws SQLException {
        String sql = "UPDATE dependencies SET status = ?, clusterId = ? WHERE ((fromid = ? AND toid = ?) OR (fromid = ? AND toid = ?))";
        if (useAuxiliaryTable) sql = "UPDATE aux_dependencies SET status = ?, clusterId = ? WHERE ((fromid = ? AND toid = ?) OR (fromid = ? AND toid = ?))";
        try (Connection conn = getConnection(organizationId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, newClusterId);
            ps.setString(3, fromid);
            ps.setString(4, toid);
            ps.setString(5, toid);
            ps.setString(6, fromid);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateClusterDependencies(String organizationId, int oldClusterId, int newClusterId, boolean useAuxiliaryTable) throws SQLException {
        String sql = "UPDATE dependencies SET clusterId = ? WHERE clusterId = ?";
        if (useAuxiliaryTable) sql = "UPDATE aux_dependencies SET clusterId = ? WHERE clusterId = ?";
        try (Connection conn = getConnection(organizationId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1,newClusterId);
            ps.setInt(2, oldClusterId);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateClusterDependencies(String organizationId, String requirementId, int newClusterId, boolean useAuxiliaryTable) throws SQLException {
        String sql = "UPDATE dependencies SET clusterId = ? WHERE status = ? AND (fromid = ? OR toid = ?)";
        if (useAuxiliaryTable) sql = "UPDATE aux_dependencies SET clusterId = ? WHERE status = ? AND (fromid = ? OR toid = ?)";
        try (Connection conn = getConnection(organizationId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1,newClusterId);
            ps.setString(2, "accepted");
            ps.setString(3, requirementId);
            ps.setString(4, requirementId);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteReqDependencies(String organizationId, String reqId, boolean useAuxiliaryTable) throws SQLException {
        String sql = "DELETE FROM dependencies WHERE (fromid = ? OR toid = ?)";
        if (useAuxiliaryTable) sql = "DELETE FROM aux_dependencies WHERE (fromid = ? OR toid = ?)";
        try (Connection conn = getConnection(organizationId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,reqId);
            ps.setString(2, reqId);
            ps.executeUpdate();
        }
    }

    @Override
    public void finishComputation(String organizationId, String responseId) throws SQLException, InternalErrorException {
        String sql = "UPDATE responses SET finished = ?, finalTime = ? WHERE organizationId = ? AND responseId = ?";

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1,1);
            ps.setLong(2, getCurrentTime());
            ps.setString(3,organizationId);
            ps.setString(4,responseId);
            ps.executeUpdate();
        } finally {
            releaseAccessToMainDb();
        }
    }

    @Override
    public void clearOrganizationResponses(String organizationId) throws SQLException, NotFoundException, InternalErrorException {
        String sql = "SELECT responseId FROM responses WHERE organizationId = ? AND finished = ?";

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName)) {
            conn.setAutoCommit(false);
            if (!existsOrganization(organizationId)) throw new NotFoundException("The organization " + organizationId + " does not exist");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, organizationId);
                ps.setInt(2, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String responseId = rs.getString("responseId");
                        deleteAllResponsePages(organizationId, responseId, conn);
                        deleteResponse(organizationId,responseId,conn);
                    }
                }
            }
            conn.commit();
        } finally {
            releaseAccessToMainDb();
        }
    }

    @Override
    public void clearOldResponses(long borderTime) throws InternalErrorException, SQLException {
        String sql = "SELECT organizationId, responseId FROM responses WHERE finalTime < ? AND finished = ?";

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, borderTime);
                ps.setInt(2, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String organizationId = rs.getString("organizationId");
                        String responseId = rs.getString("responseId");
                        deleteAllResponsePages(organizationId, responseId, conn);
                        deleteResponse(organizationId,responseId,conn);
                    }
                }
            }
            conn.commit();
        } finally {
            releaseAccessToMainDb();
        }
    }


    /*
    Auxiliary operations
     */

    private void deleteOrganization(String organizationId) throws SQLException, InternalErrorException {

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM organizations WHERE id = ?")) {
            ps.setString(1, organizationId);
            ps.executeUpdate();
        } finally {
            releaseAccessToMainDb();
        }
    }

    private int getTotalPages(String organizationId, String responseId, Connection conn) throws SQLException, NotFoundException {

        String sql = "SELECT maxPages FROM responses WHERE organizationId = ? AND responseId = ?";
        int totalPages;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, organizationId);
            ps.setString(2, responseId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalPages = rs.getInt(1);
                } else throw new NotFoundException("The organization " + organizationId + " has not a response with id " + responseId);
            }
        }

        return totalPages;
    }

    private void insertAuxiliaryDepsTable(Connection conn) throws SQLException {
        String sql1 = "INSERT INTO dependencies SELECT * FROM aux_dependencies;";
        String sql2 = "DROP TABLE aux_dependencies;";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql1);
            stmt.execute(sql2);
        }
    }

    private void saveDependencies(List<Dependency> dependencies, Connection conn, boolean useAuxiliaryTable) throws SQLException {
        String sqlProposed = "INSERT OR IGNORE INTO dependencies(fromid,toid,status,score,clusterId) VALUES (?,?,?,?,?)";
        String sqlAccepted = "INSERT OR REPLACE INTO dependencies(fromid,toid,status,score,clusterId) VALUES (?,?,?,?,?)";
        if (useAuxiliaryTable) {
            sqlProposed = "INSERT OR IGNORE INTO aux_dependencies(fromid,toid,status,score,clusterId) VALUES (?,?,?,?,?)";
            sqlAccepted = "INSERT OR REPLACE INTO aux_dependencies(fromid,toid,status,score,clusterId) VALUES (?,?,?,?,?)";
        }
        for (Dependency dependency : dependencies) {
            String sql = (dependency.getStatus().equals("proposed")) ? sqlProposed : sqlAccepted;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dependency.getFromid());
                ps.setString(2, dependency.getToid());
                ps.setString(3, dependency.getStatus());
                ps.setDouble(4, dependency.getDependencyScore());
                ps.setInt(5, dependency.getClusterId());
                ps.execute();
            }
        }
    }

    private void deleteAllResponsePages(String organizationId, String responseId, Connection conn) throws SQLException {
        String sql = "DELETE FROM responsePages WHERE organizationId = ? AND responseId = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,organizationId);
            ps.setString(2,responseId);
            ps.execute();
        }
    }

    private void deleteResponse(String organizationId, String responseId, Connection conn) throws SQLException {
        String sql = "DELETE FROM responses WHERE organizationId = ? AND responseId = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,organizationId);
            ps.setString(2,responseId);
            ps.executeUpdate();
        }
    }

    private class OrganizationInfo {
        double threshold;
        boolean compare;
        boolean hasClusters;
        int lastClusterId;
    }

    private OrganizationInfo getOrganizationInfo(String organizationId, Connection conn) throws SQLException {

        OrganizationInfo result = new OrganizationInfo();
        try (PreparedStatement ps = conn.prepareStatement("SELECT threshold, compare, hasClusters, lastClusterId FROM info WHERE id = ?")) {
                ps.setString(1, organizationId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.threshold = rs.getDouble(1);
                        result.compare = rs.getBoolean(2);
                        result.hasClusters = rs.getBoolean(3);
                        result.lastClusterId = rs.getInt(4);
                    }
                }
        }
        return result;
    }

    private void updateMaxPages(String organizationId, String responseId, int maxPages, Connection conn) throws SQLException {
        String sql1 = "UPDATE responses SET maxPages = ? WHERE organizationId = ? AND responseID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql1)) {
            ps.setInt(1,maxPages);
            ps.setString(2,organizationId);
            ps.setString(3,responseId);
            ps.executeUpdate();
        }
    }

    private void insertResponsePage(String organizationId, String responseId, int page, String jsonResponse, Connection conn) throws SQLException {
        String sql1 = "INSERT INTO responsePages(organizationId, responseId, page, jsonResponse) VALUES (?,?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(sql1)) {
            ps.setString(1, organizationId);
            ps.setString(2, responseId);
            ps.setInt(3, page);
            ps.setString(4, jsonResponse);
            ps.execute();
        }

        updateMaxPages(organizationId, responseId, page+1, conn);
    }

    private String getResponsePage(String organizationId, String responseId, int page, Connection conn) throws SQLException {
        String sql1 = "SELECT jsonResponse FROM responsePages WHERE organizationId = ? AND responseId = ? AND page = ?";
        String sql2 = "DELETE FROM responsePages WHERE organizationId = ? AND responseId = ? AND page = ?";
        String sql3 = "UPDATE responses SET actualPage = ? WHERE organizationId = ? AND responseId = ?";

        String result = null;

        try (PreparedStatement ps = conn.prepareStatement(sql1)){
            ps.setString(1,organizationId);
            ps.setString(2,responseId);
            ps.setInt(3,page);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result = rs.getString("jsonResponse");
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(sql2)) {
            ps.setString(1,organizationId);
            ps.setString(2, responseId);
            ps.setInt(3,page);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(sql3)) {
            ps.setInt(1,page+1);
            ps.setString(2,organizationId);
            ps.setString(3, responseId);
            ps.executeUpdate();
        }

        return result;
    }

    private void createOrganizationTables(Connection conn) throws SQLException {

        String sql1 = "CREATE TABLE info (\n"
                + " id varchar PRIMARY KEY, \n"
                + " threshold double, \n"
                + " compare integer, \n"
                + " hasClusters integer, \n"
                + " lastClusterId integer"
                + ");";

        String sql2 = "CREATE TABLE docs (\n"
                + " id varchar PRIMARY KEY, \n"
                + " definition text \n"
                + ");";

        String sql3 = "CREATE TABLE corpus (\n"
                + " definition text \n"
                + ");";

        String sql4 = "CREATE TABLE clusters (\n"
                + " id integer PRIMARY KEY, \n"
                + " definition text"
                + ");";

        String sql5 = "CREATE TABLE dependencies (\n"
                + "	fromid varchar, \n"
                + " toid varchar, \n"
                + " status varchar, \n"
                + " score double, \n"
                + " clusterId integer, \n"
                + " PRIMARY KEY(fromid, toid)"
                + ");";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql1);
            stmt.execute(sql2);
            stmt.execute(sql3);
            stmt.execute(sql4);
            stmt.execute(sql5);
        }
    }

    private void clearOrganizationTables(Connection conn) throws SQLException {

        String sql1 = "DELETE FROM info";
        String sql2 = "DELETE FROM docs";
        String sql3 = "DELETE FROM corpus";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql1);
            stmt.execute(sql2);
            stmt.execute(sql3);
        }

    }

    private void clearClusterTables(Connection conn) throws SQLException {

        String sql1 = "DELETE FROM clusters";
        String sql2 = "DELETE FROM dependencies";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql1);
            stmt.execute(sql2);
        }

    }

    private void insertOrganization(String organization) throws SQLException, InternalErrorException {

        String sql = "INSERT INTO organizations(id) VALUES (?)";

        getAccessToMainDb();
        try (Connection conn = getConnection(dbMainName);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,organization);
            ps.execute();
        } finally {
            releaseAccessToMainDb();
        }

    }

    private void saveOrganizationInfo(String organization, double threshold, boolean compare, boolean hasClusters, int lastClusterId, Connection conn) throws SQLException {

        String sql = "INSERT INTO info(id, threshold, compare, hasClusters, lastClusterId) VALUES (?,?,?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,organization);
            ps.setDouble(2, threshold);
            int value = 0;
            if (compare) value = 1;
            ps.setInt(3, value);
            value = 0;
            if (hasClusters) value = 1;
            ps.setInt(4, value);
            ps.setInt(5, lastClusterId);
            ps.execute();
        }
    }

    private void updateOrganizationClustersInfo(String organizationId, int lastClusterId, Connection conn) throws SQLException {

        String sql = "UPDATE info SET lastClusterId = ? WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setInt(1, lastClusterId);
            ps.setString(2,organizationId);
            ps.executeUpdate();
        }
    }

    private void saveDocs(Map<String, Map<String, Double>> docs, Connection conn) throws SQLException {

        Iterator it = docs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            String key = (String) pair.getKey();
            Map<String, Double> words = (Map<String, Double>) pair.getValue();
            String sql = "INSERT INTO docs(id, definition) VALUES (?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1,key);
                ps.setString(2,wordsConversionToJson(words).toString());
                ps.execute();
            }
        }
    }

    private JSONArray wordsConversionToJson(Map<String, Double> map) {
        JSONArray result = new JSONArray();
        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            JSONObject aux = new JSONObject();
            aux.put("id",pair.getKey());
            aux.put("value",pair.getValue());
            result.put(aux);
        }
        return result;
    }

    private void saveCorpusFrequency(Map<String, Integer> corpusFrequency, Connection conn) throws SQLException {

        String sql = "INSERT INTO corpus(definition) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,corpusFrequencyToJson(corpusFrequency));
            ps.execute();
        }
    }

    private String corpusFrequencyToJson(Map<String, Integer> corpusFrequency) {
        JSONArray result = new JSONArray();
        Iterator it = corpusFrequency.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            JSONObject aux = new JSONObject();
            aux.put("id",pair.getKey());
            aux.put("value",pair.getValue());
            result.put(aux);
        }
        return result.toString();
    }

    private void saveClusters(Map<Integer, List<String>> clusters, Connection conn) throws SQLException {

        Iterator it = clusters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            int key = (int) pair.getKey();
            List<String> requirements = (List<String>) pair.getValue();
            String sql = "INSERT INTO clusters(id, definition) VALUES (?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1,key);
                ps.setString(2,requirementsArrayConversionToJson(requirements).toString());
                ps.execute();
            }
        }
    }

    private JSONArray requirementsArrayConversionToJson(List<String> requirements) {
        JSONArray jsonArray = new JSONArray();
        for (String requirement: requirements) {
            jsonArray.put(requirement);
        }
        return jsonArray;
    }

    private Map<String, Map<String, Double>> loadDocs(Connection conn) throws SQLException {

        Map<String, Map<String, Double>> result = new HashMap<>();

        String sql = "SELECT* FROM docs";

        try (Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            while (rs.next()) {
                String key = rs.getString("id");
                String definition = rs.getString("definition");
                result.put(key,docsConversionToMap(definition));
            }
        }

        return result;
    }

    private Map<String, Double> docsConversionToMap(String rawJson) {
        JSONArray json = new JSONArray(rawJson);
        Map<String, Double> result = new HashMap<>();
        for (int i = 0; i < json.length(); ++i) {
            JSONObject aux = json.getJSONObject(i);
            String id = aux.getString("id");
            Double value = aux.getDouble("value");
            result.put(id,value);
        }
        return result;
    }

    private Map<String, Integer> loadCorpusFrequency(Connection conn) throws SQLException {

        Map<String, Integer> result;

        String sql = "SELECT* FROM corpus";

        try (Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            if (rs.next()) {
                String corpus = rs.getString("definition");
                result = corpusToMap(corpus);
            } else throw new SQLException("Error loading corpus from the database");
        }

        return result;
    }

    private Map<String, Integer> corpusToMap(String corpus) {
        Map<String, Integer> result = new HashMap<>();
        JSONArray json = new JSONArray(corpus);
        for (int i = 0; i < json.length(); ++i) {
            JSONObject aux = json.getJSONObject(i);
            String id = aux.getString("id");
            Integer value = aux.getInt("value");
            result.put(id, value);
        }
        return result;
    }

    private Map<Integer, List<String>> loadClusters(Connection conn) throws SQLException {

        Map<Integer, List<String>> result = new HashMap<>();

        String sql = "SELECT* FROM clusters";

        try (Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            while (rs.next()) {
                int key = rs.getInt("id");
                String definition = rs.getString("definition");
                result.put(key,requirementsArrayConversionToMap(definition));
            }
        }

        return result;
    }

    private List<String> requirementsArrayConversionToMap(String text) {

        List<String> result = new ArrayList<>();
        JSONArray jsonArray = new JSONArray(text);
        for (int i = 0; i < jsonArray.length(); ++i) {
            result.add(jsonArray.getString(i));
        }
        return result;
    }

    private List<Dependency> loadDependencies(Connection conn) throws SQLException {

        List<Dependency> dependencies = new ArrayList<>();

        String sql = "SELECT* FROM dependencies";

        try (Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            while (rs.next()) {
                String fromid = rs.getString("fromid");
                String toid = rs.getString("toid");
                String status = rs.getString("status");
                double score = rs.getDouble("score");
                int clusterId = rs.getInt("clusterId");
                dependencies.add(new Dependency(fromid,toid,status,score,clusterId));
            }
        }

        return dependencies;
    }

    private long getCurrentTime() {
        return Time.getInstance().getCurrentMillis();
    }
}