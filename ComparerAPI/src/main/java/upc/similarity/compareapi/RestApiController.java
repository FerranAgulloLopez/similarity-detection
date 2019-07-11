package upc.similarity.compareapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import upc.similarity.compareapi.entity.Dependency;
import upc.similarity.compareapi.entity.Requirement;
import upc.similarity.compareapi.entity.input.Clusters;
import upc.similarity.compareapi.entity.input.ReqProject;
import upc.similarity.compareapi.exception.*;
import upc.similarity.compareapi.service.CompareService;

import java.util.List;

@RestController
@RequestMapping(value = "/upc/Compare")
public class RestApiController {

    @Autowired
    CompareService compareService;

    @PostMapping(value = "/BuildModel")
    public ResponseEntity buildModel(@RequestParam("organization") String organization,
                                     @RequestParam("compare") boolean compare,
                                     @RequestParam("threshold") double threshold,
                                     @RequestParam("filename") String responseId,
                                     @RequestBody List<Requirement> input) {
        try {
            compareService.buildModel(responseId,compare,threshold,organization,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/AddRequirements")
    public ResponseEntity addRequirements(@RequestParam("organization") String organization,
                                     @RequestParam("filename") String responseId,
                                     @RequestBody List<Requirement> input) {
        try {
            compareService.addRequirements(responseId,organization,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/DeleteRequirements")
    public ResponseEntity deleteRequirements(@RequestParam("organization") String organization,
                                     @RequestParam("filename") String responseId,
                                     @RequestBody List<Requirement> input) {
        try {
            compareService.deleteRequirements(responseId,organization,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/SimReqReq")
    public ResponseEntity simReqReq(@RequestParam("organization") String organization,
                                       @RequestParam("req1") String req1,
                                       @RequestParam("req2") String req2) {
        try {
            return new ResponseEntity<>(compareService.simReqReq(organization,req1,req2),HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/SimReqOrganization")
    public ResponseEntity simReqProject(@RequestParam("organization") String organization,
                                        @RequestParam("filename") String responseId,
                                        @RequestBody List<Requirement> requirements) {
        try {
            compareService.simReqOrganization(responseId,organization,requirements);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/SimReqProject")
    public ResponseEntity simReqProject(@RequestParam("organization") String organization,
                                           @RequestParam("filename") String responseId,
                                           @RequestBody ReqProject projectRequirements) {
        try {
            compareService.simReqProject(responseId,organization,projectRequirements);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/SimProject")
    public ResponseEntity simProject(@RequestParam("organization") String organization,
                                        @RequestParam("filename") String responseId,
                                        @RequestBody List<String> projectRequirements) {
        try {
            compareService.simProject(responseId,organization,projectRequirements);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/BuildModelAndCompute")
    public ResponseEntity buildModelAndCompute(@RequestParam("organization") String organization,
                                               @RequestParam("compare") boolean compare,
                                               @RequestParam("filename") String responseId,
                                               @RequestParam("threshold") double threshold,
                                               @RequestBody List<Requirement> input) {
        try {
            compareService.buildModelAndCompute(responseId,compare,organization,threshold,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/BuildClusters")
    public ResponseEntity buildClusters(@RequestParam("organization") String organization,
                                        @RequestParam("compare") boolean compare,
                                        @RequestParam("threshold") double threshold,
                                        @RequestParam("filename") String responseId,
                                        @RequestBody Clusters input) {
        try {
            compareService.buildClusters(responseId,compare,threshold,organization,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/BuildClustersAndCompute")
    public ResponseEntity buildClustersAndCompute(@RequestParam("organization") String organization,
                                                  @RequestParam("compare") boolean compare,
                                                  @RequestParam("filename") String responseId,
                                                  @RequestParam("threshold") double threshold,
                                                  @RequestBody Clusters input) {
        try {
            compareService.buildClustersAndCompute(responseId,compare,organization,threshold,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/SimReqClusters")
    public ResponseEntity simReqClusters(@RequestParam("organization") String organization,
                                         @RequestParam("maxValue") int maxValue,
                                         @RequestBody List<String> requirements) {
        try {
            return new ResponseEntity<>(compareService.simReqClusters(organization,requirements,maxValue),HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/TreatAcceptedAndRejectedDependencies")
    public ResponseEntity treatAcceptedAndRejectedDependencies(@RequestParam("organization") String organization,
                                         @RequestBody List<Dependency> dependencies) {
        try {
            compareService.treatAcceptedAndRejectedDependencies(organization, dependencies);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @GetMapping(value = "/GetResponsePage")
    public ResponseEntity getResponsePage(@RequestParam("organization") String organization,
                                        @RequestParam("responseId") String responseId) {
        try {
            return new ResponseEntity<>(compareService.getResponsePage(organization,responseId),HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @DeleteMapping(value = "/ClearOrganizationResponses")
    public ResponseEntity clearOrganizationResponses(@RequestParam("organization") String organization) {
        try {
            compareService.clearOrganizationResponses(organization);
            return new ResponseEntity<>(null, HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @DeleteMapping(value = "/ClearOrganization")
    public ResponseEntity clearOrganization(@RequestParam("organization") String organization) {
        try {
            compareService.clearOrganization(organization);
            return new ResponseEntity<>(null, HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @DeleteMapping(value = "/ClearDatabase")
    public ResponseEntity clearDatabase() {
        try {
            compareService.clearDatabase();
            return new ResponseEntity<>(null, HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    @PostMapping(value = "/CronMethod")
    public ResponseEntity cronMethod(@RequestParam("organization") String organization,
                                        @RequestParam("filename") String responseId,
                                        @RequestBody Clusters input) {
        try {
            compareService.cronMethod(responseId,organization,input);
            return new ResponseEntity<>(null,HttpStatus.OK);
        } catch (ComponentException e) {
            return new ResponseEntity<>(e,HttpStatus.valueOf(e.getStatus()));
        }
    }

    /*
    Auxiliary operations
     */

    @PostMapping(value = "/TestAccuracy")
    public ResponseEntity TestAccuracy(@RequestParam("compare") boolean compare,
                                     @RequestBody Clusters input) {
        return new ResponseEntity<>(compareService.TestAccuracy(compare,input),HttpStatus.OK);
    }

    @PostMapping(value = "/ExtractModel")
    public ResponseEntity extractModel(@RequestParam("organization") String organization,
                                     @RequestParam("compare") boolean compare,
                                     @RequestBody Clusters input) {
        return new ResponseEntity<>(compareService.extractModel(compare,organization,input),HttpStatus.OK);
    }

}