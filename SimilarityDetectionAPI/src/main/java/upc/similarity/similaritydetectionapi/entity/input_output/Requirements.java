package upc.similarity.similaritydetectionapi.entity.input_output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import upc.similarity.similaritydetectionapi.entity.Requirement;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ApiModel(value = "Requirements", description = "OpenReqJson with requirements")
public class Requirements implements Serializable {

    @JsonProperty(value="requirements")
    private List<Requirement> requirementsArray;

    public Requirements() {
        requirementsArray = new ArrayList<>();
    }

    public List<Requirement> getRequirements() {
        return requirementsArray;
    }

    public boolean inputOk() {
        return !requirementsArray.isEmpty();
    }
}
