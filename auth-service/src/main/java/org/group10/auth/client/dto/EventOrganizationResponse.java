package org.group10.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventOrganizationResponse(Long organizationId) {
}
