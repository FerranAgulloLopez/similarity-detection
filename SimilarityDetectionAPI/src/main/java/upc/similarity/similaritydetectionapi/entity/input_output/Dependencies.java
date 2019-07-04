package upc.similarity.similaritydetectionapi.entity.input_output;

import com.fasterxml.jackson.annotation.JsonProperty;
import upc.similarity.similaritydetectionapi.entity.Dependency;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Dependencies implements Serializable {

    @JsonProperty(value="requirements")
    private List<Dependency> dependencies;

    public Dependencies() {
        this.dependencies = new ArrayList<>();
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }
}
