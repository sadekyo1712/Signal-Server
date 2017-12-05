package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AvatarDescriptor {

    @JsonProperty
    private String name;

    @JsonProperty
    private String location;

    public AvatarDescriptor(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }
}
