package upc.similarity.compareapi.util;

import org.json.JSONArray;
import upc.similarity.compareapi.config.Constants;
import upc.similarity.compareapi.entity.auxiliary.ClusterAndDeps;
import upc.similarity.compareapi.entity.Dependency;
import upc.similarity.compareapi.entity.Model;
import upc.similarity.compareapi.entity.Requirement;
import upc.similarity.compareapi.exception.InternalErrorException;
import upc.similarity.compareapi.exception.NotFoundException;

import java.util.*;

public class ClusterOperations {

    private static ClusterOperations instance = new ClusterOperations();

    private ClusterOperations(){}

    public static ClusterOperations getInstance() {
        return instance;
    }

    public ClusterAndDeps computeIniClusters(List<Dependency> dependencies, List<Requirement> requirements) {

        HashMap<Integer,List<String>> clusters = new HashMap<>();
        HashMap<String, Integer> reqCluster = new HashMap<>();
        int countIds = 0;

        for (Requirement requirement: requirements) {
            reqCluster.put(requirement.getId(),-1);
        }

        countIds = computeDependencies(dependencies, reqCluster, clusters, countIds);

        Iterator it = reqCluster.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            String requirementId = (String) pair.getKey();
            int clusterId = (int) pair.getValue();
            if (clusterId == -1) {
                List<String> aux = new ArrayList<>();
                aux.add(requirementId);
                clusters.put(countIds, aux);
                reqCluster.put(requirementId, countIds);
                ++countIds;
            }
        }

        List<Dependency> acceptedDependencies = new ArrayList<>();
        HashSet<String> notRepeated = new HashSet<>();

        for (Dependency dependency: dependencies) {
            if (validDependency(dependency)) {
                String fromid = dependency.getFromid();
                String toid = dependency.getToid();
                if (reqCluster.containsKey(fromid) && reqCluster.containsKey(toid)) {
                    if (!notRepeated.contains(fromid+toid) && !notRepeated.contains(toid+fromid)) {
                        notRepeated.add(fromid+toid);
                        dependency.setClusterId(reqCluster.get(fromid));
                        acceptedDependencies.add(dependency);
                    }
                }
            }
        }

        return new ClusterAndDeps(countIds-1, clusters, reqCluster, duplicateDependencies(acceptedDependencies));
    }

    //TODO maybe change this
    private List<Dependency> duplicateDependencies(List<Dependency> dependencies) {
        List<Dependency> result = new ArrayList<>();
        for (Dependency dependency: dependencies) {
            result.add(dependency);
            result.add(new Dependency(dependency.getToid(), dependency.getFromid(), dependency.getStatus(), dependency.getDependencyScore(), dependency.getClusterId()));
        }
        return result;
    }

    //se supone que todos los requisitos estan en el modelo y que los requisitos de entrada no estan en ningun cluster
    public void reqClusters(String organization, String responseId, List<String> requirements, Map<String, Map<String, Double>> docs, Map<Integer, List<String>> clusters, double threshold) throws InternalErrorException {
        CosineSimilarity cosineSimilarity = CosineSimilarity.getInstance();
        Constants constants = Constants.getInstance();
        DatabaseOperations databaseOperations = DatabaseOperations.getInstance();
        int cont = 0;
        List<String> requirementsToCompare = new ArrayList<>();
        JSONArray array = new JSONArray();

        for (String req1: requirements) {
            Iterator it = clusters.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                List<String> clusterRequirements = (List<String>) pair.getValue();
                double maxScore = -1;
                String maxReq = null;
                for (String req2: clusterRequirements) {
                    double score = cosineSimilarity.compute(docs, req1, req2);
                    if (score > maxScore) {
                        maxScore = score;
                        maxReq = req2;
                    }
                }
                if (maxReq != null && maxScore >= threshold) {
                    Dependency dependency = new Dependency(maxScore, req1, maxReq, constants.getStatus(), constants.getDependencyType(), constants.getComponent());
                    array.put(dependency.toJSON());
                    ++cont;
                    if (cont >= constants.getMaxDepsForPage()) {
                        databaseOperations.generateResponsePage(responseId, organization, array,constants.getDependenciesArrayName());
                        array = new JSONArray();
                        cont = 0;
                    }
                }
            }

            for (String req2: requirementsToCompare) {
                double score = cosineSimilarity.compute(docs, req1, req2);
                if (score >= threshold) {
                    Dependency dependency = new Dependency(score, req1, req2, constants.getStatus(), constants.getDependencyType(), constants.getComponent());
                    array.put(dependency.toJSON());
                    ++cont;
                    if (cont >= constants.getMaxDepsForPage()) {
                        databaseOperations.generateResponsePage(responseId, organization, array,constants.getDependenciesArrayName());
                        array = new JSONArray();
                        cont = 0;
                    }
                }
            }
            requirementsToCompare.add(req1);
        }

        if (array.length() > 0) {
            databaseOperations.generateResponsePage(responseId, organization, array, constants.getDependenciesArrayName());
        }
    }

    //se supone que todos los requisitos estan en el modelo y que los requisitos de entrada no estan en ningun cluster
    public JSONArray reqClustersNotDb(List<String> requirements, Map<String, Map<String, Double>> docs, Map<Integer, List<String>> clusters, double threshold) {
        CosineSimilarity cosineSimilarity = CosineSimilarity.getInstance();
        Constants constants = Constants.getInstance();
        List<String> requirementsToCompare = new ArrayList<>();
        JSONArray array = new JSONArray();

        for (String req1: requirements) {
            Iterator it = clusters.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                List<String> clusterRequirements = (List<String>) pair.getValue();
                double maxScore = -1;
                String maxReq = null;
                for (String req2: clusterRequirements) {
                    double score = cosineSimilarity.compute(docs, req1, req2);
                    if (score > maxScore) {
                        maxScore = score;
                        maxReq = req2;
                    }
                }
                if (maxReq != null && maxScore >= threshold) {
                    Dependency dependency = new Dependency(maxScore, req1, maxReq, constants.getStatus(), constants.getDependencyType(), constants.getComponent());
                    array.put(dependency.toJSON());
                }
            }

            for (String req2: requirementsToCompare) {
                double score = cosineSimilarity.compute(docs, req1, req2);
                if (score >= threshold) {
                    Dependency dependency = new Dependency(score, req1, req2, constants.getStatus(), constants.getDependencyType(), constants.getComponent());
                    array.put(dependency.toJSON());
                }
            }
            requirementsToCompare.add(req1);
        }
        return array;
    }

    public void deleteReqsFromClusters(String organization, String responseId, Set<String> deletedReqs, Model model, Set<Integer> clustersChanged, Map<String,Integer> reqCluster) throws InternalErrorException {
        HashMap<Integer,List<String>> clusterDeletedReqs = new HashMap<>();

        for (String requirement: deletedReqs) {
            int id = reqCluster.get(requirement);
            if (clusterDeletedReqs.containsKey(id)) {
                List<String> aux = clusterDeletedReqs.get(id);
                aux.add(requirement);
                clusterDeletedReqs.put(id, aux);
            } else {
                List<String> aux = new ArrayList<>();
                aux.add(requirement);
                clusterDeletedReqs.put(id, aux);
            }
        }

        Iterator it = clusterDeletedReqs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            int id = (int) pair.getKey();
            List<String> auxDeleted = (List<String>) pair.getValue();
            deleteReqsFromCluster(organization, responseId, id, auxDeleted, model, clustersChanged, reqCluster);
        }


    }

    public void addRequirementsToClusters(String organization, String resonseId, List<Requirement> addRequirements, Model model, Set<Integer> clustersChanged, Map<String,Integer> reqCluster) throws InternalErrorException {
        Set<String> requirementsToDelete = new HashSet<>();
        for (Requirement requirement: addRequirements) {
            if (model.getDocs().containsKey(requirement.getId())) {
                requirementsToDelete.add(requirement.getId());
            }
        }
        deleteReqsFromClusters(organization, resonseId, requirementsToDelete, model, clustersChanged, reqCluster);
        int lastClusterId = model.getLastClusterId();
        Map<Integer,List<String>> clusters = model.getClusters();
        for (Requirement requirement: addRequirements) {
            ++lastClusterId;
            List<String> aux = new ArrayList<>();
            aux.add(requirement.getId());
            clusters.put(lastClusterId,aux);
            reqCluster.put(requirement.getId(), lastClusterId);
            clustersChanged.add(lastClusterId);
        }
        model.setLastClusterId(lastClusterId);
    }

    public void updateProposedDependencies(String organization, String responseId, Model model, Set<Integer> clustersChanged, boolean useAuxiliaryTable) throws InternalErrorException {
        DatabaseOperations databaseOperations = DatabaseOperations.getInstance();
        Map<Integer,List<String>> clusters = model.getClusters();
        Set<Integer> clusterIds = new HashSet<>();
        for (int clusterId: clustersChanged) {
           if (clusters.containsKey(clusterId)) clusterIds.add(clusterId);
           databaseOperations.deleteProposedClusterDependencies(organization, responseId, clusterId, useAuxiliaryTable);
        }
        List<Dependency> dependencies = computeProposedDependencies(organization, responseId, model.getDocs().keySet(), clusterIds, model, useAuxiliaryTable);
        databaseOperations.saveDependencies(organization, responseId, dependencies, useAuxiliaryTable);
    }

    public List<Dependency> computeProposedDependencies(String organization, String responseId, Set<String> requirements, Set<Integer> clustersIds, Model model, boolean useAuxiliaryTable) throws InternalErrorException {
        CosineSimilarity cosineSimilarity = CosineSimilarity.getInstance();
        Map<Integer,List<String>> clusters = model.getClusters();
        Set<String> rejectedDependencies = loadRejectedDependencies(organization, responseId, useAuxiliaryTable);
        List<Dependency> proposedDependencies = new ArrayList<>();
        //TODO this is causing n*n efficiency, can be improved saving the result of the pairs and only compute half of the matrix (less memory efficiency)
        for (String req1: requirements) {
            for (int clusterId: clustersIds) {
                List<String> clusterRequirements = clusters.get(clusterId);
                double maxScore = model.getThreshold();
                String maxReq = null;
                for (String req2: clusterRequirements) {
                    if (!rejectedDependencies.contains(req1+req2) && !req1.equals(req2)) {
                        double score = cosineSimilarity.compute(model.getDocs(), req1, req2);
                        if (score >= maxScore) {
                            maxScore = score;
                            maxReq = req2;
                        }
                    }
                }
                if (maxReq != null) {
                    proposedDependencies.add(new Dependency(req1,maxReq,"proposed",maxScore,clusterId));
                }
            }
        }
        return proposedDependencies;
    }

    private void deleteReqsFromCluster(String organization, String responseId, int clusterId, List<String> requirementsId, Model model, Set<Integer> clustersChanged, Map<String,Integer> reqCluster) throws InternalErrorException {

        DatabaseOperations databaseOperations = DatabaseOperations.getInstance();
        Map<Integer,List<String>> clusters = model.getClusters();
        int lastClusterId = model.getLastClusterId();
        Tfidf tfidf = Tfidf.getInstance();

        List<String> clusterRequirements = clusters.get(clusterId);
        if (clusterRequirements.size() > 1) {
            List<Dependency> dependencies = databaseOperations.getClusterDependencies(organization, responseId, clusterId, true);
            HashMap<String, List<String>> reqDeps = createReqDeps(clusterRequirements, dependencies);
            List<String> candidateReqs = new ArrayList<>();
            for (String requirementId : requirementsId) candidateReqs.addAll(reqDeps.get(requirementId));
            HashSet<String> avoidReqs = new HashSet<>(requirementsId);
            Clusters aux = bfsClusters(reqDeps, clusterRequirements, candidateReqs, avoidReqs);
            clusterRequirements.removeAll(requirementsId);
            HashMap<Integer, List<String>> candidateClusters = aux.candidateClusters;
            //updating clusters
            if (candidateClusters.size() > 1) {
                boolean firstOne = true;
                Iterator it = candidateClusters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    List<String> auxClusterRequirements = (List<String>) pair.getValue();
                    if (firstOne) {
                        clusters.put(clusterId, auxClusterRequirements);
                        clustersChanged.add(clusterId);
                        firstOne = false;
                    } else {
                        ++lastClusterId;
                        clusters.put(lastClusterId, auxClusterRequirements);
                        clustersChanged.add(lastClusterId);
                        for (String req : auxClusterRequirements) {
                            reqCluster.put(req, lastClusterId);
                            databaseOperations.updateClusterDependencies(organization, responseId, req, lastClusterId, true);
                        }
                    }
                }
            }
        } else {
            for (String req: requirementsId) {
                model.getClusters().remove(reqCluster.get(req));
            }
        }
        for (String req: requirementsId) {
            reqCluster.remove(req);
            databaseOperations.deleteReqDependencies(organization, responseId, req, true);
        }
        tfidf.deleteReqsAndRecomputeModel(requirementsId,model);
        model.setLastClusterId(lastClusterId);
    }

    public void addDeletedDependencies(String organization, String responseId, List<Dependency> deletedDependencies, Model model, Set<Integer> clustersChanged, Map<String,Integer> reqCluster) throws InternalErrorException {
        //TODO do each cluster together?
        DatabaseOperations databaseOperations = DatabaseOperations.getInstance();
        Map<Integer,List<String>> clusters = model.getClusters();
        int lastClusterId = model.getLastClusterId();
        for (Dependency dependency: deletedDependencies) {
            String fromid = dependency.getFromid();
            String toid = dependency.getToid();
            double score = dependency.getDependencyScore();
            try {
                Dependency aux = databaseOperations.getDependency(organization, responseId, fromid, toid, true);
                String status = aux.getStatus();
                int clusterId = aux.getClusterId();
                if (status.equals("accepted")) {
                    List<String> clusterRequirements = clusters.get(clusterId);
                    databaseOperations.updateDependencyStatus(organization, responseId, fromid, toid, "rejected", -1,true);
                    List<Dependency> dependencies = databaseOperations.getClusterDependencies(organization, responseId, clusterId, true);
                    HashMap<String,List<String>> reqDeps = createReqDeps(clusterRequirements,dependencies);
                    List<String> requirementIds = new ArrayList<>();
                    requirementIds.add(fromid);
                    requirementIds.add(toid);
                    Clusters auxClusters = bfsClusters(reqDeps, clusterRequirements, requirementIds, new HashSet<>());
                    HashMap<Integer,List<String>> candidateClusters = auxClusters.candidateClusters;
                    if (candidateClusters.size() > 1) {
                        boolean firstOne = true;
                        Iterator it = candidateClusters.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry pair = (Map.Entry) it.next();
                            List<String> auxClusterRequirements = (List<String>) pair.getValue();
                            if (firstOne) {
                                clusters.put(clusterId, auxClusterRequirements);
                                clustersChanged.add(clusterId);
                                firstOne = false;
                            } else {
                                ++lastClusterId;
                                clusters.put(lastClusterId, auxClusterRequirements);
                                clustersChanged.add(lastClusterId);
                                for (String req: auxClusterRequirements) {
                                    reqCluster.put(req, lastClusterId);
                                    databaseOperations.updateClusterDependencies(organization, responseId, req, lastClusterId, true);
                                }
                            }
                        }
                    }
                }
                if (status.equals("accepted") || status.equals("proposed")) {
                    databaseOperations.saveDependencyOrReplace(organization, responseId, new Dependency(fromid, toid, "rejected", score, -1), true);
                    databaseOperations.saveDependencyOrReplace(organization, responseId, new Dependency(toid, fromid, "rejected", score, -1), true);
                }
            } catch (NotFoundException e) {
                databaseOperations.saveDependencyOrReplace(organization, responseId, new Dependency(fromid, toid, "rejected", score, -1), true);
                databaseOperations.saveDependencyOrReplace(organization, responseId, new Dependency(toid, fromid, "rejected", score, -1), true);
            }
        }
        model.setLastClusterId(lastClusterId);
    }

    public void addAcceptedDependencies(String organization, String responseId, List<Dependency> acceptedDependencies, Model model, HashSet<Integer> clustersChanged, Map<String,Integer> reqCluster) throws InternalErrorException {
        DatabaseOperations databaseOperations = DatabaseOperations.getInstance();

        Map<Integer,List<String>> clusters = model.getClusters();

        for (Dependency dependency: acceptedDependencies) {
            String fromid = dependency.getFromid();
            String toid = dependency.getToid();
            int oldId1 = reqCluster.get(fromid);
            int oldId2 = reqCluster.get(toid);
            boolean exists = false;
            String status = "accepted";
            try {
                Dependency aux = databaseOperations.getDependency(organization, responseId, fromid, toid, true);
                exists = true;
                status = aux.getStatus();
            } catch (NotFoundException e) {
                //empty
            }
            if (!exists || status.equals("proposed")) {
                mergeClusters(clusters, reqCluster, fromid, toid, model.getLastClusterId());
                int newId = reqCluster.get(fromid);
                clustersChanged.add(newId);
                if (oldId1 != -1) clustersChanged.add(oldId1);
                if (oldId2 != -1) clustersChanged.add(oldId2);
                databaseOperations.saveDependencyOrReplace(organization, responseId, new Dependency(fromid, toid, "accepted", dependency.getDependencyScore(), newId), true);
                databaseOperations.saveDependencyOrReplace(organization, responseId, new Dependency(toid, fromid, "accepted", dependency.getDependencyScore(), newId), true);
                if (oldId1 != newId && oldId1 != -1) databaseOperations.updateClusterDependencies(organization, responseId, oldId1, newId, true);
                if (oldId2 != newId && oldId2 != -1) databaseOperations.updateClusterDependencies(organization, responseId, oldId2, newId, true);
            }
        }
    }

    /*
    Auxiliary operations
     */

    private class Clusters {
        HashMap<Integer,List<String>> candidateClusters;
        HashMap<String,Integer> reqCluster; //TODO is truly necessary?
        Clusters(HashMap<Integer,List<String>> candidateClusters, HashMap<String,Integer> reqCluster) {
            this.candidateClusters = candidateClusters;
            this.reqCluster = reqCluster;
        }
    }

    private Set<String> loadRejectedDependencies(String organization, String responseId, boolean useAuxiliaryTable) throws InternalErrorException {
        Set<String> result = new HashSet<>();
        DatabaseOperations databaseOperations = DatabaseOperations.getInstance();
        if (databaseOperations.existsOrganization(responseId, organization)) {
            List<Dependency> dependencies = DatabaseOperations.getInstance().getRejectedDependencies(organization, responseId, useAuxiliaryTable);
            for (Dependency dependency : dependencies) {
                String fromId = dependency.getFromid();
                String toId = dependency.getToid();
                result.add(fromId + toId);
                result.add(toId + fromId);
            }
        }
        return result;
    }

    private Clusters bfsClusters(HashMap<String,List<String>> reqDeps, List<String> clusterRequirements, List<String> requirementIds, HashSet<String> avoidReqs) {
        HashMap<Integer,List<String>> candidateClusters = new HashMap<>();
        HashSet<String> processedReqs = new HashSet<>();
        HashMap<String,Integer> reqCluster = new HashMap<>();
        PriorityQueue<String> priorityQueue = new PriorityQueue<>();
        int countIds = 0;
        for (String requirement: clusterRequirements) {
            reqCluster.put(requirement, -1);
        }
        for (String requirement: requirementIds) {
            List<String> aux = new ArrayList<>();
            aux.add(requirement);
            candidateClusters.put(countIds, aux);
            reqCluster.put(requirement, countIds);
            priorityQueue.add(requirement);
            ++countIds;
        }
        while(priorityQueue.size() > 0 && candidateClusters.size() > 1) {
            String requirement = priorityQueue.poll();
            if (!processedReqs.contains(requirement)) {
                for (String req2 : reqDeps.get(requirement)) {
                    if (!processedReqs.contains(req2) && !avoidReqs.contains(req2)) {
                        countIds = mergeClusters(candidateClusters, reqCluster, requirement, req2, countIds);
                        priorityQueue.add(req2);
                    }
                }
                processedReqs.add(requirement);
            }
        }
        return new Clusters(candidateClusters, reqCluster);
    }

    private HashMap<String,List<String>> createReqDeps(List<String> clusterRequirements, List<Dependency> dependencies) {
        HashMap<String,List<String>> reqDeps = new HashMap<>();
        for (String requirement: clusterRequirements) {
            List<String> aux = new ArrayList<>();
            reqDeps.put(requirement, aux);
        }
        for (Dependency dependency: dependencies) {
            String fromid = dependency.getFromid();
            String toid = dependency.getToid();
            List<String> aux1 = reqDeps.get(fromid);
            List<String> aux2 = reqDeps.get(toid);
            if (!aux1.contains(toid)) {
                aux1.add(toid);
                aux2.add(fromid);
                reqDeps.put(fromid, aux1);
                reqDeps.put(toid, aux2);
            }
        }
        return reqDeps;
    }

    private int computeDependencies(List<Dependency> dependencies, Map<String,Integer> reqCluster, Map<Integer,List<String>> clusters, int countIds) {
        for (Dependency dependency: dependencies) {
            if (validDependency(dependency)) {
                String fromid = dependency.getFromid();
                String toid = dependency.getToid();
                if (reqCluster.containsKey(fromid) && reqCluster.containsKey(toid)) {
                     countIds = mergeClusters(clusters, reqCluster, fromid, toid, countIds);
                }
            }
        }
        return countIds;
    }

    private boolean validDependency(Dependency dependency) {
        String type = dependency.getDependencyType();
        return (type != null && (type.equals("similar") || type.equals("duplicates")) && dependency.getStatus().equals("accepted"));
    }

    private int mergeClusters(Map<Integer,List<String>> clusters, Map<String,Integer> reqCluster, String req1, String req2, int countIds) {
        int clusterReq1 = reqCluster.get(req1);
        int clusterReq2 = reqCluster.get(req2);
        if (clusterReq1 == -1 && clusterReq2 == -1) {
            List<String> aux = new ArrayList<>();
            aux.add(req1);
            aux.add(req2);
            clusters.put(countIds, aux);
            reqCluster.put(req1, countIds);
            reqCluster.put(req2, countIds);
            ++countIds;
        } else if (clusterReq1 == -1) {
            List<String> aux = clusters.get(clusterReq2);
            aux.add(req1);
            clusters.put(clusterReq2, aux);
            reqCluster.put(req1, clusterReq2);
        } else if (clusterReq2 == -1) {
            List<String> aux = clusters.get(clusterReq1);
            aux.add(req2);
            clusters.put(clusterReq1, aux);
            reqCluster.put(req2, clusterReq1);
        } else if (clusterReq1 != clusterReq2) {
            List<String> aux1 = clusters.get(clusterReq1);
            List<String> aux2 = clusters.get(clusterReq2);
            aux1.addAll(aux2);
            for (String req: aux2) {
                reqCluster.put(req,clusterReq1);
            }
            clusters.remove(clusterReq2);
        }
        return countIds;
    }
}
